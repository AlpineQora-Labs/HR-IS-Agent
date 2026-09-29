package com.taportal.domain.events;

import java.util.UUID;

/**
 * The hiring team put times on offer for an interview. The times are the
 * interview's proposed slots at the moment of the event; a listener uses
 * those and never proposes again.
 */
public record InterviewTimesOffered(UUID interviewId, Why why) {

    public enum Why {
        /** The team asked for times to be offered. */
        PROPOSED,
        /** The team released a booked time and offered others. */
        MOVED
    }
}
