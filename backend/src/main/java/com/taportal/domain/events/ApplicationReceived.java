package com.taportal.domain.events;

import java.util.UUID;

/** A candidate has applied: the application now exists. */
public record ApplicationReceived(UUID applicationId) {
}
