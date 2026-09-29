package com.taportal.domain.journey;

import com.taportal.domain.events.BookingOrigin;
import com.taportal.domain.events.InterviewTimesOffered;
import com.taportal.domain.interview.InterviewService;
import com.taportal.domain.interview.InterviewService.RescheduleAttempt;
import com.taportal.domain.journey.JourneyUnits.Inbound;
import com.taportal.domain.journey.JourneyUnits.Pick;
import com.taportal.domain.journey.JourneyUnits.Reschedule;
import com.taportal.domain.journey.JourneyUnits.Texter;
import com.taportal.domain.messaging.CandidateMessage;
import com.taportal.domain.messaging.MessageDispatcher;
import com.taportal.domain.messaging.MessagePolicy;
import com.taportal.domain.notification.NotificationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * The candidate journey, as the rest of the system sees it. Each method runs
 * one or more units of work ({@link JourneyUnits}), each in a transaction of
 * its own, and sends what they recorded once they have committed.
 *
 * <p>It has no transaction of its own, on purpose. It is called after other
 * transactions have committed, and it calls scheduling methods that refuse by
 * throwing: inside one surrounding transaction the first would write nothing
 * and the second would spoil everything written before it.
 *
 * <p>Nothing here throws to its caller. A message that could not be prepared
 * must never undo, or seem to undo, what it was about; the recruiters are
 * told instead.
 */
@Service
public class JourneyService {

    private static final Logger log = LoggerFactory.getLogger(JourneyService.class);

    private final JourneyUnits units;
    private final MessageDispatcher dispatcher;
    private final InterviewService interviewService;
    private final MessagePolicy policy;
    private final NotificationService notifications;
    private final TransactionTemplate alone;

