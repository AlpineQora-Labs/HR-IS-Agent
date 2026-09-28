package com.taportal.api;

import com.taportal.domain.interview.AvailabilityService;
import com.taportal.domain.interview.CalendarEvent;
import com.taportal.domain.interview.CalendarEventRepository;
import com.taportal.domain.interview.InterviewRound;
import com.taportal.domain.interview.InterviewRoundMember;
import com.taportal.domain.interview.InterviewRoundMemberRepository;
import com.taportal.domain.interview.InterviewRoundRepository;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.JobRepository;
import com.taportal.domain.interview.InterviewerWeeklyRule;
import com.taportal.domain.interview.InterviewerWeeklyRuleRepository;
import com.taportal.domain.recruiter.RecruiterUser;
import com.taportal.domain.recruiter.RecruiterUserRepository;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-requisition interview plans, defined during intake: ordered rounds with
 * the interviewers the manager aligned to each. The scheduler proposes times
 * from every round member's calendar.
 */
@RestController
@RequestMapping("/v1/interview-plan")
public class InterviewPlanController {

    /** {@code notes} = human-readable availability constraints ("No Mon mornings"). */
    public record MemberDto(UUID userId, String name, String role, List<String> notes) {}

    public record RoundDto(UUID id, int roundNo, String name, int durationMin, List<MemberDto> members) {}

    public record RequisitionRow(
            UUID jobId, String title, String department, String status,
            String recruiterName, String hiringManagerName, int roundCount) {}

    public record RoundRequest(String name, Integer durationMin) {}

    public record MembersRequest(List<UUID> userIds) {}

    /** The engine's live read of a round's calendars: can this panel be booked? */
    public record RoundHealth(java.time.OffsetDateTime nextAvailable, int openSlots, boolean blocked) {}

    private final InterviewRoundRepository rounds;
    private final InterviewRoundMemberRepository members;
    private final JobRepository jobs;
    private final RecruiterUserRepository users;
    private final AvailabilityService availability;
    private final InterviewerWeeklyRuleRepository weeklyRules;
    private final CalendarEventRepository calendarEvents;

    public InterviewPlanController(
            InterviewRoundRepository rounds,
            InterviewRoundMemberRepository members,
            JobRepository jobs,
            RecruiterUserRepository users,
            AvailabilityService availability,
            InterviewerWeeklyRuleRepository weeklyRules,
            CalendarEventRepository calendarEvents) {
        this.rounds = rounds;
        this.members = members;
        this.jobs = jobs;
        this.users = users;
        this.availability = availability;
        this.weeklyRules = weeklyRules;
        this.calendarEvents = calendarEvents;
    }

