package com.taportal.domain.engagement;

import com.taportal.api.EngagementDtos.Campaign;
import com.taportal.api.EngagementDtos.EventRow;
import com.taportal.api.EngagementDtos.Referral;
import com.taportal.api.EngagementDtos.SurveyRow;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Engagement read APIs: campaigns, referrals, events, surveys. */
@Service
@Transactional(readOnly = true)
public class EngagementService {

    private final NurtureCampaignRepository campaignRepository;
    private final ReferralRepository referralRepository;
    private final RecruitingEventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrations;
    private final com.taportal.domain.approval.ApprovalRequestService approvals;
    private final com.taportal.domain.approval.ApprovalRequestRepository approvalRequests;
    private final SurveyRepository surveyRepository;
    private final CampusService campus;
    private final EventTemplateService eventTemplates;

    public EngagementService(
            NurtureCampaignRepository campaignRepository,
            ReferralRepository referralRepository,
            RecruitingEventRepository eventRepository,
            SurveyRepository surveyRepository,
            EventRegistrationRepository eventRegistrations,
            com.taportal.domain.approval.ApprovalRequestService approvals,
            com.taportal.domain.approval.ApprovalRequestRepository approvalRequests,
            CampusService campus,
            EventTemplateService eventTemplates) {
        this.campus = campus;
        this.eventTemplates = eventTemplates;
        this.campaignRepository = campaignRepository;
        this.referralRepository = referralRepository;
        this.eventRepository = eventRepository;
        this.eventRegistrations = eventRegistrations;
        this.approvals = approvals;
        this.approvalRequests = approvalRequests;
        this.surveyRepository = surveyRepository;
    }

    public List<Campaign> campaigns() {
        return campaignRepository.findByOrderByCreatedAtDesc().stream()
                .map(
                        c ->
                                new Campaign(
                                        c.getId(),
                                        c.getName(),
                                        c.getAudience(),
                                        c.getChannel(),
                                        c.getStatus(),
                                        c.getSent(),
                                        c.getOpened(),
                                        c.getReplied(),
                                        rate(c.getOpened(), c.getSent()),
                                        rate(c.getReplied(), c.getSent())))
                .toList();
    }

    public List<Referral> referrals() {
        // Only candidate/job ids are known here; candidateName/jobTitle are returned null
        // (the frontend tolerates this per CONTRACT).
        return referralRepository.findByOrderByCreatedAtDesc().stream()
                .map(
                        r ->
                                new Referral(
                                        r.getId(),
                                        r.getReferrerName(),
                                        null,
                                        null,
                                        r.getStatus(),
                                        r.getBonusAmount(),
                                        r.getCreatedAt()))
                .toList();
    }

    public List<EventRow> events() {
        java.util.Map<String, String> approvalByEvent = latestEventApprovals();
        return eventRepository.findByOrderByStartsAtDesc().stream()
                .map((e) -> toEventRow(e, approvalByEvent.getOrDefault(
                        e.getId().toString(), e.isApprovalRequired() ? "NOT_SENT" : null)))
                .toList();
    }

    /** Latest approval status per event id (requests are newest-first). */
    private java.util.Map<String, String> latestEventApprovals() {
        java.util.Map<String, String> byEvent = new java.util.HashMap<>();
        for (var r : approvalRequests.findByItemTypeOrderByCreatedAtDesc("EVENT")) {
            byEvent.putIfAbsent(r.getItemRef(), r.getStatus().name());
        }
        return byEvent;
    }

    /**
     * Events with roster rows compute their funnel live; legacy rows keep their counters.
     * Registered counts everyone who signed up and did not cancel, waitlist included; hires are
     * the students whose application from the event reached HIRED.
     */
    private EventRow toEventRow(RecruitingEvent e, String approvalStatus) {
        long rosterSize = eventRegistrations.countByEventId(e.getId());
        long cancelled = eventRegistrations.countByEventIdAndStatus(e.getId(), "CANCELLED");
        int registered = rosterSize > 0 ? (int) (rosterSize - cancelled) : e.getRegistrations();
        int attended = rosterSize > 0
                ? (int) eventRegistrations.countByEventIdAndStatus(e.getId(), "CHECKED_IN")
                : e.getAttended();
        int hires = rosterSize > 0 ? Math.max(campus.hires(e.getId()), e.getHires()) : e.getHires();
        return new EventRow(
                e.getId(), e.getName(), e.getType(), e.getLocation(), e.getStartsAt(),
                e.getEndsAt(), e.getTimezone(), approvalStatus,
                registered, attended, hires,
                e.getCapacity(), e.getRegistrationClosesAt(), e.isWaitlistEnabled(),
                (int) eventRegistrations.countByEventIdAndStatus(e.getId(), "WAITLISTED"),
                (int) eventRegistrations.countByEventIdAndShortlistedTrue(e.getId()),
                e.getRegistrationFormId(), e.getTemplateId(), e.getCountry(), e.getFormat(),
                e.getPublishAt(), campus.isPublic(e, approvalStatus));
    }

