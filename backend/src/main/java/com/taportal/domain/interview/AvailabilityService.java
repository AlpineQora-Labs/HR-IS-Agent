package com.taportal.domain.interview;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.temporal.WeekFields;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Computes open interview times from all participants' calendars (ported from
 * the proven VMS scheduling engine, hardened in Phase 1). Guardrails come from
 * data, not constants:
 *
 * <ul>
 *   <li>{@link SchedulingPolicy}: minimum candidate notice, search horizon,
 *       interview buffers, reschedule limits (enforced in InterviewService)
 *   <li>{@link InterviewerSettings}: per-person working window in the person's
 *       own timezone, plus daily and weekly interview caps. No row = defaults
 *       (9:00–17:00 ET, 2/day, 8/week).
 *   <li>Weekdays only, 30-minute grid, at most 2 proposals per day so options
 *       spread across days.
 * </ul>
 *
 * <p>Whether one time can be booked is decided in one place, {@link #blocked},
 * and everything that reads availability asks it: the times the system
 * proposes ({@link #openSlots}), the month a recruiter looks at
 * ({@link #openings}), and the check of a time somebody chose
 * ({@link #whyNotOpen}). They cannot come to disagree.
 */
@Service
public class AvailabilityService {

    static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(17, 0);
    /** Times are looked for on a half-hour grid from 09:00; an interview ends by this hour, Eastern Time. */
    private static final LocalTime GRID_END = LocalTime.of(20, 0);
    private static final int GRID_MINUTES = 30;
    private static final int DEFAULT_MAX_PER_DAY = 2;
    private static final int DEFAULT_MAX_PER_WEEK = 8;
    private static final int MAX_PROPOSALS_PER_DAY = 2;
    /** Two times offered on one day are this far apart at least, so they are a real choice. */
    private static final Duration SAME_DAY_SPACING = Duration.ofHours(2);
    private static final WeekFields WEEK = WeekFields.of(Locale.US);

    /** Calendar kinds that do NOT block scheduling (soft/informational). */
    private static final java.util.Set<String> NON_BLOCKING = java.util.Set.of("TENTATIVE", "IN_OFFICE");

    private final CalendarEventRepository calendarEvents;
    private final SchedulingPolicyRepository policies;
    private final InterviewerSettingsRepository settingsRepository;
    private final InterviewerWeeklyRuleRepository weeklyRules;
    private final Clock clock;

    @Autowired
    public AvailabilityService(
            CalendarEventRepository calendarEvents,
            SchedulingPolicyRepository policies,
            InterviewerSettingsRepository settingsRepository,
            InterviewerWeeklyRuleRepository weeklyRules) {
        this(calendarEvents, policies, settingsRepository, weeklyRules, Clock.system(ZONE));
    }

    /** @param clock what "now" is: the system's, except where a test needs a day to stand still */
    AvailabilityService(
            CalendarEventRepository calendarEvents,
            SchedulingPolicyRepository policies,
            InterviewerSettingsRepository settingsRepository,
            InterviewerWeeklyRuleRepository weeklyRules,
            Clock clock) {
        this.calendarEvents = calendarEvents;
        this.policies = policies;
        this.settingsRepository = settingsRepository;
        this.weeklyRules = weeklyRules;
        this.clock = clock;
    }

    /** What a day holds for a set of interviewers and a length of interview. */
    public enum DayState {
        /** Before today. */
        PAST,
        /** Nothing could be booked that day whatever the calendars held: a weekend, too soon, too far ahead, outside hours. */
        OUTSIDE,
        /** A working day inside the policy, with nothing free. */
        FULL,
        /** At least one time can be booked. */
        OPEN
    }

    /**
     * @param why   for a day that is not open, the reason in words; null otherwise
     * @param times every time that can be booked that day, earliest first, as [start, end)
     */
    public record DayOpenings(LocalDate day, DayState state, String why, List<OffsetDateTime[]> times) {
    }

    /** Why one time cannot be booked. */
    private enum Block { OUTSIDE_HOURS, WEEKLY_RULE, BUSY }

    /** Everything a reading of availability needs, read once. */
    private record Frame(
            SchedulingPolicy policy, ZonedDateTime now, ZonedDateTime earliest, ZonedDateTime horizon,
            Duration buffer, List<CalendarEvent> busy,
            Map<UUID, InterviewerSettings> settings, Map<UUID, List<InterviewerWeeklyRule>> rules) {
    }

    private Frame frame(List<UUID> participantIds) {
        SchedulingPolicy policy = policies.current();
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
        ZonedDateTime horizon = now.plusDays(policy.getHorizonDays());
        List<CalendarEvent> busy = participantIds.isEmpty() ? List.of() : calendarEvents
                .findByUserIdInAndStartsAtLessThanAndEndsAtGreaterThan(
                        participantIds, horizon.plusDays(1).toOffsetDateTime(), now.toOffsetDateTime());
        return new Frame(policy, now, now.plus(Duration.ofHours(policy.getMinNoticeHours())), horizon,
                Duration.ofMinutes(policy.getBufferMinutes()), busy,
                settingsFor(participantIds), rulesFor(participantIds));
    }

    /**
     * Why a time cannot be booked for these people, or null when it can: it
     * sits inside everybody's working hours, breaks nobody's weekly rule, and
     * meets nothing in anybody's calendar.
     */
    private static Block blocked(
            Frame f, List<UUID> participantIds, ZonedDateTime start, ZonedDateTime end, UUID movingInterview) {
        if (!withinEveryWorkWindow(participantIds, f.settings(), start, end)) {
            return Block.OUTSIDE_HOURS;
        }
        if (!weeklyRulesAllow(participantIds, f.settings(), f.rules(), start, end)) {
            return Block.WEEKLY_RULE;
        }
        if (conflicts(f.busy(), start.toOffsetDateTime(), end.toOffsetDateTime(), movingInterview, f.buffer())) {
            return Block.BUSY;
        }
        return null;
    }

    /**
     * Each day from {@code from} to {@code to}, as it stands for these people
     * and this length of interview: whether anything can be booked, every time
     * that can, and when nothing can, why.
     *
     * @param movingInterview an interview whose own booking is to be ignored, because it is
     *                        the one being moved; null otherwise
     */
    public List<DayOpenings> openings(
            List<UUID> participantIds, int durationMin, LocalDate from, LocalDate to, UUID movingInterview) {
        List<DayOpenings> days = new ArrayList<>();
        if (from == null || to == null || to.isBefore(from)) {
            return days;
        }
        Frame f = frame(participantIds);
        LocalDate today = f.now().toLocalDate();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (day.isBefore(today)) {
                days.add(new DayOpenings(day, DayState.PAST, null, List.of()));
            } else if (participantIds.isEmpty()) {
                days.add(new DayOpenings(day, DayState.OUTSIDE, "No interviewers are chosen", List.of()));
            } else if (day.getDayOfWeek().getValue() >= 6) {
                days.add(new DayOpenings(day, DayState.OUTSIDE, "Weekend", List.of()));
            } else if (day.isBefore(f.earliest().toLocalDate())) {
                days.add(new DayOpenings(day, DayState.OUTSIDE, tooSoon(f), List.of()));
            } else if (day.isAfter(f.horizon().toLocalDate())) {
                days.add(new DayOpenings(day, DayState.OUTSIDE, tooFar(f), List.of()));
            } else if (overCap(day, participantIds, f.settings(), f.busy(), movingInterview)) {
                days.add(new DayOpenings(day, DayState.FULL,
                        "An interviewer has reached their limit of interviews", List.of()));
            } else {
                days.add(onTheGrid(f, participantIds, durationMin, day, movingInterview));
            }
        }
        return days;
    }

    /** A working day inside the policy, read time by time. */
    private static DayOpenings onTheGrid(
            Frame f, List<UUID> participantIds, int durationMin, LocalDate day, UUID movingInterview) {
        List<OffsetDateTime[]> times = new ArrayList<>();
        boolean takenInHours = false;   // a time inside everybody's hours that a calendar holds
        boolean soonInHours = false;    // a time inside everybody's hours that comes too soon
        for (LocalTime t = DEFAULT_START; !t.plusMinutes(durationMin).isAfter(GRID_END); t = t.plusMinutes(GRID_MINUTES)) {
            ZonedDateTime start = day.atTime(t).atZone(ZONE);
            ZonedDateTime end = start.plusMinutes(durationMin);
            Block block = blocked(f, participantIds, start, end, movingInterview);
            if (start.isBefore(f.earliest())) {
                soonInHours = soonInHours || block == null || block == Block.BUSY;
            } else if (block == null) {
                times.add(new OffsetDateTime[] {start.toOffsetDateTime(), end.toOffsetDateTime()});
            } else if (block == Block.BUSY) {
                takenInHours = true;
            }
        }
        if (!times.isEmpty()) {
            return new DayOpenings(day, DayState.OPEN, null, times);
        }
        if (takenInHours) {
            return new DayOpenings(day, DayState.FULL, "No time is free in the interviewers' calendars", List.of());
        }
        if (soonInHours) {
            return new DayOpenings(day, DayState.OUTSIDE, tooSoon(f), List.of());
        }
        return new DayOpenings(day, DayState.OUTSIDE, "Outside the hours the interviewers take interviews", List.of());
    }

    /**
     * Why a time somebody chose cannot be booked, in words that follow the
     * time ("… is no longer open: an interviewer is not free then"); empty
     * when it can. The same rules as {@link #openings}, asked of one time —
     * at the moment of booking, whatever a screen showed a minute ago.
     */
    public Optional<String> whyNotOpen(
            List<UUID> participantIds, OffsetDateTime startsAt, int durationMin, UUID movingInterview) {
        if (startsAt == null) {
            return Optional.of("no time was given");
        }
        if (participantIds.isEmpty()) {
            return Optional.of("no interviewers are chosen");
        }
        Frame f = frame(participantIds);
        ZonedDateTime start = startsAt.atZoneSameInstant(ZONE);
        ZonedDateTime end = start.plusMinutes(durationMin);
        LocalTime t = start.toLocalTime();
        if (t.getSecond() != 0 || t.getNano() != 0 || t.getMinute() % GRID_MINUTES != 0
                || t.isBefore(DEFAULT_START) || t.plusMinutes(durationMin).isAfter(GRID_END)
                || !end.toLocalDate().equals(start.toLocalDate())) {
            return Optional.of("it is not a time interviews are scheduled at");
        }
        if (start.isBefore(f.earliest())) {
            return Optional.of("it is " + lowerFirst(tooSoon(f)));
        }
        LocalDate day = start.toLocalDate();
        if (day.isAfter(f.horizon().toLocalDate())) {
            return Optional.of("it is " + lowerFirst(tooFar(f)));
        }
        if (day.getDayOfWeek().getValue() >= 6) {
            return Optional.of("it is at a weekend");
        }
        if (overCap(day, participantIds, f.settings(), f.busy(), movingInterview)) {
            return Optional.of("an interviewer has reached their limit of interviews");
        }
        Block block = blocked(f, participantIds, start, end, movingInterview);
        if (block == null) {
            return Optional.empty();
        }
        return Optional.of(switch (block) {
            case OUTSIDE_HOURS -> "it is outside an interviewer's working hours";
            case WEEKLY_RULE -> "an interviewer does not take interviews then";
            case BUSY -> "an interviewer is not free then";
        });
    }

    private static String tooSoon(Frame f) {
        return "Inside the " + f.policy().getMinNoticeHours() + " hours' notice candidates are given";
    }

    private static String tooFar(Frame f) {
        return "Beyond the " + f.policy().getHorizonDays() + " days interviews are scheduled ahead";
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Up to {@code count} open [start,end) windows that fit every participant. */
    public List<OffsetDateTime[]> openSlots(List<UUID> participantIds, int durationMin, int count) {
        return openSlots(participantIds, durationMin, count, null, List.of(), null);
    }

    /**
     * Up to {@code count} open [start,end) windows that fit every participant.
     * Times offered on the same day never overlap and sit at least
     * {@link #SAME_DAY_SPACING} apart.
     *
     * @param after           only times starting after this; null for the earliest allowed
     * @param avoid           starts not to offer: times offered before, or the time being given up
     * @param movingInterview an interview whose own booking is to be ignored, because it is
     *                        the one being moved; null otherwise
     */
    public List<OffsetDateTime[]> openSlots(
            List<UUID> participantIds, int durationMin, int count,
            OffsetDateTime after, List<OffsetDateTime> avoid, UUID movingInterview) {
        if (count <= 0) {
            return List.of();
        }
        java.util.Set<java.time.Instant> skip = new java.util.HashSet<>();
        for (OffsetDateTime a : avoid == null ? List.<OffsetDateTime>of() : avoid) {
            if (a != null) {
                skip.add(a.toInstant());
            }
        }
        Frame f = frame(participantIds);
        ZonedDateTime earliest = f.earliest();
        ZonedDateTime windowEnd = f.horizon();
        List<CalendarEvent> busy = f.busy();
        Map<UUID, InterviewerSettings> settings = f.settings();

        Map<LocalDate, Integer> perDay = new HashMap<>();
        Map<LocalDate, ZonedDateTime> freeFrom = new HashMap<>();
        List<OffsetDateTime[]> out = new ArrayList<>();

        for (LocalDate day = earliest.toLocalDate(); !day.isAfter(windowEnd.toLocalDate()); day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() >= 6) {
                continue; // weekend
            }
            if (overCap(day, participantIds, settings, busy, movingInterview)) {
                continue; // a participant is at their daily or weekly cap
            }
            for (LocalTime t = DEFAULT_START; !t.plusMinutes(durationMin).isAfter(GRID_END); t = t.plusMinutes(GRID_MINUTES)) {
                ZonedDateTime start = day.atTime(t).atZone(ZONE);
                if (start.isBefore(earliest)) {
                    continue;
                }
                if (after != null && !start.toInstant().isAfter(after.toInstant())) {
                    continue;
                }
                if (skip.contains(start.toInstant())) {
                    continue;
                }
                ZonedDateTime notBefore = freeFrom.get(day);
                if (notBefore != null && start.isBefore(notBefore)) {
                    continue; // too close to a time already offered that day
                }
                ZonedDateTime end = start.plusMinutes(durationMin);
                if (blocked(f, participantIds, start, end, movingInterview) != null) {
                    continue;
                }
                int used = perDay.getOrDefault(day, 0);
                if (used >= MAX_PROPOSALS_PER_DAY) {
                    break;
                }
                perDay.put(day, used + 1);
                freeFrom.put(day, end.plus(SAME_DAY_SPACING));
                out.add(new OffsetDateTime[] {start.toOffsetDateTime(), end.toOffsetDateTime()});
                if (out.size() >= count) {
                    return out;
                }
            }
        }
        return out;
    }

    /** True if any participant has an overlapping event (with interview buffers applied). */
    public boolean hasConflict(List<UUID> participantIds, OffsetDateTime start, OffsetDateTime end, UUID ignoreInterviewId) {
        Duration buffer = Duration.ofMinutes(policies.current().getBufferMinutes());
        List<CalendarEvent> busy = calendarEvents
                .findByUserIdInAndStartsAtLessThanAndEndsAtGreaterThan(
                        participantIds, end.plus(buffer), start.minus(buffer));
        return conflicts(busy, start, end, ignoreInterviewId, buffer);
    }

    private Map<UUID, List<InterviewerWeeklyRule>> rulesFor(List<UUID> participantIds) {
        Map<UUID, List<InterviewerWeeklyRule>> map = new HashMap<>();
        for (InterviewerWeeklyRule r : weeklyRules.findByUserIdIn(participantIds)) {
            map.computeIfAbsent(r.getUserId(), (k) -> new ArrayList<>()).add(r);
        }
        return map;
    }

    /**
     * Weekly preferences, per participant and in that participant's zone:
     * the slot must dodge every NO_INTERVIEWS blackout, and when a person has
     * INTERVIEW_BLOCK rules it must sit entirely inside one of them.
     */
    private static boolean weeklyRulesAllow(
            List<UUID> participantIds, Map<UUID, InterviewerSettings> settings,
            Map<UUID, List<InterviewerWeeklyRule>> rules,
            ZonedDateTime start, ZonedDateTime end) {
        for (UUID user : participantIds) {
            List<InterviewerWeeklyRule> userRules = rules.getOrDefault(user, List.of());
            if (userRules.isEmpty()) {
                continue;
            }
            InterviewerSettings s = settings.get(user);
            ZoneId zone = s == null ? ZONE : ZoneId.of(s.getTimezone());
            ZonedDateTime ls = start.withZoneSameInstant(zone);
            ZonedDateTime le = end.withZoneSameInstant(zone);
            int day = ls.getDayOfWeek().getValue();
            boolean hasBlocks = false;
            boolean insideABlock = false;
            for (InterviewerWeeklyRule r : userRules) {
                if ("INTERVIEW_BLOCK".equals(r.getKind())) {
                    hasBlocks = true;
                    if (r.getDayOfWeek() == day
                            && !ls.toLocalTime().isBefore(r.getStartTime())
                            && !le.toLocalTime().isAfter(r.getEndTime())) {
                        insideABlock = true;
                    }
                } else if ("NO_INTERVIEWS".equals(r.getKind()) && r.getDayOfWeek() == day) {
                    // blackout intersects the slot?
                    if (ls.toLocalTime().isBefore(r.getEndTime()) && le.toLocalTime().isAfter(r.getStartTime())) {
                        return false;
                    }
                }
            }
            if (hasBlocks && !insideABlock) {
                return false;
            }
        }
        return true;
    }

    private Map<UUID, InterviewerSettings> settingsFor(List<UUID> participantIds) {
        Map<UUID, InterviewerSettings> map = new HashMap<>();
        for (InterviewerSettings s : settingsRepository.findByUserIdIn(participantIds)) {
            map.put(s.getUserId(), s);
        }
        return map;
    }

    /** The slot must sit inside EVERY participant's working window, evaluated in their own zone. */
    private static boolean withinEveryWorkWindow(
            List<UUID> participantIds, Map<UUID, InterviewerSettings> settings,
            ZonedDateTime start, ZonedDateTime end) {
        for (UUID user : participantIds) {
            InterviewerSettings s = settings.get(user);
            LocalTime ws = s == null ? DEFAULT_START : s.getWorkStart();
            LocalTime we = s == null ? DEFAULT_END : s.getWorkEnd();
            ZoneId zone = s == null ? ZONE : ZoneId.of(s.getTimezone());
            ZonedDateTime ls = start.withZoneSameInstant(zone);
            ZonedDateTime le = end.withZoneSameInstant(zone);
            if (!ls.toLocalDate().equals(le.toLocalDate())
                    || ls.toLocalTime().isBefore(ws)
                    || le.toLocalTime().isAfter(we)) {
                return false;
            }
        }
        return true;
    }

    private static boolean conflicts(
            List<CalendarEvent> events, OffsetDateTime start, OffsetDateTime end,
            UUID ignoreInterviewId, Duration buffer) {
        for (CalendarEvent e : events) {
            if (ignoreInterviewId != null && ignoreInterviewId.equals(e.getInterviewId())) {
                continue;
            }
            if (NON_BLOCKING.contains(e.getKind())) {
                continue; // tentative holds and in-office markers don't block
            }
            OffsetDateTime s = e.getStartsAt();
            OffsetDateTime f = e.getEndsAt();
            if ("INTERVIEW".equals(e.getKind())) {
                s = s.minus(buffer);
                f = f.plus(buffer);
            }
            if (s.isBefore(end) && f.isAfter(start)) {
                return true;
            }
        }
        return false;
    }

    /** Daily AND weekly interview caps, per participant, from their settings. */
    private static boolean overCap(
            LocalDate day, List<UUID> participantIds,
            Map<UUID, InterviewerSettings> settings, List<CalendarEvent> busy, UUID movingInterview) {
        int week = day.get(WEEK.weekOfWeekBasedYear());
        for (UUID user : participantIds) {
            InterviewerSettings s = settings.get(user);
            int maxDay = s == null ? DEFAULT_MAX_PER_DAY : s.getMaxPerDay();
            int maxWeek = s == null ? DEFAULT_MAX_PER_WEEK : s.getMaxPerWeek();
            int onDay = 0;
            int inWeek = 0;
            for (CalendarEvent e : busy) {
                if (!"INTERVIEW".equals(e.getKind()) || !user.equals(e.getUserId())) {
                    continue;
                }
                if (movingInterview != null && movingInterview.equals(e.getInterviewId())) {
                    continue;
                }
                LocalDate d = e.getStartsAt().atZoneSameInstant(ZONE).toLocalDate();
                if (d.equals(day)) {
                    onDay++;
                }
                if (d.get(WEEK.weekOfWeekBasedYear()) == week && d.getYear() == day.getYear()) {
                    inWeek++;
                }
            }
            if (onDay >= maxDay || inWeek >= maxWeek) {
                return true;
            }
        }
        return false;
    }
}
