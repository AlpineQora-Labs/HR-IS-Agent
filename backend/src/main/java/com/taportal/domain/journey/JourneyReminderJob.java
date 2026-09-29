package com.taportal.domain.journey;

import com.taportal.domain.interview.Interview;
import com.taportal.domain.interview.InterviewRepository;
import com.taportal.domain.messaging.CandidateMessageRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * What the journey does by the clock: reminders, keeping the promise to text
 * times when there were none, and sending anything recorded and never sent.
 *
 * <p>The sweep has no transaction. Each interview is handled in a unit of its
 * own, so one that fails costs the others nothing and nothing is sent twice.
 */
@Component
public class JourneyReminderJob {

    private static final Logger log = LoggerFactory.getLogger(JourneyReminderJob.class);

    private final InterviewRepository interviews;
    private final CandidateMessageRepository messages;
    private final JourneyService journey;

    public JourneyReminderJob(
            InterviewRepository interviews, CandidateMessageRepository messages, JourneyService journey) {
        this.interviews = interviews;
        this.messages = messages;
        this.journey = journey;
    }

    @Scheduled(fixedDelayString = "${app.journey.sweep-ms:60000}", initialDelayString = "${app.journey.sweep-initial-ms:30000}")
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        try {
            for (Interview interview : interviews.findByStatusAndScheduledAtBetween("SCHEDULED", now, now.plusHours(24))) {
                due(interview, now).ifPresent(point -> journey.remind(interview.getId(), point));
            }
            List<UUID> waiting = messages.interviewsWaitingForTimes(now.minusDays(14));
            for (UUID interviewId : waiting) {
                journey.offerWhenTimesOpen(interviewId);
            }
        } catch (RuntimeException e) {
            log.warn("Journey sweep failed: {}", e.toString());
        }
        journey.sweep();
    }

    /**
     * Which reminder is due for a booked interview, if any.
     *
     * <ul>
     *   <li>The 24-hour reminder: from 24 hours before until 12 hours before —
     *       so a server that was down at the hour still reminds — and only
     *       when the interview was booked more than 24 hours ahead. Booked
     *       later than that, the confirmation is the reminder.
     *   <li>The 1-hour reminder: in the last hour before the interview.
     * </ul>
     */
    static Optional<JourneyPoint> due(Interview interview, OffsetDateTime now) {
        OffsetDateTime at = interview.getScheduledAt();
        if (at == null || !at.isAfter(now)) {
            return Optional.empty();
        }
        if (!now.isBefore(at.minusHours(1))) {
            return Optional.of(JourneyPoint.INTERVIEW_REMINDER_1H);
        }
        OffsetDateTime bookedAt = interview.getBookedAt() != null ? interview.getBookedAt() : interview.getCreatedAt();
        boolean bookedWellAhead = bookedAt != null && !bookedAt.isAfter(at.minusHours(24));
        if (bookedWellAhead && !now.isBefore(at.minusHours(24)) && now.isBefore(at.minusHours(12))) {
            return Optional.of(JourneyPoint.INTERVIEW_REMINDER_24H);
        }
        return Optional.empty();
    }
}
