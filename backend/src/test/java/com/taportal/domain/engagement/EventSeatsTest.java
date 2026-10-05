package com.taportal.domain.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.taportal.domain.engagement.EventSeats.State;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventSeatsTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    private static final OffsetDateTime STARTS = NOW.plusDays(10);

    @Test
    @DisplayName("an event with no capacity takes everyone until it starts")
    void noLimit() {
        assertThat(EventSeats.state(null, false, null, STARTS, 5000, NOW)).isEqualTo(State.OPEN);
        assertThat(EventSeats.left(null, 5000)).isNull();
    }

    @Test
    @DisplayName("there is a seat while fewer are taken than the event seats")
    void seatFree() {
        assertThat(EventSeats.state(50, false, null, STARTS, 49, NOW)).isEqualTo(State.OPEN);
        assertThat(EventSeats.left(50, 49)).isEqualTo(1);
    }

    @Test
    @DisplayName("a full event with a waitlist takes names for it")
    void fullWithWaitlist() {
        assertThat(EventSeats.state(50, true, null, STARTS, 50, NOW)).isEqualTo(State.WAITLIST);
    }

    @Test
    @DisplayName("a full event without a waitlist refuses")
    void fullWithoutWaitlist() {
        assertThat(EventSeats.state(50, false, null, STARTS, 50, NOW)).isEqualTo(State.FULL);
        assertThat(EventSeats.left(50, 53)).isZero();
    }

    @Test
    @DisplayName("registration is closed from the deadline on, however many seats are free")
    void closedAtDeadline() {
        assertThat(EventSeats.state(50, true, NOW, STARTS, 0, NOW)).isEqualTo(State.CLOSED);
        assertThat(EventSeats.state(50, true, NOW.plusSeconds(1), STARTS, 0, NOW)).isEqualTo(State.OPEN);
    }

    @Test
    @DisplayName("with no deadline, registration closes when the event starts")
    void closedOnceStarted() {
        assertThat(EventSeats.state(null, false, null, NOW.minusMinutes(1), 0, NOW)).isEqualTo(State.CLOSED);
    }

    @Test
    @DisplayName("a no-show keeps the seat they were given; a cancelled or waitlisted student holds none")
    void whoHoldsASeat() {
        assertThat(EventSeats.holdsSeat("REGISTERED")).isTrue();
        assertThat(EventSeats.holdsSeat("CHECKED_IN")).isTrue();
        assertThat(EventSeats.holdsSeat("NO_SHOW")).isTrue();
        assertThat(EventSeats.holdsSeat("WAITLISTED")).isFalse();
        assertThat(EventSeats.holdsSeat("CANCELLED")).isFalse();
    }
}
