package com.taportal.domain.engagement;

import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the timed event emails: the reminder the day before, and the note afterwards. It looks
 * every few minutes; {@link EventMessages} keeps each to once per student and event.
 */
@Component
public class EventMessageJob {

    private static final Logger log = LoggerFactory.getLogger(EventMessageJob.class);

    private final RecruitingEventRepository events;
    private final EventRegistrationRepository registrations;
    private final EventMessages messages;

    public EventMessageJob(
            RecruitingEventRepository events, EventRegistrationRepository registrations, EventMessages messages) {
        this.events = events;
        this.registrations = registrations;
        this.messages = messages;
    }

    @Scheduled(fixedDelayString = "${app.events.message-sweep-ms:300000}", initialDelayString = "${app.events.message-sweep-initial-ms:45000}")
    @Transactional
    public void sweep() {
        try {
            run(OffsetDateTime.now());
        } catch (RuntimeException e) {
            log.warn("Event message sweep failed: {}", e.toString());
        }
    }

    /** One look. A seated student is reminded; afterwards those checked in are thanked and those marked no-show are told they were missed. */
    void run(OffsetDateTime now) {
        // Only events near now can have anything due: a day ahead for reminders, a few days back for notes.
        for (RecruitingEvent e : events.findByStartsAtBetween(now.minusDays(5), now.plusDays(2))) {
            boolean remind = EventMessages.reminderDue(e.getStartsAt(), now);
            boolean note = EventMessages.noteDue(e.getStartsAt(), e.getEndsAt(), now);
            if (!remind && !note) {
                continue;
            }
            for (EventRegistration r : registrations.findByEventIdOrderByCreatedAt(e.getId())) {
                if (remind && "REGISTERED".equals(r.getStatus())) {
                    messages.reminder(e, r);
                } else if (note && "CHECKED_IN".equals(r.getStatus())) {
                    messages.thanks(e, r);
                } else if (note && "NO_SHOW".equals(r.getStatus())) {
                    // Only a student a recruiter marked as a no-show. One still marked registered may
                    // well have come: an unmarked roster must not tell a whole room it was missed.
                    messages.missed(e, r);
                }
            }
        }
    }
}
