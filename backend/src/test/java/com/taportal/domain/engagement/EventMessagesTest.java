package com.taportal.domain.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("When an event's timed emails are due")
class EventMessagesTest {

    private static final OffsetDateTime STARTS = OffsetDateTime.parse("2026-10-14T21:00:00Z");
    private static final OffsetDateTime ENDS = STARTS.plusHours(2);

    @Test
    @DisplayName("the reminder goes in the last 24 hours before the start")
    void reminderInTheLastDay() {
        assertThat(EventMessages.reminderDue(STARTS, STARTS.minusHours(25))).isFalse();
        assertThat(EventMessages.reminderDue(STARTS, STARTS.minusHours(24))).isTrue();
        assertThat(EventMessages.reminderDue(STARTS, STARTS.minusMinutes(1))).isTrue();
    }

    @Test
    @DisplayName("no reminder once the event has started")
    void noReminderAfterTheStart() {
        assertThat(EventMessages.reminderDue(STARTS, STARTS)).isFalse();
        assertThat(EventMessages.reminderDue(STARTS, STARTS.plusHours(1))).isFalse();
    }

    @Test
    @DisplayName("the note afterwards waits an hour past the end, so the roster can be marked first")
    void noteWaitsAnHour() {
        assertThat(EventMessages.noteDue(STARTS, ENDS, ENDS.plusMinutes(59))).isFalse();
        assertThat(EventMessages.noteDue(STARTS, ENDS, ENDS.plusHours(1))).isTrue();
    }

    @Test
    @DisplayName("the note is not sent more than three days late")
    void noteNotTooLate() {
        assertThat(EventMessages.noteDue(STARTS, ENDS, ENDS.plusDays(3).minusMinutes(1))).isTrue();
        assertThat(EventMessages.noteDue(STARTS, ENDS, ENDS.plusDays(3))).isFalse();
    }

    @Test
    @DisplayName("an event with no end time is taken to last two hours")
    void noEndTime() {
        assertThat(EventMessages.noteDue(STARTS, null, STARTS.plusHours(2).plusMinutes(59))).isFalse();
        assertThat(EventMessages.noteDue(STARTS, null, STARTS.plusHours(3))).isTrue();
    }
}
