package com.taportal.domain.journey;

import com.taportal.domain.events.ApplicationReceived;
import com.taportal.domain.events.ApplicationStageChanged;
import com.taportal.domain.events.InterviewBooked;
import com.taportal.domain.events.InterviewCanceled;
import com.taportal.domain.events.InterviewTimesOffered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hears what happened in the hiring domain, once it has been committed, and
 * hands it to the journey. It does nothing else: it writes nothing itself and
 * calls no other service, because at this moment the transaction that
 * published the event is over and anything joined to it would be lost.
 *
 * <p>Who publishes what:
 * <pre>
 * ApplicationReceived      ApplicationService.passedScreening, .receive (when no screening follows)
 * ApplicationStageChanged  ApplicationService.update, and only when the stage really changes
 * InterviewBooked          InterviewService.selectProposedSlot, .schedule
 * InterviewTimesOffered    InterviewService.proposeByTeam, .beginByTeam, .rescheduleByTeam
 * InterviewCanceled        InterviewService.cancelByTeam (also reached when an application is closed)
 * </pre>
 */
@Component
public class JourneyListener {

    private final JourneyService journey;

    public JourneyListener(JourneyService journey) {
        this.journey = journey;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ApplicationReceived event) {
        journey.applicationReceived(event.applicationId());
    }

    /**
     * Being moved to INTERVIEW is being selected for interview. A move to ASSESSMENT, OFFER, HIRED or
     * REJECTED is noted, and the candidate is told a few minutes later if the application is still
     * there, on whatever channels the drawing has for it (see {@link StagesReached}). No other move
     * sends anything.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ApplicationStageChanged event) {
        if ("INTERVIEW".equals(event.to())) {
            journey.selectedForInterview(event.applicationId());
            return;
        }
        JourneyPoint.onReaching(event.to()).ifPresent(point -> journey.stageNoted(event.applicationId(), point, event.from()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(InterviewBooked event) {
        journey.booked(event.interviewId(), event.scheduledAt(), event.origin());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(InterviewTimesOffered event) {
        journey.timesOffered(event.interviewId(), event.why());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(InterviewCanceled event) {
        journey.canceled(event.interviewId(), event.applicationId(), event.was());
    }
}
