package com.taportal.domain.interview;

import com.taportal.api.InterviewDtos.CreateInterviewRequest;
import com.taportal.api.InterviewDtos.InterviewResponse;
import com.taportal.api.InterviewDtos.ScheduleInterviewRequest;
import com.taportal.api.InterviewDtos.SlotResponse;
import com.taportal.domain.application.Application;
import com.taportal.domain.application.ApplicationRepository;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.events.BookingOrigin;
import com.taportal.domain.events.InterviewBooked;
import com.taportal.domain.events.InterviewCanceled;
import com.taportal.domain.events.InterviewTimesOffered;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.JobRepository;
import com.taportal.domain.recruiter.RecruiterUser;
import com.taportal.domain.recruiter.RecruiterUserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewSlotRepository slotRepository;
    private final CalendarEventRepository calendarEvents;
    private final AvailabilityService availability;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final CandidateRepository candidates;
    private final RecruiterUserRepository recruiterUsers;
    private final InterviewPanelistRepository panelists;
    private final InterviewRoundRepository rounds;
    private final InterviewRoundMemberRepository roundMembers;
    private final SchedulingPolicyRepository policies;
    private final com.taportal.domain.notification.NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final jakarta.persistence.EntityManager entityManager;

    public InterviewService(
            InterviewRepository interviewRepository,
            InterviewSlotRepository slotRepository,
            CalendarEventRepository calendarEvents,
            AvailabilityService availability,
            ApplicationRepository applications,
            JobRepository jobs,
            CandidateRepository candidates,
            RecruiterUserRepository recruiterUsers,
            InterviewPanelistRepository panelists,
            InterviewRoundRepository rounds,
            InterviewRoundMemberRepository roundMembers,
            SchedulingPolicyRepository policies,
            com.taportal.domain.notification.NotificationService notifications,
            ApplicationEventPublisher events,
            jakarta.persistence.EntityManager entityManager) {
        this.interviewRepository = interviewRepository;
        this.slotRepository = slotRepository;
        this.calendarEvents = calendarEvents;
        this.availability = availability;
        this.applications = applications;
        this.jobs = jobs;
        this.candidates = candidates;
        this.recruiterUsers = recruiterUsers;
        this.panelists = panelists;
        this.rounds = rounds;
        this.roundMembers = roundMembers;
        this.policies = policies;
        this.notifications = notifications;
        this.events = events;
        this.entityManager = entityManager;
    }

    public List<InterviewResponse> listByApplication(UUID applicationId) {
        return interviewRepository.findByApplicationId(applicationId).stream().map(InterviewService::toResponse).toList();
    }

    /** A slot in one of these states is still on offer: the legacy per-job pool, or proposed for an interview. */
    private static final Set<String> ON_OFFER = Set.of("OPEN", "PROPOSED");

    /** The job's times a recruiter can still book (see {@link #bookableSlots}). */
    public List<SlotResponse> openSlots(UUID jobId) {
        return bookableSlots(jobId).stream().map(InterviewService::toSlotResponse).toList();
    }

    /**
     * The job's slots that can still be booked: on offer (neither selected nor
     * withdrawn), not taken, and starting in the future — earliest first. Two
     * rows for the same time are one choice, so only the first is kept.
     */
    public List<InterviewSlot> bookableSlots(UUID jobId) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<Instant, InterviewSlot> oneEach = new LinkedHashMap<>();
        slotRepository.findByJobIdAndBookedFalse(jobId).stream()
                .filter(s -> ON_OFFER.contains(s.getStatus()) && s.getStartsAt().isAfter(now))
                .sorted(Comparator.comparing(InterviewSlot::getStartsAt))
                .forEach(s -> oneEach.putIfAbsent(s.getStartsAt().toInstant(), s));
        return List.copyOf(oneEach.values());
    }

    /** Proposed slots, generating a fresh set when none exist and the interview is unbooked. */
    @Transactional
    public List<SlotResponse> proposedSlotsOrPropose(UUID interviewId) {
        List<SlotResponse> current = proposedSlots(interviewId);
        if (!current.isEmpty()) {
            return current;
        }
        Interview interview = load(interviewId);
        if (interview.getScheduledAt() != null || "CANCELED".equals(interview.getStatus())) {
            return current;
        }
        return autoPropose(interviewId);
    }

    /** The times on offer that are still to come. */
    public List<SlotResponse> proposedSlots(UUID interviewId) {
        OffsetDateTime now = OffsetDateTime.now();
        return slotRepository.findByInterviewIdAndStatusOrderByStartsAt(interviewId, "PROPOSED").stream()
                .filter(s -> s.getStartsAt().isAfter(now))
                .map(InterviewService::toSlotResponse)
                .toList();
    }

    @Transactional
    public InterviewResponse create(CreateInterviewRequest request) {
        Interview interview = new Interview();
        interview.setApplicationId(request.applicationId());
        interview.setType(request.type());
        interview.setDurationMin(request.durationMin() != null ? request.durationMin() : 30);
        interview.setStatus("SCHEDULED");
        interview.setInterviewers(request.interviewers());
        return toResponse(interviewRepository.save(interview));
    }

    /**
     * The hiring team books a time directly. The same guards hold as when a
     * candidate picks one: an application has one interview booked at a time,
     * the interviewers must be free, and their calendars are blocked — so the
     * hour cannot be offered to anyone else. Whatever was on offer to the
     * candidate for this application is withdrawn.
     */
    @Transactional
    public InterviewResponse schedule(UUID id, ScheduleInterviewRequest request) {
        InterviewSlot slot = slotRepository.findById(request.slotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + request.slotId()));
        Interview interview = interviewRepository.lockById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + id));
        Application app = applications.findById(interview.getApplicationId()).orElseThrow();
        List<UUID> ids = participantsFor(interview, app).stream().map(RecruiterUser::getId).toList();
        List<RecruiterUser> team = ids.isEmpty() ? List.of() : recruiterUsers.lockAllByIdIn(ids);
        entityManager.refresh(slot);
        entityManager.refresh(interview);

        refuseSecondBooking(interview);
        if (slot.isBooked() && !slot.getId().equals(interview.getSlotId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time has been taken");
        }
        OffsetDateTime startsAt = request.scheduledAt() != null ? request.scheduledAt() : slot.getStartsAt();
        OffsetDateTime endsAt = startsAt.plusMinutes(interview.getDurationMin());
        if (!startsAt.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time has passed");
        }
        if (!ids.isEmpty() && availability.hasConflict(ids, startsAt, endsAt, interview.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An interviewer is not free at this time. Choose another time.");
        }

        calendarEvents.deleteByInterviewId(interview.getId()); // a time it was booked for before, if any
        interview.setSlotId(slot.getId());
        interview.setScheduledAt(startsAt);
        interview.setStatus("SCHEDULED");
        if (interview.getMeetingLink() == null || interview.getMeetingLink().isBlank()) {
            interview.setMeetingLink("https://teams.microsoft.com/l/meetup-join/19%3Ameeting_" + UUID.randomUUID());
        }
        slot.setBooked(true);
        slot.setStatus("SELECTED");
        slotRepository.save(slot);
        interview.setBookedAt(OffsetDateTime.now());
        Interview saved = interviewRepository.save(interview);
        block(saved, app, team, startsAt, endsAt);
        withdrawOtherOffers(saved);
        events.publishEvent(new InterviewBooked(saved.getId(), saved.getScheduledAt(), BookingOrigin.TEAM));
        return toResponse(saved);
    }

    /** One interview booked for an application at a time: a second would be a second invitation. */
    private void refuseSecondBooking(Interview interview) {
        Interview other = bookedAhead(interview.getApplicationId())
                .filter(i -> !i.getId().equals(interview.getId())).orElse(null);
        if (other != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This application already has an interview booked. Reschedule or cancel that one first.");
        }
    }

    /** The interviewers' calendars are blocked for the hour: invites double as blockers for everyone else. */
    private void block(Interview interview, Application app, List<RecruiterUser> team,
            OffsetDateTime startsAt, OffsetDateTime endsAt) {
        String candidateName = candidates.findById(app.getCandidateId()).map(c -> c.getName()).orElse("Candidate");
        Job job = jobs.findById(app.getJobId()).orElse(null);
        String title = "Interview — " + candidateName + (job != null ? " (" + job.getTitle() + ")" : "");
        for (RecruiterUser member : team) {
            CalendarEvent event = new CalendarEvent();
            event.setUserId(member.getId());
            event.setInterviewId(interview.getId());
            event.setTitle(title);
            event.setStartsAt(startsAt);
            event.setEndsAt(endsAt);
            event.setKind("INTERVIEW");
            calendarEvents.save(event);
        }
    }

    /** Times on offer for the application's other interviews can no longer be picked. */
    private void withdrawOtherOffers(Interview booked) {
        for (Interview other : interviewRepository.findByApplicationId(booked.getApplicationId())) {
            if (!other.getId().equals(booked.getId())
                    && List.of("REQUESTED", "SLOTS_PROPOSED").contains(other.getStatus())) {
                expireSlots(other.getId());
                other.setStatus("REQUESTED");
                interviewRepository.save(other);
            }
        }
    }

    // =====================================================================
    // Calendar-driven self-scheduling (ported from the VMS engine)
    // =====================================================================

    /**
     * Start (or reuse) a candidate self-scheduling interview for an application and
     * propose times computed from the hiring team's calendars. Returns the interview;
     * fetch its options via {@link #proposedSlots}. If the calendars yield nothing,
     * the interview stays REQUESTED and callers may fall back to the per-job pool.
     */
    @Transactional
    public Interview beginSelfSchedule(UUID applicationId, String type, int durationMin) {
        Interview booked = bookedAhead(applicationId).orElse(null);
        if (booked != null) {
            return booked; // already booked: a second interview would be a second invitation
        }
        Interview interview = interviewRepository.findByApplicationId(applicationId).stream()
                .filter(i -> "REQUESTED".equals(i.getStatus()) || "SLOTS_PROPOSED".equals(i.getStatus()))
                .findFirst()
                .orElseGet(() -> {
                    Interview i = new Interview();
                    i.setApplicationId(applicationId);
                    i.setType(type);
                    i.setDurationMin(durationMin);
                    i.setStatus("REQUESTED");
                    return interviewRepository.save(i);
                });
        autoPropose(interview.getId());
        return interviewRepository.findById(interview.getId()).orElseThrow();
    }

    /**
     * Begin self-scheduling for a specific round of the job's interview plan:
     * the round names the interview, sets its length, and its aligned
     * interviewers become the panel the engine schedules around.
     */
    @Transactional
    public Interview beginRound(UUID applicationId, UUID roundId) {
        InterviewRound round = rounds.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));
        Interview booked = bookedAhead(applicationId).orElse(null);
        if (booked != null) {
            return booked;
        }
        Interview interview = interviewRepository.findByApplicationId(applicationId).stream()
                .filter(i -> "REQUESTED".equals(i.getStatus()) || "SLOTS_PROPOSED".equals(i.getStatus()))
                .findFirst()
                .orElseGet(() -> {
                    // Everything the row must have is set before it is saved: what is
                    // saved is what is inserted, and a later change is a second statement.
                    Interview i = new Interview();
                    i.setApplicationId(applicationId);
                    i.setStatus("REQUESTED");
                    i.setType(round.getName());
                    i.setDurationMin(round.getDurationMin());
                    return interviewRepository.save(i);
                });
        interview.setType(round.getName());
        interview.setDurationMin(round.getDurationMin());
        interviewRepository.save(interview);
        List<UUID> panel = roundMembers.findByRoundId(roundId).stream()
                .map(InterviewRoundMember::getUserId)
                .toList();
        autoPropose(interview.getId(), panel);
        return interviewRepository.findById(interview.getId()).orElseThrow();
    }

    /** Compute fresh options from participants' calendars and offer them. */
    @Transactional
    public List<SlotResponse> autoPropose(UUID interviewId) {
        return autoPropose(interviewId, null);
    }

    /**
     * Compute fresh options and offer them. A non-empty {@code panel} replaces the
     * interview's panel first; availability then honors every panelist's calendar.
     */
    @Transactional
    public List<SlotResponse> autoPropose(UUID interviewId, List<UUID> panel) {
        return propose(interviewId, panel, null, List.of());
    }

    /**
     * Offer times later than the ones on offer now — for a candidate none of
     * them suit. The times on offer are retracted; they are not offered again.
     */
    @Transactional
    public List<SlotResponse> proposeLater(UUID interviewId) {
        Interview interview = load(interviewId);
        if (isBooked(interview)) {
            return List.of();
        }
        List<InterviewSlot> current = slotRepository.findByInterviewIdAndStatusOrderByStartsAt(interviewId, "PROPOSED");
        OffsetDateTime after = current.isEmpty() ? null : current.get(current.size() - 1).getStartsAt();
        Application app = applications.findById(interview.getApplicationId()).orElseThrow();
        List<RecruiterUser> team = participantsFor(interview, app);
        List<OffsetDateTime[]> windows = availability.openSlots(
                team.stream().map(RecruiterUser::getId).toList(), interview.getDurationMin(),
                policies.current().getProposalCount(), after,
                current.stream().map(InterviewSlot::getStartsAt).toList(), null);
        if (windows.isEmpty()) {
            return List.of(); // nothing later: what is on offer stays on offer
        }
        return offer(interview, app, team, windows);
    }

    /** The team asks for times to be offered; the candidate is told (see {@link InterviewTimesOffered}). */
    @Transactional
    public List<SlotResponse> proposeByTeam(UUID interviewId, List<UUID> panel) {
        List<SlotResponse> slots = autoPropose(interviewId, panel);
        events.publishEvent(new InterviewTimesOffered(interviewId, InterviewTimesOffered.Why.PROPOSED));
        return slots;
    }

    /** The team starts scheduling for an application; the candidate is told the times. */
    @Transactional
    public Interview beginByTeam(UUID applicationId, String type, int durationMin, UUID roundId) {
        boolean alreadyBooked = bookedAhead(applicationId).isPresent();
        Interview interview = roundId != null
                ? beginRound(applicationId, roundId)
                : beginSelfSchedule(applicationId, type, durationMin);
        if (!alreadyBooked) {
            events.publishEvent(new InterviewTimesOffered(interview.getId(), InterviewTimesOffered.Why.PROPOSED));
        }
        return interview;
    }

    /** True when the interview has a time that is still to come. */
    public static boolean isBooked(Interview interview) {
        return "SCHEDULED".equals(interview.getStatus())
                && interview.getScheduledAt() != null
                && interview.getScheduledAt().isAfter(OffsetDateTime.now());
    }

    /** The application's interview that is booked for a time still to come, if it has one. */
    public Optional<Interview> bookedAhead(UUID applicationId) {
        return interviewRepository.findByApplicationId(applicationId).stream()
                .filter(InterviewService::isBooked)
                .min(java.util.Comparator.comparing(Interview::getScheduledAt));
    }

    private List<SlotResponse> propose(UUID interviewId, List<UUID> panel, OffsetDateTime after, List<OffsetDateTime> avoid) {
        Interview interview = load(interviewId);
        if (isBooked(interview)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This interview is booked — reschedule it to offer other times.");
        }
        Application app = applications.findById(interview.getApplicationId()).orElseThrow();
        if (panel != null && !panel.isEmpty()) {
            panelists.deleteByInterviewId(interviewId);
            for (UUID userId : panel.stream().distinct().toList()) {
                panelists.save(new InterviewPanelist(interviewId, userId));
            }
        }
        List<RecruiterUser> team = participantsFor(interview, app);
        List<UUID> ids = team.stream().map(RecruiterUser::getId).toList();
        List<OffsetDateTime[]> windows = availability.openSlots(
                ids, interview.getDurationMin(), policies.current().getProposalCount(), after, avoid, null);
        return offer(interview, app, team, windows);
    }

    /** Put these times on offer, retracting whatever was on offer before. */
    private List<SlotResponse> offer(Interview interview, Application app, List<RecruiterUser> team, List<OffsetDateTime[]> windows) {
        UUID interviewId = interview.getId();
        expireSlots(interviewId);

        List<InterviewSlot> proposed = new ArrayList<>();
        for (OffsetDateTime[] w : windows) {
            InterviewSlot slot = new InterviewSlot();
            slot.setJobId(app.getJobId());
            slot.setInterviewerId(team.isEmpty() ? null : team.get(0).getId());
            slot.setInterviewId(interviewId);
            slot.setStartsAt(w[0]);
            slot.setEndsAt(w[1]);
            slot.setBooked(false);
            slot.setStatus("PROPOSED");
            proposed.add(slot);
        }
        slotRepository.saveAll(proposed);

        if (proposed.isEmpty() && !team.isEmpty()) {
            // Every panelist's calendar is blocked across the horizon — tell
            // them directly, and alert recruiting so they can intervene.
            String candidate = candidateName(interview);
            for (RecruiterUser member : team) {
                notifications.notifyUser(member.getId(), "SCHEDULING",
                        "Your calendar is blocking an interview",
                        "No open times found for " + candidate + " (" + interview.getType()
                                + "). Free up time or adjust your availability rules.",
                        "/availability");
            }
            notifications.notifyRole("RECRUITER", "SCHEDULING",
                    "Interview scheduling blocked",
                    "No open times for " + candidate + " — every panelist's calendar is full. "
                            + "Consider a different panel or ask interviewers to open time.",
                    "/interviews");
        }

        interview.setStatus(proposed.isEmpty() ? "REQUESTED" : "SLOTS_PROPOSED");
        interview.setInterviewers(team.stream().map(RecruiterUser::getName).collect(Collectors.joining(", ")));
        interviewRepository.save(interview);
        return proposed.stream().map(InterviewService::toSlotResponse).toList();
    }

    /**
     * Candidate picks a proposed slot. Re-validates every participant's calendar at
     * booking time; a stale slot is expired and rejected with 409 (the expiry must
     * survive the exception — hence noRollbackFor).
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Interview selectProposedSlot(UUID slotId) {
        return selectProposedSlot(slotId, BookingOrigin.WEB_PAGE);
    }

    /**
     * Book a proposed time. One booking runs at a time for an interview and for
     * each of its interviewers: the interview and the panel are held first, and
     * only then is the time checked — so two people picking the same hour for
     * the same interviewer cannot both be told it is theirs.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Interview selectProposedSlot(UUID slotId, BookingOrigin origin) {
        InterviewSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        if (slot.getInterviewId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time is no longer available");
        }
        Interview interview = interviewRepository.lockById(slot.getInterviewId())
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + slot.getInterviewId()));
        Application app = applications.findById(interview.getApplicationId()).orElseThrow();
        List<UUID> ids = participantsFor(interview, app).stream().map(RecruiterUser::getId).toList();
        List<RecruiterUser> team = ids.isEmpty() ? List.of() : recruiterUsers.lockAllByIdIn(ids);
        // What was read before the wait may have changed while waiting.
        entityManager.refresh(slot);
        entityManager.refresh(interview);

        if (!"PROPOSED".equals(slot.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time is no longer available");
        }
        if (isBooked(interview)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This interview is already booked");
        }
        refuseSecondBooking(interview);
        if (!slot.getStartsAt().isAfter(OffsetDateTime.now())) {
            slot.setStatus("EXPIRED");
            slotRepository.save(slot);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time has passed");
        }

        if (!ids.isEmpty() && availability.hasConflict(ids, slot.getStartsAt(), slot.getEndsAt(), interview.getId())) {
            slot.setStatus("EXPIRED");
            slotRepository.save(slot);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time was just taken — fresh options coming up");
        }

        slot.setBooked(true);
        slot.setStatus("SELECTED");
        slotRepository.save(slot);
        for (InterviewSlot other : slotRepository.findByInterviewIdAndStatusOrderByStartsAt(interview.getId(), "PROPOSED")) {
            if (!other.getId().equals(slot.getId())) {
                other.setStatus("EXPIRED");
                slotRepository.save(other);
            }
        }

        interview.setSlotId(slot.getId());
        interview.setScheduledAt(slot.getStartsAt());
        interview.setStatus("SCHEDULED");
        interview.setMeetingLink("https://teams.microsoft.com/l/meetup-join/19%3Ameeting_" + UUID.randomUUID());
        interview.setBookedAt(OffsetDateTime.now());
        interviewRepository.save(interview);
        events.publishEvent(new InterviewBooked(interview.getId(), interview.getScheduledAt(), origin));

        block(interview, app, team, slot.getStartsAt(), slot.getEndsAt());
        withdrawOtherOffers(interview);

        app.setStage("INTERVIEW");
        applications.save(app);
        return interview;
    }

    /**
     * Candidate-driven reschedule — POLICIED, unlike the recruiter's
     * {@link #reschedule}: limited to {@code reschedule_limit} times and blocked
     * inside the {@code reschedule_cutoff_hours} window before the interview.
     *
     * @throws ResponseStatusException 422 when a policy blocks the reschedule
     */
    @Transactional
    public List<SlotResponse> candidateReschedule(UUID interviewId) {
        RescheduleAttempt attempt = tryCandidateReschedule(interviewId);
        return switch (attempt.result()) {
            case MOVED -> attempt.slots();
            case REFUSED -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, attempt.refusal().message());
            case NO_OTHER_TIMES -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No other times are open right now — your interview stays as booked.");
            // Nothing booked to give up: offering times again costs nothing.
            case NOT_BOOKED -> proposedSlotsOrPropose(interviewId);
        };
    }

    public enum RescheduleResult { MOVED, NO_OTHER_TIMES, REFUSED, NOT_BOOKED }

    /**
     * @param refusal set when the result is {@link RescheduleResult#REFUSED}
     * @param was     the time that was booked
     * @param slots   the times now on offer, when the result is {@link RescheduleResult#MOVED}
     */
    public record RescheduleAttempt(
            RescheduleResult result, RescheduleRefusal refusal, OffsetDateTime was, List<SlotResponse> slots) {
    }

    /**
     * A candidate asks to move a booked interview. The policy is asked first;
     * then other times are looked for while the booking still stands. Only
     * when there is something to move to is the booking given up and the
     * change counted — a candidate is never left with no interview and one
     * change fewer. Never throws for a reason the candidate should be told.
     */
    @Transactional
    public RescheduleAttempt tryCandidateReschedule(UUID interviewId) {
        Interview interview = interviewRepository.lockById(interviewId)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + interviewId));
        entityManager.refresh(interview);
        OffsetDateTime was = interview.getScheduledAt();
        if (!isBooked(interview)) {
            return new RescheduleAttempt(RescheduleResult.NOT_BOOKED, null, was, List.of());
        }
        RescheduleRefusal refusal = rescheduleRefusal(interviewId).orElse(null);
        if (refusal != null) {
            return new RescheduleAttempt(RescheduleResult.REFUSED, refusal, was, List.of());
        }
        Application app = applications.findById(interview.getApplicationId()).orElseThrow();
        List<RecruiterUser> team = participantsFor(interview, app);
        List<OffsetDateTime[]> windows = availability.openSlots(
                team.stream().map(RecruiterUser::getId).toList(), interview.getDurationMin(),
                policies.current().getProposalCount(), null, List.of(was), interviewId);
        if (windows.isEmpty()) {
            return new RescheduleAttempt(RescheduleResult.NO_OTHER_TIMES, null, was, List.of());
        }
        interview.setRescheduleCount(interview.getRescheduleCount() + 1);
        giveUpTime(interview);
        List<SlotResponse> slots = offer(interview, app, team, windows);
        notifications.notifyRole("RECRUITER", "RESCHEDULE",
                "Candidate rescheduled: " + candidateName(interview),
                typeLabel(interview) + " — new times proposed", "/interviews");
        return new RescheduleAttempt(RescheduleResult.MOVED, null, was, slots);
    }

    /** Why a candidate may not move an interview. */
    public enum RescheduleRefusalReason { LIMIT_REACHED, TOO_CLOSE }

    /**
     * @param message what the self-schedule page shows
     * @param hours   the cutoff, for {@link RescheduleRefusalReason#TOO_CLOSE}
     */
    public record RescheduleRefusal(RescheduleRefusalReason reason, String message, int hours) {
    }

    /**
     * The policy's answer to "may this candidate move this interview?", without
     * changing anything — so a caller can tell the candidate why not, instead
     * of catching a refusal that has already spoiled its transaction.
     */
    public Optional<RescheduleRefusal> rescheduleRefusal(UUID interviewId) {
        Interview interview = load(interviewId);
        SchedulingPolicy policy = policies.current();
        if (interview.getRescheduleCount() >= policy.getRescheduleLimit()) {
            return Optional.of(new RescheduleRefusal(RescheduleRefusalReason.LIMIT_REACHED,
                    "Reschedule limit reached — a recruiter will reach out to help.", 0));
        }
        if (interview.getScheduledAt() != null && interview.getScheduledAt()
                .isBefore(OffsetDateTime.now().plusHours(policy.getRescheduleCutoffHours()))) {
            return Optional.of(new RescheduleRefusal(RescheduleRefusalReason.TOO_CLOSE,
                    "The interview is less than " + policy.getRescheduleCutoffHours()
                            + " hours away — please contact your recruiter to change it.",
                    policy.getRescheduleCutoffHours()));
        }
        return Optional.empty();
    }

    /**
     * Candidate-driven cancel: frees the slot and every calendar immediately and
     * alerts recruiting so re-engagement can start.
     */
    @Transactional
    public void candidateCancel(UUID interviewId) {
        Interview interview = load(interviewId);
        transition(interviewId, "CANCELED");
        notifications.notifyRole("RECRUITER", "CANCELLED",
                "Interview cancelled by candidate: " + candidateName(interview),
                typeLabel(interview), "/interviews");
    }

    private String candidateName(Interview interview) {
        return applications.findById(interview.getApplicationId())
                .flatMap((a) -> candidates.findById(a.getCandidateId()))
                .map((c) -> c.getName())
                .orElse("Candidate");
    }

    private static String typeLabel(Interview i) {
        return i.getType() == null ? "Interview" : i.getType().replace('_', ' ').toLowerCase();
    }

    /** Free the booked time and immediately re-offer fresh calendar options. */
    @Transactional
    public List<SlotResponse> reschedule(UUID interviewId) {
        return release(interviewId);
    }

    /**
     * The hiring team moves an interview: the time is freed, fresh options are
     * offered, and the candidate is told (see {@link InterviewReleasedByTeam}).
     */
    @Transactional
    public List<SlotResponse> rescheduleByTeam(UUID interviewId) {
        Interview interview = load(interviewId);
        if (isBooked(interview)) {
            // Look before letting go: a candidate is not left with no interview and nothing to choose from.
            Application app = applications.findById(interview.getApplicationId()).orElseThrow();
            List<UUID> ids = participantsFor(interview, app).stream().map(RecruiterUser::getId).toList();
            boolean somewhereToGo = !availability.openSlots(ids, interview.getDurationMin(), 1, null,
                    List.of(interview.getScheduledAt()), interviewId).isEmpty();
            if (!somewhereToGo) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No other time is open for these interviewers, so the interview stays as booked. "
                                + "Open time in their calendars, change the panel, or cancel the interview.");
            }
        }
        List<SlotResponse> slots = release(interviewId);
        events.publishEvent(new InterviewTimesOffered(interviewId, InterviewTimesOffered.Why.MOVED));
        return slots;
    }

    /** The hiring team cancels an interview; a candidate who was booked is told. */
    @Transactional
    public InterviewResponse cancelByTeam(UUID interviewId) {
        Interview interview = load(interviewId);
        OffsetDateTime was = isBooked(interview) ? interview.getScheduledAt() : null;
        InterviewResponse response = transition(interviewId, "CANCELED");
        if (was != null) {
            events.publishEvent(new InterviewCanceled(interviewId, interview.getApplicationId(), was));
        }
        return response;
    }

    /**
     * An application was closed: whatever interview it was waiting for or
     * booked for is cancelled with it, so nobody is reminded of, or can book,
     * an interview for an application that is no longer running.
     */
    @Transactional
    public void cancelForApplication(UUID applicationId) {
        for (Interview interview : interviewRepository.findByApplicationId(applicationId)) {
            if (List.of("REQUESTED", "SLOTS_PROPOSED", "SCHEDULED").contains(interview.getStatus())
                    && (interview.getScheduledAt() == null || interview.getScheduledAt().isAfter(OffsetDateTime.now()))) {
                cancelByTeam(interview.getId());
            }
        }
    }

    /** Free the booked time and offer others — never the time just given up. */
    private List<SlotResponse> release(UUID interviewId) {
        Interview interview = load(interviewId);
        OffsetDateTime was = interview.getScheduledAt();
        giveUpTime(interview);
        return propose(interviewId, null, null, was == null ? List.of() : List.of(was));
    }

    private void giveUpTime(Interview interview) {
        calendarEvents.deleteByInterviewId(interview.getId());
        interview.setSlotId(null);
        interview.setScheduledAt(null);
        interview.setMeetingLink(null);
        interview.setBookedAt(null);
        if ("SCHEDULED".equals(interview.getStatus())) {
            interview.setStatus("REQUESTED");
        }
        interviewRepository.save(interview);
    }

    /** COMPLETED | CANCELED | NO_SHOW. Cancel/no-show free the calendars immediately. */
    @Transactional
    public InterviewResponse transition(UUID interviewId, String status) {
        if (!List.of("COMPLETED", "CANCELED", "NO_SHOW").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown transition: " + status);
        }
        Interview interview = load(interviewId);
        interview.setStatus(status);
        if (!"COMPLETED".equals(status)) {
            calendarEvents.deleteByInterviewId(interviewId);
            expireSlots(interviewId);
        }
        return toResponse(interviewRepository.save(interview));
    }

    /**
     * The interview's hiring team: its panel when one is set, otherwise the job's
     * hiring manager + recruiter.
     */
    public List<RecruiterUser> participantsFor(Interview interview, Application app) {
        List<UUID> panel = panelists.findByInterviewId(interview.getId()).stream()
                .map(InterviewPanelist::getUserId)
                .toList();
        if (!panel.isEmpty()) {
            return recruiterUsers.findAllById(panel);
        }
        Job job = jobs.findById(app.getJobId()).orElse(null);
        List<UUID> ids = new ArrayList<>();
        if (job != null && job.getHiringManagerId() != null) {
            ids.add(job.getHiringManagerId());
        }
        if (job != null && job.getRecruiterId() != null && !ids.contains(job.getRecruiterId())) {
            ids.add(job.getRecruiterId());
        }
        return recruiterUsers.findAllById(ids);
    }

    private void expireSlots(UUID interviewId) {
        for (InterviewSlot slot : slotRepository.findByInterviewId(interviewId)) {
            if ("PROPOSED".equals(slot.getStatus()) || "SELECTED".equals(slot.getStatus())) {
                slot.setStatus("EXPIRED");
                slot.setBooked(false);
                slotRepository.save(slot);
            }
        }
    }

    private Interview load(UUID id) {
        return interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + id));
    }

    public static InterviewResponse toResponse(Interview i) {
        return new InterviewResponse(
                i.getId(),
                i.getApplicationId(),
                i.getType(),
                i.getScheduledAt(),
                i.getDurationMin(),
                i.getStatus(),
                splitNames(i.getInterviewers()),
                i.getScore(),
                i.getRecommendation(),
                i.getSummary(),
                i.getMeetingLink());
    }

    static SlotResponse toSlotResponse(InterviewSlot s) {
        return new SlotResponse(
                s.getId(),
                s.getJobId(),
                s.getInterviewerId(),
                null,
                s.getStartsAt(),
                s.getEndsAt(),
                s.isBooked(),
                s.getInterviewId(),
                s.getStatus());
    }

    private static List<String> splitNames(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
