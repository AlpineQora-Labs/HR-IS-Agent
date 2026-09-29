package com.taportal.domain.journey;

import static org.assertj.core.api.Assertions.assertThat;

import com.taportal.domain.interview.Interview;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JourneyReminderJobTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-06T14:00:00Z");

    private static Interview booked(OffsetDateTime bookedAt) {
        Interview i = new Interview();
        i.setStatus("SCHEDULED");
        i.setScheduledAt(AT);
        i.setBookedAt(bookedAt);
        return i;
    }

    @Test
    void theDayBeforeReminderIsDueFromADayBeforeUntilHalfADayBefore() {
        Interview i = booked(AT.minusDays(5));

        assertThat(JourneyReminderJob.due(i, AT.minusHours(25))).isEmpty();
        assertThat(JourneyReminderJob.due(i, AT.minusHours(24))).contains(JourneyPoint.INTERVIEW_REMINDER_24H);
        assertThat(JourneyReminderJob.due(i, AT.minusHours(18))).contains(JourneyPoint.INTERVIEW_REMINDER_24H);
        assertThat(JourneyReminderJob.due(i, AT.minusHours(12))).isEmpty();
        assertThat(JourneyReminderJob.due(i, AT.minusHours(3))).isEmpty();
    }

    @Test
    @DisplayName("booked less than a day ahead: the confirmation is the reminder")
    void aLateBookingGetsNoDayBeforeReminder() {
        Interview i = booked(AT.minusHours(22));

        assertThat(JourneyReminderJob.due(i, AT.minusHours(21))).isEmpty();
        assertThat(JourneyReminderJob.due(i, AT.minusHours(13))).isEmpty();
        assertThat(JourneyReminderJob.due(i, AT.minusMinutes(50))).contains(JourneyPoint.INTERVIEW_REMINDER_1H);
    }

    @Test
    void theLastHourReminderIsDueInTheLastHour() {
        Interview i = booked(AT.minusDays(5));

        assertThat(JourneyReminderJob.due(i, AT.minusMinutes(61))).isEmpty();
        assertThat(JourneyReminderJob.due(i, AT.minusMinutes(60))).contains(JourneyPoint.INTERVIEW_REMINDER_1H);
        assertThat(JourneyReminderJob.due(i, AT.minusMinutes(1))).contains(JourneyPoint.INTERVIEW_REMINDER_1H);
    }

    @Test
    void nothingIsDueOnceTheInterviewHasBegun() {
        Interview i = booked(AT.minusDays(5));

        assertThat(JourneyReminderJob.due(i, AT)).isEmpty();
        assertThat(JourneyReminderJob.due(i, AT.plusHours(1))).isEmpty();
    }

    @Test
    void anInterviewWithNoTimeHasNothingDue() {
        Interview i = new Interview();
        i.setStatus("SLOTS_PROPOSED");

        assertThat(JourneyReminderJob.due(i, AT)).isEmpty();
    }
}
