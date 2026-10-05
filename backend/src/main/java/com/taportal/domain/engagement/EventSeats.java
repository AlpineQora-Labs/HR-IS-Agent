package com.taportal.domain.engagement;

import java.time.OffsetDateTime;

/**
 * Whether an event can take one more registration, and how. The rules, in one place:
 *
 * <ul>
 *   <li>After the registration deadline, or once the event has started, registration is closed.</li>
 *   <li>With no capacity set there is no limit.</li>
 *   <li>Every seat taken: the next student is waitlisted when the event has a waitlist,
 *       and refused when it has none.</li>
 * </ul>
 *
 * A seat is taken by a student who is registered or checked in. A no-show keeps the seat they
 * were given; a student who cancels, or is still on the waitlist, holds none.
 */
public final class EventSeats {

    private EventSeats() {}

    public enum State {
        /** There is a seat. */
        OPEN,
        /** No seat, but the student can join the waitlist. */
        WAITLIST,
        /** No seat and no waitlist. */
        FULL,
        /** Past the deadline, or the event has begun. */
        CLOSED
    }

    /**
     * @param capacity   the most students the event seats; null for no limit
     * @param waitlist   whether a full event takes names for a waitlist
     * @param closesAt   when registration ends; null to keep it open until the event starts
     * @param startsAt   when the event starts
     * @param seatsTaken students registered or checked in
     * @param now        the moment the student asks
     */
    public static State state(
            Integer capacity, boolean waitlist, OffsetDateTime closesAt, OffsetDateTime startsAt,
            long seatsTaken, OffsetDateTime now) {
        if (closesAt != null && !now.isBefore(closesAt)) {
            return State.CLOSED;
        }
        if (closesAt == null && startsAt != null && !now.isBefore(startsAt)) {
            return State.CLOSED;
        }
        if (capacity == null || seatsTaken < capacity) {
            return State.OPEN;
        }
        return waitlist ? State.WAITLIST : State.FULL;
    }

    /** Seats still free; null when the event has no limit. Never below zero. */
    public static Integer left(Integer capacity, long seatsTaken) {
        return capacity == null ? null : (int) Math.max(0, capacity - seatsTaken);
    }

    /** Whether a registration in this status holds a seat. */
    public static boolean holdsSeat(String status) {
        return "REGISTERED".equals(status) || "CHECKED_IN".equals(status) || "NO_SHOW".equals(status);
    }
}
