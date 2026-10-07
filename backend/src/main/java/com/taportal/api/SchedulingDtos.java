package com.taportal.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Scheduling-dashboard DTOs: what's stuck, what's today, who's loaded. */
public final class SchedulingDtos {

    private SchedulingDtos() {}

    /** An interview needing recruiter or candidate attention, enriched for display. */
    public record AttentionItem(
            UUID interviewId,
            UUID applicationId,
            String candidateName,
            String jobTitle,
            String status,
            OffsetDateTime scheduledAt,
            long waitingHours,
            String meetingLink,
            List<String> interviewers) {
    }

    /** An interviewer's booked interview load over the next 7 days. */
    public record InterviewerLoad(
            UUID userId,
            String name,
            String initials,
            String role,
            long next7Days) {
    }

    public record Overview(
            List<AttentionItem> awaitingCandidate,
            List<AttentionItem> needsOutcome,
            List<AttentionItem> noShows,
            List<AttentionItem> today,
            List<InterviewerLoad> load) {
    }

    /** Optional panel override when (re)proposing times. */
    public record ProposeRequest(List<UUID> interviewerUserIds) {}

    /** One day for a panel: OPEN, FULL, OUTSIDE or PAST; why when not open; the start of every time that can be booked. */
    public record DayOpeningsDto(java.time.LocalDate day, String state, String why, List<OffsetDateTime> times) {}

    /** A time the hiring team picked for the candidate, and the panel it is for (null keeps the panel). */
    public record BookTimeRequest(OffsetDateTime startsAt, List<UUID> interviewerUserIds) {}
}
