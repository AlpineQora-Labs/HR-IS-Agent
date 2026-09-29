package com.taportal.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An interview has a time: a candidate picked one, or the hiring team set it.
 *
 * @param scheduledAt the time booked — carried so a later change to the
 *                    interview cannot alter what this booking was
 */
public record InterviewBooked(UUID interviewId, OffsetDateTime scheduledAt, BookingOrigin origin) {
}
