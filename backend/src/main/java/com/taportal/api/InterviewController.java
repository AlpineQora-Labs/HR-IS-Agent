package com.taportal.api;

import com.taportal.api.InterviewDtos.CreateInterviewRequest;
import com.taportal.api.InterviewDtos.InterviewResponse;
import com.taportal.api.InterviewDtos.ScheduleInterviewRequest;
import com.taportal.api.InterviewDtos.SlotResponse;
import com.taportal.domain.interview.InterviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/v1/interviews")
    public List<InterviewResponse> list(@RequestParam UUID applicationId) {
        return interviewService.listByApplication(applicationId);
    }

    @PostMapping("/v1/interviews")
    public InterviewResponse create(@Valid @RequestBody CreateInterviewRequest request) {
        return interviewService.create(request);
    }

    @PostMapping("/v1/interviews/{id}/schedule")
    public InterviewResponse schedule(@PathVariable UUID id, @Valid @RequestBody ScheduleInterviewRequest request) {
        return interviewService.schedule(id, request);
    }

    @GetMapping("/v1/slots")
    public List<SlotResponse> slots(@RequestParam UUID jobId) {
        return interviewService.openSlots(jobId);
    }

    // ---- Calendar-driven self-scheduling (ported from the VMS engine) ----

    /**
     * Recruiter one-click: compute options from the hiring team's calendars and offer
     * them. An optional body {@code {interviewerUserIds: [...]}} sets the panel first.
     */
    public record BeginSelfScheduleRequest(
            java.util.UUID applicationId, String type, Integer durationMin, java.util.UUID roundId) {}

    /** Start (or reuse) candidate self-scheduling; the returned id backs /schedule/:id. */
    @PostMapping("/v1/interviews/self-schedule")
    public InterviewDtos.InterviewResponse beginSelfSchedule(@RequestBody BeginSelfScheduleRequest request) {
        var interview = interviewService.beginByTeam(
                request.applicationId(),
                request.type() == null ? "RECRUITER_SCREEN" : request.type(),
                request.durationMin() == null ? 45 : request.durationMin(),
                request.roundId());
        return InterviewService.toResponse(interview);
    }

    @PostMapping("/v1/interviews/{id}/propose")
    public List<SlotResponse> propose(
            @PathVariable UUID id,
            @RequestBody(required = false) SchedulingDtos.ProposeRequest request) {
        return interviewService.proposeByTeam(id, request == null ? null : request.interviewerUserIds());
    }

    /** Day by day, every time this panel could hold the interview. Read only. */
    @GetMapping("/v1/interviews/{id}/openings")
    public List<SchedulingDtos.DayOpeningsDto> openings(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(required = false) List<UUID> interviewerUserIds,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "42") int days) {
        java.time.LocalDate from = java.time.LocalDate.now();
        return interviewService.openingsFor(id, interviewerUserIds, from, from.plusDays(Math.max(1, Math.min(days, 90)))).stream()
                .map(d -> new SchedulingDtos.DayOpeningsDto(
                        d.day(), d.state().name(), d.why(), d.times().stream().map(t -> t[0]).toList()))
                .toList();
    }

    /** The hiring team books a time it picked for the candidate. 409 if it is no longer free. */
    @PostMapping("/v1/interviews/{id}/book-time")
    public InterviewResponse bookTime(@PathVariable UUID id, @RequestBody SchedulingDtos.BookTimeRequest request) {
        return InterviewService.toResponse(
                interviewService.bookByTeam(id, request.startsAt(), request.interviewerUserIds()));
    }

    /** The options currently awaiting the candidate. */
    @GetMapping("/v1/interviews/{id}/proposed-slots")
    public List<SlotResponse> proposedSlots(@PathVariable UUID id) {
        return interviewService.proposedSlots(id);
    }

    /** Candidate (or Aria on their behalf) books a proposed time. 409 if just taken. */
    @PostMapping("/v1/interview-slots/{id}/select")
    public InterviewResponse select(@PathVariable UUID id) {
        return InterviewService.toResponse(
                interviewService.selectProposedSlot(id, com.taportal.domain.events.BookingOrigin.TEAM));
    }

    /** Free the time and immediately re-offer fresh calendar options. */
    @PostMapping("/v1/interviews/{id}/reschedule")
    public List<SlotResponse> reschedule(@PathVariable UUID id) {
        return interviewService.rescheduleByTeam(id);
    }

    /** COMPLETED | CANCELED | NO_SHOW (cancel/no-show free the calendars). */
    @PostMapping("/v1/interviews/{id}/transition")
    public InterviewResponse transition(@PathVariable UUID id, @Valid @RequestBody InterviewDtos.TransitionRequest request) {
        return "CANCELED".equals(request.status())
                ? interviewService.cancelByTeam(id)
                : interviewService.transition(id, request.status());
    }
}