    @Transactional
    public EventRow createEvent(com.taportal.api.EngagementDtos.EventCreate req) {
        // Business rule: a scheduled end must come after the start.
        if (req.endsAt() != null && req.startsAt() != null && !req.endsAt().isAfter(req.startsAt())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "Event end time must be after the start time");
        }
        RecruitingEvent e = new RecruitingEvent();
        e.setName(req.name());
        e.setType(req.type() != null ? req.type() : "HIRING_EVENT");
        e.setLocation(req.location());
        e.setStartsAt(req.startsAt());
        e.setEndsAt(req.endsAt());
        e.setTimezone(req.timezone() != null && !req.timezone().isBlank() ? req.timezone() : "America/New_York");
        e.setIntakeFormId(req.intakeFormId());
        e.setRegistrationFormId(req.registrationFormId());
        e.setFormat(req.format());
        if (req.publishAt() != null && req.startsAt() != null && !req.publishAt().isBefore(req.startsAt())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "The event must be published before it starts");
        }
        e.setPublishAt(req.publishAt());
        // Started from a template: the event takes the template's settings, and they are then its own.
        if (req.templateId() != null) {
            eventTemplates.applyTo(e, req.templateId());
            if (req.format() != null) {
                e.setFormat(req.format());
            }
        }
        e.setRegistrations(0);
        e.setAttended(0);
        e.setHires(0);
        // Every event created here must be approved before students can see it.
        e.setApprovalRequired(true);
        RecruitingEvent saved = eventRepository.save(e);
        return toEventRow(saved, sendForApproval(saved, req.flaggedCritical()));
    }

    /**
     * Routes an event through the Event approval workflow: it goes to the approvals queue, and
     * the request links back to the event by its id.
     *
     * @return the request's status, or NOT_SENT when no workflow is switched on for events, in
     *         which case the event stays off the careers site until one is and it is sent again
     */
    private String sendForApproval(RecruitingEvent saved, Boolean flaggedCritical) {
        // Real event context for the workflow's conditions: format, lead time, flag.
        boolean virtual = "VIRTUAL".equals(saved.getFormat()) || (saved.getLocation() != null
                && saved.getLocation().toLowerCase().matches(".*(virtual|online|remote).*"));
        Double daysNotice = saved.getStartsAt() == null ? null
                : (double) java.time.Duration.between(java.time.OffsetDateTime.now(), saved.getStartsAt()).toDays();
        var request = approvals.submit(new com.taportal.api.ApprovalDtos.SubmitApprovalRequest(
                "Event", "EVENT", saved.getId().toString(),
                saved.getName(),
                saved.getLocation() + " · " + (saved.getStartsAt() == null ? "" : saved.getStartsAt().toLocalDate()),
                virtual ? "VIRTUAL" : "IN_PERSON", daysNotice, flaggedCritical));
        return request == null ? "NOT_SENT" : String.valueOf(request.status());
    }

    /**
     * Sends an event for approval again: after it was rejected and changed, or when no workflow
     * was switched on at the time it was created. Refused while a request is waiting or once it
     * is approved.
     */
    @Transactional
    public EventRow sendForApproval(java.util.UUID eventId) {
        RecruitingEvent e = eventRepository.findById(eventId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Event not found: " + eventId));
        String latest = latestEventApprovals().get(eventId.toString());
        if ("PENDING".equals(latest)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "This event is already waiting for approval");
        }
        if ("APPROVED".equals(latest) || "AUTO_APPROVED".equals(latest)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "This event is already approved");
        }
        e.setApprovalRequired(true);
        eventRepository.save(e);
        String status = sendForApproval(e, null);
        if ("NOT_SENT".equals(status)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "No approval workflow is switched on for events. Switch one on under Settings, Workflow, then send this again.");
        }
        return toEventRow(e, status);
    }

    public List<SurveyRow> surveys() {
        return surveyRepository.findByOrderByNameAsc().stream()
                .map(
                        s ->
                                new SurveyRow(
                                        s.getId(),
                                        s.getName(),
                                        s.getStage(),
                                        s.getSent(),
                                        s.getResponses(),
                                        s.getAvgSentiment(),
                                        s.getNps(),
                                        rate(s.getResponses(), s.getSent())))
                .toList();
    }

    /** Percentage 0..100 rounded to one decimal; 0 when denominator is 0. */
    private static double rate(int numerator, int denominator) {
        if (denominator <= 0) return 0.0;
        return Math.round((numerator * 1000.0) / denominator) / 10.0;
    }
}
