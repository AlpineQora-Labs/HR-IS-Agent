package com.taportal.domain.interview;

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
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Computes open interview times from all participants' calendars (ported from
 * the proven VMS scheduling engine). Policy:
 *
 * <ul>
 *   <li>Business hours 9:00–17:00 ET, weekdays only, 30-minute grid, 14-day window
 *   <li>Minimum candidate notice: 20 hours
 *   <li>15-minute buffer around existing INTERVIEW events (no back-to-backs)
 *   <li>At most 2 proposals per day (spread options across days)
 *   <li>At most 2 interviews per day per participant (interviewer-fairness cap)
 * </ul>
 */
@Service
public class AvailabilityService {

    static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final LocalTime DAY_START = LocalTime.of(9, 0);
    private static final LocalTime DAY_END = LocalTime.of(17, 0);
    private static final Duration MIN_NOTICE = Duration.ofHours(20);
    private static final Duration INTERVIEW_BUFFER = Duration.ofMinutes(15);
    private static final int WINDOW_DAYS = 14;
    private static final int MAX_PER_DAY = 2;
    private static final int MAX_INTERVIEWS_PER_DAY_PER_PERSON = 2;

    private final CalendarEventRepository calendarEvents;

    public AvailabilityService(CalendarEventRepository calendarEvents) {
        this.calendarEvents = calendarEvents;
    }

    /** Up to {@code count} open [start,end) windows that fit every participant. */
    public List<OffsetDateTime[]> openSlots(List<UUID> participantIds, int durationMin, int count) {
        ZonedDateTime earliest = ZonedDateTime.now(ZONE).plus(MIN_NOTICE);
        ZonedDateTime windowEnd = ZonedDateTime.now(ZONE).plusDays(WINDOW_DAYS);
        List<CalendarEvent> busy = calendarEvents
                .findByUserIdInAndStartsAtLessThanAndEndsAtGreaterThan(
                        participantIds, windowEnd.toOffsetDateTime(), OffsetDateTime.now());

        Map<LocalDate, Integer> perDay = new HashMap<>();
        List<OffsetDateTime[]> out = new ArrayList<>();

        for (LocalDate day = earliest.toLocalDate(); !day.isAfter(windowEnd.toLocalDate()); day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() >= 6) {
                continue; // weekend
            }
            if (overCapDay(day, participantIds, busy)) {
                continue; // a participant already has too many interviews that day
            }
            for (LocalTime t = DAY_START; !t.plusMinutes(durationMin).isAfter(DAY_END); t = t.plusMinutes(30)) {
                ZonedDateTime start = day.atTime(t).atZone(ZONE);
                if (start.isBefore(earliest)) {
                    continue;
                }
                ZonedDateTime end = start.plusMinutes(durationMin);
                if (conflicts(busy, start.toOffsetDateTime(), end.toOffsetDateTime(), null)) {
                    continue;
                }
                int used = perDay.getOrDefault(day, 0);
                if (used >= MAX_PER_DAY) {
                    break;
                }
                perDay.put(day, used + 1);
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
        List<CalendarEvent> busy = calendarEvents
                .findByUserIdInAndStartsAtLessThanAndEndsAtGreaterThan(
                        participantIds, end.plus(INTERVIEW_BUFFER), start.minus(INTERVIEW_BUFFER));
        return conflicts(busy, start, end, ignoreInterviewId);
    }

    private static boolean conflicts(List<CalendarEvent> events, OffsetDateTime start, OffsetDateTime end, UUID ignoreInterviewId) {
        for (CalendarEvent e : events) {
            if (ignoreInterviewId != null && ignoreInterviewId.equals(e.getInterviewId())) {
                continue;
            }
            OffsetDateTime s = e.getStartsAt();
            OffsetDateTime f = e.getEndsAt();
            if ("INTERVIEW".equals(e.getKind())) {
                s = s.minus(INTERVIEW_BUFFER);
                f = f.plus(INTERVIEW_BUFFER);
            }
            if (s.isBefore(end) && f.isAfter(start)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overCapDay(LocalDate day, List<UUID> participantIds, List<CalendarEvent> busy) {
        for (UUID user : participantIds) {
            int n = 0;
            for (CalendarEvent e : busy) {
                if ("INTERVIEW".equals(e.getKind()) && user.equals(e.getUserId())
                        && e.getStartsAt().atZoneSameInstant(ZONE).toLocalDate().equals(day)) {
                    n++;
                }
            }
            if (n >= MAX_INTERVIEWS_PER_DAY_PER_PERSON) {
                return true;
            }
        }
        return false;
    }
}
