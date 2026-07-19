package com.taportal.domain.interview;

import com.taportal.api.InterviewDtos.CreateInterviewRequest;
import com.taportal.api.InterviewDtos.InterviewResponse;
import com.taportal.api.InterviewDtos.ScheduleInterviewRequest;
import com.taportal.api.InterviewDtos.SlotResponse;
import com.taportal.domain.application.Application;
import com.taportal.domain.application.ApplicationRepository;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.JobRepository;
import com.taportal.domain.recruiter.RecruiterUser;
import com.taportal.domain.recruiter.RecruiterUserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class InterviewService {

    private static final int PROPOSAL_COUNT = 4;

    private final InterviewRepository interviewRepository;
    private final InterviewSlotRepository slotRepository;
    private final CalendarEventRepository calendarEvents;
    private final AvailabilityService availability;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final CandidateRepository candidates;
    private final RecruiterUserRepository recruiterUsers;

    public InterviewService(
            InterviewRepository interviewRepository,
            InterviewSlotRepository slotRepository,
            CalendarEventRepository calendarEvents,
            AvailabilityService availability,
            ApplicationRepository applications,
            JobRepository jobs,
            CandidateRepository candidates,
            RecruiterUserRepository recruiterUsers) {
        this.interviewRepository = interviewRepository;
        this.slotRepository = slotRepository;
        this.calendarEvents = calendarEvents;
        this.availability = availability;
        this.applications = applications;
        this.jobs = jobs;
        this.candidates = candidates;
        this.recruiterUsers = recruiterUsers;
    }

    public List<InterviewResponse> listByApplication(UUID applicationId) {
        return interviewRepository.findByApplicationId(applicationId).stream().map(InterviewService::toResponse).toList();
    }

    public List<SlotResponse> openSlots(UUID jobId) {
        return slotRepository.findByJobIdAndBookedFalse(jobId).stream().map(InterviewService::toSlotResponse).toList();
    }

    public List<SlotResponse> proposedSlots(UUID interviewId) {
        return slotRepository.findByInterviewIdAndStatusOrderByStartsAt(interviewId, "PROPOSED").stream()
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

    @Transactional
    public InterviewResponse schedule(UUID id, ScheduleInterviewRequest request) {
        Interview interview = load(id);
        InterviewSlot slot = slotRepository.findById(request.slotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + request.slotId()));

        interview.setSlotId(slot.getId());
        interview.setScheduledAt(request.scheduledAt() != null ? request.scheduledAt() : slot.getStartsAt());
        slot.setBooked(true);
        slot.setStatus("SELECTED");
        slotRepository.save(slot);
        return toResponse(interviewRepository.save(interview));
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

    /** Compute fresh options from participants' calendars and offer them. */
    @Transactional
    public List<SlotResponse> autoPropose(UUID interviewId) {
        Interview interview = load(interviewId);
        Application app = applications.findById(interview.getApplicationId()).orElseThrow();
        List<RecruiterUser> team = participantsFor(app);

        // Retract any previous offer before making a new one.
        expireSlots(interviewId);

        List<UUID> ids = team.stream().map(RecruiterUser::getId).toList();
        List<OffsetDateTime[]> windows = availability.openSlots(ids, interview.getDurationMin(), PROPOSAL_COUNT);

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
        InterviewSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        if (!"PROPOSED".equals(slot.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time is no longer available");
        }
        Interview interview = load(slot.getInterviewId());
        Application app = applications.findById(interview.getApplicationId()).orElseThrow();
        List<RecruiterUser> team = participantsFor(app);
        List<UUID> ids = team.stream().map(RecruiterUser::getId).toList();

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
        interviewRepository.save(interview);

        // Invites double as availability blockers for every other candidate.
        String candidateName = candidates.findById(app.getCandidateId()).map(c -> c.getName()).orElse("Candidate");
        Job job = jobs.findById(app.getJobId()).orElse(null);
        String title = "Interview — " + candidateName + (job != null ? " (" + job.getTitle() + ")" : "");
        for (RecruiterUser member : team) {
            CalendarEvent event = new CalendarEvent();
            event.setUserId(member.getId());
            event.setInterviewId(interview.getId());
            event.setTitle(title);
            event.setStartsAt(slot.getStartsAt());
            event.setEndsAt(slot.getEndsAt());
            event.setKind("INTERVIEW");
            calendarEvents.save(event);
        }

        app.setStage("INTERVIEW");
        applications.save(app);
        return interview;
    }

    /** Free the booked time and immediately re-offer fresh calendar options. */
    @Transactional
    public List<SlotResponse> reschedule(UUID interviewId) {
        Interview interview = load(interviewId);
        calendarEvents.deleteByInterviewId(interviewId);
        interview.setSlotId(null);
        interview.setScheduledAt(null);
        interview.setMeetingLink(null);
        interviewRepository.save(interview);
        return autoPropose(interviewId);
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

    /** Hiring team for an application: the job's hiring manager + recruiter. */
    public List<RecruiterUser> participantsFor(Application app) {
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
