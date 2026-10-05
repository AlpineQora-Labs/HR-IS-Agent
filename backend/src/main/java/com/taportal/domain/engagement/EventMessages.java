package com.taportal.domain.engagement;

import com.taportal.domain.messaging.CandidateMessage;
import com.taportal.domain.messaging.MessagePolicy;
import com.taportal.domain.messaging.MessageStore;
import com.taportal.domain.messaging.MessageStore.Draft;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The emails a student gets about an event: registered, waitlisted, seated from the waitlist,
 * cancelled, a reminder the day before, and a note afterwards (thanks for coming, or sorry we
 * missed you).
 *
 * <p>Each is written to the message record and sent by the same dispatcher as every other
 * candidate message, so it shows in the candidate's timeline and goes through whichever email
 * gateway is configured (simulated unless one is set up). A message is only written here; the
 * dispatcher's sweep sends it within about a minute.
 *
 * <p>The reminder and the note afterwards carry a key, so a student gets each once per event
 * however often the job looks. The wording is fixed here for now; it is not yet a template an
 * admin can edit.
 */
@Component
public class EventMessages {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' h:mm a zzz", Locale.ENGLISH);

    /** How long before an event the reminder goes, and for how long afterwards a note is still worth sending. */
    static final Duration REMIND_AHEAD = Duration.ofHours(24);
    static final Duration NOTE_AFTER = Duration.ofHours(1);
    static final Duration NOTE_UNTIL = Duration.ofDays(3);

    private final MessageStore store;
    private final MessagePolicy policy;
    private final String publicBaseUrl;
    private final String companyName;

    public EventMessages(
            MessageStore store, MessagePolicy policy,
            @Value("${app.public-base-url:http://localhost:5177}") String publicBaseUrl,
            @Value("${app.company-name:Bank of America Careers}") String companyName) {
        this.store = store;
        this.policy = policy;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.companyName = companyName;
    }

    public void registered(RecruitingEvent e, EventRegistration r) {
        write(e, r, "EVENT_REGISTERED", null, "You're registered for " + e.getName(),
                "You're registered for <strong>" + esc(e.getName()) + "</strong>.",
                details(e),
                "If you can no longer come, reply to this email so we can give your seat to someone else.");
    }

    public void waitlisted(RecruitingEvent e, EventRegistration r, Integer position) {
        write(e, r, "EVENT_WAITLISTED", null, "You're on the waitlist for " + e.getName(),
                "Every seat for <strong>" + esc(e.getName()) + "</strong> is taken, so you are on the waitlist"
                        + (position == null ? "" : " at number " + position) + ".",
                "If a seat comes free it is yours, and we will write to you straight away.",
                details(e));
    }

    public void seated(RecruitingEvent e, EventRegistration r) {
        write(e, r, "EVENT_SEATED", null, "A seat has come free: you're in for " + e.getName(),
                "Good news: a seat has come free and you are now registered for <strong>" + esc(e.getName())
                        + "</strong>.",
                details(e),
                "If you can no longer come, reply to this email so the seat can go to the next student.");
    }

    public void cancelled(RecruitingEvent e, EventRegistration r) {
        write(e, r, "EVENT_CANCELLED", null, "Your registration for " + e.getName() + " was cancelled",
                "Your registration for <strong>" + esc(e.getName()) + "</strong> has been cancelled.",
                "If that is not what you expected, reply to this email.",
                "You can see our other events at <a href=\"" + publicBaseUrl + "/careers/events\">" + publicBaseUrl
                        + "/careers/events</a>.");
    }

    /** In the last 24 hours before the event. Once per student and event. */
    public void reminder(RecruitingEvent e, EventRegistration r) {
        write(e, r, "EVENT_REMINDER", "event-reminder:" + r.getId(), "Coming up: " + e.getName(),
                "A reminder that <strong>" + esc(e.getName()) + "</strong> is coming up.",
                details(e),
                "We look forward to meeting you.");
    }

    /** After the event, to a student who came. Once per student and event. */
    public void thanks(RecruitingEvent e, EventRegistration r) {
        write(e, r, "EVENT_THANKS", "event-after:" + r.getId(), "Thank you for coming to " + e.getName(),
                "Thank you for coming to <strong>" + esc(e.getName()) + "</strong>. It was good to meet you.",
                "If a role caught your eye, you can apply at <a href=\"" + publicBaseUrl + "/careers\">" + publicBaseUrl
                        + "/careers</a>. Use this email address so we can connect your application to our conversation.",
                null);
    }

    /** After the event, to a student who was registered and did not come. Once per student and event. */
    public void missed(RecruitingEvent e, EventRegistration r) {
        write(e, r, "EVENT_MISSED", "event-after:" + r.getId(), "Sorry we missed you at " + e.getName(),
                "We are sorry we did not get to meet you at <strong>" + esc(e.getName()) + "</strong>.",
                "You can still see our open roles and other events at <a href=\"" + publicBaseUrl + "/careers/events\">"
                        + publicBaseUrl + "/careers/events</a>.",
                null);
    }

    /* ---------- when the timed ones are due ---------- */

    /** The reminder is due in the last 24 hours before the event starts, and never after. */
    static boolean reminderDue(OffsetDateTime startsAt, OffsetDateTime now) {
        return startsAt != null && now.isBefore(startsAt) && !now.isBefore(startsAt.minus(REMIND_AHEAD));
    }

    /**
     * The note afterwards is due from an hour after the event ends until three days after: late
     * enough that the roster has been marked, and not so late that it reads as a mistake. An
     * event with no end time is taken to last two hours.
     */
    static boolean noteDue(OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime now) {
        if (startsAt == null) {
            return false;
        }
        OffsetDateTime end = endsAt != null ? endsAt : startsAt.plusHours(2);
        return !now.isBefore(end.plus(NOTE_AFTER)) && now.isBefore(end.plus(NOTE_UNTIL));
    }

    /* ---------- writing ---------- */

    private void write(
            RecruitingEvent e, EventRegistration r, String point, String key, String subject, String... paragraphs) {
        if (key != null && store.known(key)) {
            return;
        }
        String first = r.getName() == null ? "" : r.getName().trim().split("\\s+")[0];
        StringBuilder html = new StringBuilder("<p>Hello " + esc(first) + ",</p>");
        for (String p : paragraphs) {
            if (p != null && !p.isBlank()) {
                html.append("<p>").append(p).append("</p>");
            }
        }
        html.append("<p>").append(esc(companyName)).append("</p>");
        Draft draft = new Draft(
                r.getCandidateId(), r.getApplicationId(), null, CandidateMessage.EMAIL, r.getEmail(), subject,
                html.toString(), point, null, "Event: " + e.getName(), key, null);
        MessagePolicy.Decision may = policy.email(r.getEmail());
        if (may.send()) {
            store.queue(draft);
        } else {
            store.notSent(draft, CandidateMessage.SUPPRESSED, may.reason());
        }
    }

    /** When and where, in the event's own timezone. */
    private static String details(RecruitingEvent e) {
        ZoneId zone;
        try {
            zone = ZoneId.of(e.getTimezone());
        } catch (Exception ex) {
            zone = ZoneId.of("America/New_York");
        }
        String when = e.getStartsAt() == null ? null : e.getStartsAt().atZoneSameInstant(zone).format(WHEN);
        String where = e.getLocation() == null || e.getLocation().isBlank() ? null : esc(e.getLocation());
        return (when == null ? "" : "<strong>When:</strong> " + when)
                + (when != null && where != null ? "<br>" : "")
                + (where == null ? "" : "<strong>Where:</strong> " + where);
    }

    private static String esc(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