    /**
     * Live calendar check for a round: the same engine that proposes times to
     * candidates (windows, weekly rules, caps, buffers) previews the round's
     * panel. blocked=true means no bookable time in the whole horizon.
     */
    @GetMapping("/rounds/{roundId}/health")
    public RoundHealth roundHealth(@PathVariable UUID roundId) {
        InterviewRound round = rounds.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));
        List<UUID> ids = members.findByRoundId(roundId).stream()
                .map(InterviewRoundMember::getUserId)
                .toList();
        if (ids.isEmpty()) {
            return new RoundHealth(null, 0, false);
        }
        var slots = availability.openSlots(ids, round.getDurationMin(), 6);
        return new RoundHealth(slots.isEmpty() ? null : slots.get(0)[0], slots.size(), slots.isEmpty());
    }

    /** Requisitions with their hiring team, for the availability screen's plan list. */
    @GetMapping("/requisitions")
    public List<RequisitionRow> requisitions() {
        Map<UUID, RecruiterUser> byId = users.findAll().stream()
                .collect(Collectors.toMap(RecruiterUser::getId, Function.identity()));
        return jobs.findAll().stream()
                .filter((j) -> !"CLOSED".equalsIgnoreCase(j.getStatus()) && !"ARCHIVED".equalsIgnoreCase(j.getStatus()))
                .map((j) -> new RequisitionRow(
                        j.getId(), j.getTitle(), j.getDepartment(), j.getStatus(),
                        name(byId, j.getRecruiterId()), name(byId, j.getHiringManagerId()),
                        rounds.countByJobId(j.getId())))
                .toList();
    }

    @GetMapping
    public List<RoundDto> plan(@RequestParam UUID jobId) {
        return rounds.findByJobIdOrderByRoundNo(jobId).stream().map(this::toDto).toList();
    }

    @PostMapping("/{jobId}/rounds")
    public RoundDto addRound(@PathVariable UUID jobId, @RequestBody RoundRequest req) {
        if (!jobs.existsById(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown requisition");
        }
        int next = rounds.findByJobIdOrderByRoundNo(jobId).stream()
                .mapToInt(InterviewRound::getRoundNo).max().orElse(0) + 1;
        String name = req.name() == null || req.name().isBlank() ? "Round " + next : req.name().trim();
        int duration = req.durationMin() == null || req.durationMin() < 15 ? 45 : req.durationMin();
        return toDto(rounds.save(new InterviewRound(null, jobId, next, name, duration)));
    }

    @PutMapping("/rounds/{roundId}")
    public RoundDto updateRound(@PathVariable UUID roundId, @RequestBody RoundRequest req) {
        InterviewRound round = rounds.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));
        if (req.name() != null && !req.name().isBlank()) {
            round.setName(req.name().trim());
        }
        if (req.durationMin() != null && req.durationMin() >= 15) {
            round.setDurationMin(req.durationMin());
        }
        return toDto(rounds.save(round));
    }

    @DeleteMapping("/rounds/{roundId}")
    @Transactional
    public void deleteRound(@PathVariable UUID roundId) {
        members.deleteByRoundId(roundId);
        rounds.deleteById(roundId);
    }

    /** Replace a round's interviewer lineup. */
    @PutMapping("/rounds/{roundId}/members")
    @Transactional
    public RoundDto setMembers(@PathVariable UUID roundId, @RequestBody MembersRequest req) {
        InterviewRound round = rounds.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));
        members.deleteByRoundId(roundId);
        for (UUID userId : req.userIds().stream().distinct().toList()) {
            if (users.existsById(userId)) {
                members.save(new InterviewRoundMember(null, roundId, userId));
            }
        }
        return toDto(round);
    }

    private RoundDto toDto(InterviewRound r) {
        List<MemberDto> lineup = members.findByRoundId(r.getId()).stream()
                .map((m) -> users.findById(m.getUserId())
                        .map((u) -> new MemberDto(u.getId(), u.getName(), u.getRole(), notesFor(u.getId())))
                        .orElse(null))
                .filter((m) -> m != null)
                .toList();
        return new RoundDto(r.getId(), r.getRoundNo(), r.getName(), r.getDurationMin(), lineup);
    }

    private static final String[] DAY_SHORT = {"", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    /**
     * The constraints a coordinator should know while lining up this person:
     * weekly blackouts, dedicated interview windows, and vacations inside the
     * scheduling horizon — phrased for humans, not for the engine.
     */
    private List<String> notesFor(UUID userId) {
        List<String> notes = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        for (InterviewerWeeklyRule rule : weeklyRules.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId)) {
            String day = DAY_SHORT[rule.getDayOfWeek()];
            if ("NO_INTERVIEWS".equals(rule.getKind())) {
                notes.add("No " + day + " " + windowWord(rule.getStartTime(), rule.getEndTime()));
            } else {
                blocks.add(day + " " + clock(rule.getStartTime()) + "–" + clock(rule.getEndTime()));
            }
        }
        if (!blocks.isEmpty()) {
            notes.add("Interviews only " + String.join(", ", blocks));
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (CalendarEvent e : calendarEvents
                .findByUserIdInAndStartsAtLessThanAndEndsAtGreaterThan(List.of(userId), now.plusDays(14), now)) {
            if ("VACATION".equals(e.getKind())) {
                notes.add("Vacation " + e.getStartsAt().atZoneSameInstant(java.time.ZoneId.of("America/New_York"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d")));
            }
        }
        return notes;
    }

    private static String windowWord(LocalTime start, LocalTime end) {
        if (!end.isAfter(LocalTime.of(12, 30))) {
            return "mornings";
        }
        if (!start.isBefore(LocalTime.of(11, 30)) && end.isAfter(LocalTime.of(17, 0))) {
            return "afternoons";
        }
        return clock(start) + "–" + clock(end);
    }

    private static String clock(LocalTime t) {
        int h12 = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        String ampm = t.getHour() < 12 ? "AM" : "PM";
        return t.getMinute() == 0 ? h12 + " " + ampm : h12 + ":" + String.format("%02d", t.getMinute()) + " " + ampm;
    }

    private static String name(Map<UUID, RecruiterUser> byId, UUID id) {
        RecruiterUser u = id == null ? null : byId.get(id);
        return u == null ? null : u.getName();
    }
}