    public JourneyService(
            JourneyUnits units, MessageDispatcher dispatcher, InterviewService interviewService,
            MessagePolicy policy, NotificationService notifications, PlatformTransactionManager transactions) {
        this.units = units;
        this.dispatcher = dispatcher;
        this.interviewService = interviewService;
        this.policy = policy;
        this.notifications = notifications;
        this.alone = new TransactionTemplate(transactions);
        this.alone.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---- what happens ----

    public List<CandidateMessage> applicationReceived(UUID applicationId) {
        return run("telling a candidate their application was received", () -> units.applicationReceived(applicationId));
    }

    public List<CandidateMessage> selectedForInterview(UUID applicationId) {
        return run("inviting a candidate to interview", () -> units.selectedForInterview(applicationId));
    }

    public List<CandidateMessage> timesOffered(UUID interviewId, InterviewTimesOffered.Why why) {
        return run("sending a candidate the times on offer", () -> units.timesOffered(interviewId, why));
    }

    public List<CandidateMessage> booked(UUID interviewId, OffsetDateTime at, BookingOrigin origin) {
        return run("confirming an interview", () -> units.booked(interviewId, at, origin));
    }

    public List<CandidateMessage> canceled(UUID interviewId, UUID applicationId, OffsetDateTime was) {
        return run("telling a candidate their interview was cancelled", () -> units.canceled(interviewId, applicationId, was));
    }

    public List<CandidateMessage> remind(UUID interviewId, JourneyPoint which) {
        return run("reminding a candidate of an interview", () -> units.remind(interviewId, which));
    }

    public List<CandidateMessage> offerWhenTimesOpen(UUID interviewId) {
        return run("offering times that have opened up", () -> units.offerWhenTimesOpen(interviewId));
    }

    /** What is on record for a reminder of an interview as it is booked now. Empty when nothing is. */
    public List<CandidateMessage> reminded(UUID interviewId, JourneyPoint which) {
        try {
            return units.reminded(interviewId, which);
        } catch (RuntimeException e) {
            log.warn("Could not read the record of a reminder: {}", e.toString());
            return List.of();
        }
    }

    // ---- what a candidate texts ----

    public Inbound receive(String address, String text, String provider, String providerMessageId) {
        return units.inbound(address, text, provider, providerMessageId);
    }

    public void handled(Texter t) {
        try {
            units.handled(t.inboundId());
        } catch (RuntimeException e) {
            log.warn("Could not mark text {} as handled: {}", t.inboundId(), e.toString());
        }
    }

    /** True when this number said STOP and has not said START since. */
    public boolean optedOut(Texter t) {
        return policy.optedOut(t.address());
    }

    public List<CandidateMessage> optOut(Texter t) {
        return run("confirming an opt-out", () -> units.optOut(t));
    }

    public List<CandidateMessage> optIn(Texter t) {
        return run("confirming an opt-in", () -> units.optIn(t));
    }

    public List<CandidateMessage> notActedOn(Texter t, String what) {
        return run("recording a text from a number that opted out", () -> units.notActedOn(t, what));
    }

    public List<CandidateMessage> help(Texter t) {
        return run("answering a request for help", () -> units.help(t));
    }

    public List<CandidateMessage> unclear(Texter t) {
        return run("answering a text that was not understood", () -> units.unclear(t));
    }

    public List<CandidateMessage> status(Texter t) {
        return run("answering a request for status", () -> units.status(t));
    }

    public List<CandidateMessage> more(Texter t) {
        return run("offering other times", () -> units.more(t));
    }

    public List<CandidateMessage> times(Texter t) {
        return run("showing the times on offer", () -> units.times(t));
    }

    /**
     * A number was texted. When it names a time that can be booked, the offer
     * is taken first — so that of two replies arriving together only one
     * books — and then the time is booked. The confirmation is sent by the
     * booking itself (see JourneyListener), not from here.
     */
    public List<CandidateMessage> pick(Texter t, int n) {
        Pick pick;
        try {
            pick = units.pick(t, n);
        } catch (RuntimeException e) {
            failed("reading a reply that picks a time", e);
            return List.of();
        }
        switch (pick.next()) {
            case ANSWERED:
                dispatcher.dispatch(pick.written());
                return pick.written();
            case GONE:
                return run("answering that a time is gone", () -> units.gone(t, pick.interviewId()));
            default:
                break;
        }
        boolean mine;
        try {
            mine = units.claim(pick.offerId());
        } catch (RuntimeException e) {
            failed("taking an offer", e);
            return List.of();
        }
        if (!mine) {
            // Another reply got there first. Whatever it led to is what is true now.
            return run("answering a second reply", () -> units.gone(t, pick.interviewId()));
        }
        try {
            interviewService.selectProposedSlot(pick.slotId(), BookingOrigin.TEXT);
            return List.of();
        } catch (ResponseStatusException | DataAccessException e) {
            // Taken between the reading and the booking, or refused by the database's own guard.
            return run("answering that a time is gone", () -> units.gone(t, pick.interviewId()));
        } catch (RuntimeException e) {
            failed("booking a time picked by text", e);
            return List.of();
        }
    }

    /**
     * RESCHEDULE. What is to be moved is found first; then the scheduling
     * service is asked, which refuses without changing anything when the
     * policy says no or there is nothing to move to; then the candidate is
     * told what came of it.
     */
    public List<CandidateMessage> reschedule(Texter t) {
        Reschedule plan;
        try {
            plan = units.reschedule(t);
        } catch (RuntimeException e) {
            failed("reading a request to reschedule", e);
            return List.of();
        }
        if (plan.interviewId() == null) {
            if (plan.asMore()) {
                return more(t);
            }
            dispatcher.dispatch(plan.written());
            return plan.written();
        }
        RescheduleAttempt attempt;
        try {
            attempt = interviewService.tryCandidateReschedule(plan.interviewId());
        } catch (RuntimeException e) {
            failed("moving an interview", e);
            return List.of();
        }
        return run("answering a request to reschedule", () -> units.rescheduled(t, plan.interviewId(), attempt));
    }

    /**
     * A text that may have been asking to move an interview, in words that
     * could mean something else. A booking is never given up on the strength
     * of it: the candidate is told what they are booked for and how to ask.
     * With nothing booked, asking again costs nothing, and it is taken as asked.
     */
    public List<CandidateMessage> mayWantToReschedule(Texter t) {
        Reschedule plan;
        try {
            plan = units.reschedule(t);
        } catch (RuntimeException e) {
            failed("reading a text that may ask to reschedule", e);
            return List.of();
        }
        if (plan.interviewId() != null) {
            return run("answering a text that may ask to reschedule",
                    () -> units.bookedAsItIs(t, plan.interviewId()));
        }
        if (plan.asMore()) {
            return more(t);
        }
        dispatcher.dispatch(plan.written());
        return plan.written();
    }

    /** Send what was written and never sent. */
    public void sweep() {
        try {
            dispatcher.sweep();
        } catch (RuntimeException e) {
            log.warn("Message sweep failed: {}", e.toString());
        }
    }

    private List<CandidateMessage> run(String what, Supplier<List<CandidateMessage>> unit) {
        List<CandidateMessage> written;
        try {
            written = unit.get();
        } catch (RuntimeException e) {
            failed(what, e);
            return List.of();
        }
        try {
            dispatcher.dispatch(written);
        } catch (RuntimeException e) {
            // Recorded and not sent: the sweep will send it.
            log.warn("Could not send after {}: {}", what, e.toString());
        }
        return written;
    }

    /** Nobody is watching this thread. Say what went wrong where someone is. */
    private void failed(String what, RuntimeException e) {
        log.error("The candidate journey failed while {}", what, e);
        try {
            alone.executeWithoutResult(tx -> notifications.notifyRole("ADMIN", "MESSAGE",
                    "A candidate message could not be prepared",
                    "It failed while " + what + ". Nothing was sent. The server log has the details.",
                    "/admin?tab=workflow"));
        } catch (RuntimeException alert) {
            log.error("…and the alert about it could not be written", alert);
        }
    }
}
