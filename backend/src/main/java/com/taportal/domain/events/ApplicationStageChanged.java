package com.taportal.domain.events;

import java.util.UUID;

/** The hiring team moved an application from one stage to another. */
public record ApplicationStageChanged(UUID applicationId, String from, String to) {
}
