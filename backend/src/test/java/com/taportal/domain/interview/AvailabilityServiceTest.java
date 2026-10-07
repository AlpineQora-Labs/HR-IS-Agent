package com.taportal.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.taportal.domain.interview.AvailabilityService.DayOpenings;
import com.taportal.domain.interview.AvailabilityService.DayState;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What is open, read a month at a time and a time at a time. Monday 5 October 2026 stands still. */
class AvailabilityServiceTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final UUID ELENA = UUID.randomUUID();
    private static final UUID DAVID = UUID.randomUUID();

    private final CalendarEventRepository calendar = mock(CalendarEventRepository.class);
    private final SchedulingPolicyRepository policies = mock(SchedulingPolicyRepository.class);
    private final InterviewerSettingsRepository settings = mock(InterviewerSettingsRepository.class);
    private final InterviewerWeeklyRuleRepository rules = mock(InterviewerWeeklyRuleRepository.class);

    private final List<CalendarEvent> events = new ArrayList<>();
    private final List<InterviewerSettings> personal = new ArrayList<>();
    private final List<InterviewerWeeklyRule> weekly = new ArrayList<>();

    @BeforeEach
    void thePolicy() {
        SchedulingPolicy policy = new SchedulingPolicy();
        policy.setMinNoticeHours(20);
        policy.setHorizonDays(14);
        policy.setBufferMinutes(15);
        policy.setProposalCount(3);
        when(policies.current()).thenReturn(policy);
        when(calendar.findByUserIdInAndStartsAtLessThanAndEndsAtGreaterThan(any(), any(), any())).thenReturn(events);
        when(settings.findByUserIdIn(any())).thenReturn(personal);
        when(rules.findByUserIdIn(any())).thenReturn(weekly);
    }

    /** The engine, at this hour of Monday 5 October 2026, Eastern Time. */
    private AvailabilityService at(int hour, int minute) {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 5, hour, minute, 0, 0, ET);
        return new AvailabilityService(calendar, policies, settings, rules, Clock.fixed(now.toInstant(), ET));
    }

    private static OffsetDateTime et(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ET).toOffsetDateTime();
    }

    private void onCalendar(UUID who, String kind, OffsetDateTime from, OffsetDateTime to, UUID interviewId) {
        CalendarEvent e = new CalendarEvent();
        e.setUserId(who);
        e.setKind(kind);
        e.setStartsAt(from);
        e.setEndsAt(to);
        e.setInterviewId(interviewId);
        events.add(e);
    }

    private Map<Integer, DayOpenings> october(AvailabilityService engine, List<UUID> who, int minutes) {
        return engine.openings(who, minutes, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null).stream()
                .collect(Collectors.toMap(d -> d.day().getDayOfMonth(), Function.identity()));
    }

    private static List<String> starts(DayOpenings day) {
        return day.times().stream().map(t -> t[0].atZoneSameInstant(ET).toLocalTime().toString()).toList();
    }

    @Nested
    @DisplayName("a month")
    class AMonth {

        @Test
        void everyDayOfTheMonthIsAccountedFor() {
            List<DayOpenings> days = at(10, 0).openings(List.of(ELENA), 45,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null);

            assertThat(days).hasSize(31);
            assertThat(days).extracting(DayOpenings::day).isSorted();
        }

        @Test
        void daysGoneByArePast() {
            Map<Integer, DayOpenings> m = october(at(10, 0), List.of(ELENA), 45);

            assertThat(List.of(m.get(1), m.get(2), m.get(3), m.get(4))).extracting(DayOpenings::state)
                    .containsOnly(DayState.PAST);
        }

        @Test
        @DisplayName("today and any day inside the notice candidates are given cannot be booked, and say why")
        void insideTheNoticeNothingIsOpen() {
            Map<Integer, DayOpenings> m = october(at(10, 0), List.of(ELENA), 45);

            assertThat(m.get(5).state()).isEqualTo(DayState.OUTSIDE);
            assertThat(m.get(5).why()).isEqualTo("Inside the 20 hours' notice candidates are given");
            assertThat(m.get(5).times()).isEmpty();
        }

        @Test
        @DisplayName("an ordinary working day is open at every half hour an interview fits inside working hours")
        void aWorkingDayIsOpenOnTheHalfHour() {
            DayOpenings tuesday = october(at(10, 0), List.of(ELENA), 45).get(6);

            assertThat(tuesday.state()).isEqualTo(DayState.OPEN);
            assertThat(tuesday.why()).isNull();
            assertThat(starts(tuesday)).startsWith("09:00", "09:30", "10:00").endsWith("15:30", "16:00").hasSize(15);
            assertThat(tuesday.times().get(0)[1]).isEqualTo(et(6, 9, 45));
        }

        @Test
        @DisplayName("on the day the notice runs out, only the times after it are open")
        void theNoticeCutsTheDay() {
            DayOpenings tuesday = october(at(14, 0), List.of(ELENA), 45).get(6);

            assertThat(tuesday.state()).isEqualTo(DayState.OPEN);
            assertThat(starts(tuesday)).startsWith("10:00").doesNotContain("09:00", "09:30");
        }

        @Test
        @DisplayName("when the notice swallows the whole working day, the day is outside, not fully booked")
        void aDayTheNoticeSwallowsIsOutside() {
            DayOpenings tuesday = october(at(23, 0), List.of(ELENA), 45).get(6);

            assertThat(tuesday.state()).isEqualTo(DayState.OUTSIDE);
            assertThat(tuesday.why()).startsWith("Inside the 20 hours' notice");
        }

        @Test
        void weekendsAreOutside() {
            Map<Integer, DayOpenings> m = october(at(10, 0), List.of(ELENA), 45);

            assertThat(m.get(10).state()).isEqualTo(DayState.OUTSIDE);
            assertThat(m.get(10).why()).isEqualTo("Weekend");
            assertThat(m.get(11).why()).isEqualTo("Weekend");
        }

        @Test
        @DisplayName("the policy schedules fourteen days ahead: the last of them is open, the day after is not")
        void beyondTheHorizonNothingIsOpen() {
            Map<Integer, DayOpenings> m = october(at(10, 0), List.of(ELENA), 45);

            assertThat(m.get(19).state()).isEqualTo(DayState.OPEN);
            assertThat(m.get(20).state()).isEqualTo(DayState.OUTSIDE);
            assertThat(m.get(20).why()).isEqualTo("Beyond the 14 days interviews are scheduled ahead");
        }

        @Test
        @DisplayName("a day with a calendar that holds every hour is fully booked")
        void aDayWithNothingFreeIsFull() {
            onCalendar(ELENA, "BUSY", et(7, 8, 0), et(7, 18, 0), null);

            DayOpenings wednesday = october(at(10, 0), List.of(ELENA), 45).get(7);

            assertThat(wednesday.state()).isEqualTo(DayState.FULL);
            assertThat(wednesday.why()).isEqualTo("No time is free in the interviewers' calendars");
        }

        @Test
        @DisplayName("an interviewer at their limit for the day makes the day fully booked, though hours are free")
        void aDayAtTheLimitIsFull() {
            onCalendar(ELENA, "INTERVIEW", et(7, 9, 0), et(7, 9, 45), UUID.randomUUID());
            onCalendar(ELENA, "INTERVIEW", et(7, 14, 0), et(7, 14, 45), UUID.randomUUID());

            DayOpenings wednesday = october(at(10, 0), List.of(ELENA), 45).get(7);

            assertThat(wednesday.state()).isEqualTo(DayState.FULL);
            assertThat(wednesday.why()).isEqualTo("An interviewer has reached their limit of interviews");
        }

        @Test
        @DisplayName("an interview in the calendar takes its own time and the buffer either side")
        void anInterviewTakesItsBuffersToo() {
            onCalendar(ELENA, "INTERVIEW", et(7, 10, 0), et(7, 10, 45), UUID.randomUUID());

            DayOpenings wednesday = october(at(10, 0), List.of(ELENA), 45).get(7);

            assertThat(starts(wednesday)).contains("09:00", "11:00").doesNotContain("09:30", "10:00", "10:30");
        }

        @Test
        void tentativeHoldsDoNotTakeTime() {
            onCalendar(ELENA, "TENTATIVE", et(7, 9, 0), et(7, 17, 0), null);

            assertThat(october(at(10, 0), List.of(ELENA), 45).get(7).state()).isEqualTo(DayState.OPEN);
        }

        @Test
        @DisplayName("a time is open only when it is open for everyone on the panel")
        void everyoneOnThePanelHasToBeFree() {
            onCalendar(DAVID, "BUSY", et(7, 9, 0), et(7, 13, 0), null);

            DayOpenings wednesday = october(at(10, 0), List.of(ELENA, DAVID), 45).get(7);

            assertThat(starts(wednesday)).startsWith("13:00").doesNotContain("09:00", "12:30");
        }

        @Test
        @DisplayName("working hours are each person's own, in their own time zone")
        void hoursAreEachPersonsOwn() {
            InterviewerSettings pacific = new InterviewerSettings();
            pacific.setUserId(DAVID);
            pacific.setWorkStart(LocalTime.of(9, 0));
            pacific.setWorkEnd(LocalTime.of(17, 0));
            pacific.setTimezone("America/Los_Angeles");
            pacific.setMaxPerDay(2);
            pacific.setMaxPerWeek(8);
            personal.add(pacific);

            DayOpenings wednesday = october(at(10, 0), List.of(ELENA, DAVID), 45).get(7);

            // David starts at noon Eastern; Elena stops at five.
            assertThat(starts(wednesday)).startsWith("12:00").endsWith("16:00");
        }

        @Test
        @DisplayName("a weekly rule against interviews on a morning closes that morning")
        void aWeeklyRuleClosesItsHours() {
            weekly.add(new InterviewerWeeklyRule(UUID.randomUUID(), ELENA, 3,
                    LocalTime.of(9, 0), LocalTime.of(12, 0), "NO_INTERVIEWS"));

            DayOpenings wednesday = october(at(10, 0), List.of(ELENA), 45).get(7);

            assertThat(starts(wednesday)).startsWith("12:00").doesNotContain("09:00", "11:30");
        }

        @Test
        @DisplayName("a day nobody takes interviews on is outside their hours, not fully booked")
        void aDayOutsideEveryonesHoursIsOutside() {
            weekly.add(new InterviewerWeeklyRule(UUID.randomUUID(), ELENA, 2,
                    LocalTime.of(13, 0), LocalTime.of(15, 0), "INTERVIEW_BLOCK"));

            Map<Integer, DayOpenings> m = october(at(10, 0), List.of(ELENA), 45);

            assertThat(m.get(7).state()).isEqualTo(DayState.OUTSIDE);
            assertThat(m.get(7).why()).isEqualTo("Outside the hours the interviewers take interviews");
            assertThat(starts(m.get(6))).containsExactly("13:00", "13:30", "14:00");
        }

        @Test
        void aLongerInterviewHasFewerTimes() {
            assertThat(starts(october(at(10, 0), List.of(ELENA), 90).get(6))).endsWith("15:00", "15:30").hasSize(14);
        }

        @Test
        @DisplayName("an interview being moved does not stand in its own way")
        void theInterviewBeingMovedIsIgnored() {
            UUID moving = UUID.randomUUID();
            onCalendar(ELENA, "INTERVIEW", et(7, 10, 0), et(7, 10, 45), moving);

            List<DayOpenings> days = at(10, 0).openings(List.of(ELENA), 45,
                    LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 7), moving);

            assertThat(starts(days.get(0))).contains("09:30", "10:00", "10:30");
        }

        @Test
        void withNobodyChosenNothingIsOpen() {
            Map<Integer, DayOpenings> m = october(at(10, 0), List.of(), 45);

            assertThat(m.get(6).state()).isEqualTo(DayState.OUTSIDE);
            assertThat(m.get(6).why()).isEqualTo("No interviewers are chosen");
        }

        @Test
        void aRangeThatRunsBackwardsHoldsNothing() {
            assertThat(at(10, 0).openings(List.of(ELENA), 45,
                    LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 1), null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("one time somebody chose")
    class OneTime {

        @Test
        void anOpenTimeIsOpen() {
            assertThat(at(10, 0).whyNotOpen(List.of(ELENA), et(6, 10, 30), 45, null)).isEmpty();
        }

        @Test
        @DisplayName("a time is judged as the clock stands when it is booked, not when it was shown")
        void everyReasonIsSaid() {
            AvailabilityService engine = at(10, 0);
            onCalendar(ELENA, "BUSY", et(8, 13, 0), et(8, 14, 0), null);

            assertThat(engine.whyNotOpen(List.of(ELENA), et(6, 10, 15), 45, null))
                    .contains("it is not a time interviews are scheduled at");
            assertThat(engine.whyNotOpen(List.of(ELENA), et(6, 8, 0), 45, null))
                    .contains("it is not a time interviews are scheduled at");
            assertThat(engine.whyNotOpen(List.of(ELENA), et(5, 15, 0), 45, null))
                    .contains("it is inside the 20 hours' notice candidates are given");
            assertThat(engine.whyNotOpen(List.of(ELENA), et(20, 10, 0), 45, null))
                    .contains("it is beyond the 14 days interviews are scheduled ahead");
            assertThat(engine.whyNotOpen(List.of(ELENA), et(10, 10, 0), 45, null)).contains("it is at a weekend");
            assertThat(engine.whyNotOpen(List.of(ELENA), et(6, 16, 30), 45, null))
                    .contains("it is outside an interviewer's working hours");
            assertThat(engine.whyNotOpen(List.of(ELENA), et(8, 13, 30), 45, null))
                    .contains("an interviewer is not free then");
            assertThat(engine.whyNotOpen(List.of(), et(6, 10, 0), 45, null)).contains("no interviewers are chosen");
            assertThat(engine.whyNotOpen(List.of(ELENA), null, 45, null)).contains("no time was given");
        }

        @Test
        void theLimitForTheDayRefusesATimeThatIsFree() {
            onCalendar(ELENA, "INTERVIEW", et(7, 9, 0), et(7, 9, 45), UUID.randomUUID());
            onCalendar(ELENA, "INTERVIEW", et(7, 14, 0), et(7, 14, 45), UUID.randomUUID());

            assertThat(at(10, 0).whyNotOpen(List.of(ELENA), et(7, 11, 30), 45, null))
                    .contains("an interviewer has reached their limit of interviews");
        }

        @Test
        void aTimeGivenInAnotherZoneIsReadAsTheMomentItIs() {
            OffsetDateTime sameMoment = et(6, 10, 30).withOffsetSameInstant(java.time.ZoneOffset.UTC);

            assertThat(at(10, 0).whyNotOpen(List.of(ELENA), sameMoment, 45, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("the month and the proposals agree")
    class Agreement {

        @Test
        @DisplayName("every time the system would propose is a time the month shows as open, and the reverse check holds")
        void whatIsProposedIsOpenInTheMonth() {
            onCalendar(ELENA, "INTERVIEW", et(7, 10, 0), et(7, 10, 45), UUID.randomUUID());
            onCalendar(DAVID, "BUSY", et(8, 9, 0), et(8, 15, 0), null);
            weekly.add(new InterviewerWeeklyRule(UUID.randomUUID(), ELENA, 5,
                    LocalTime.of(12, 0), LocalTime.of(17, 0), "NO_INTERVIEWS"));
            AvailabilityService engine = at(14, 0);
            List<UUID> panel = List.of(ELENA, DAVID);

            List<OffsetDateTime> open = engine.openings(panel, 45,
                            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null).stream()
                    .flatMap(d -> d.times().stream()).map(t -> t[0]).toList();
            List<OffsetDateTime> proposed = engine.openSlots(panel, 45, 40).stream().map(t -> t[0]).toList();

            assertThat(proposed).isNotEmpty();
            assertThat(open).containsAll(proposed);
            for (OffsetDateTime time : open) {
                assertThat(engine.whyNotOpen(panel, time, 45, null)).as("%s", time).isEmpty();
            }
        }

        @Test
        @DisplayName("the proposals keep their own rule: two a day at most, two hours apart")
        void proposalsStaySpread() {
            List<OffsetDateTime[]> proposed = at(10, 0).openSlots(List.of(ELENA), 45, 3);

            assertThat(proposed).extracting(t -> t[0]).containsExactly(et(6, 9, 0), et(6, 12, 0), et(7, 9, 0));
        }
    }
}
