package com.taportal.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The hiring team's side cancelled an interview the candidate was expecting.
 *
 * @param was the time it had been booked for
 */
public record InterviewCanceled(UUID interviewId, UUID applicationId, OffsetDateTime was) {
}
