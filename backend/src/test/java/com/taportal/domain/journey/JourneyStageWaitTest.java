package com.taportal.domain.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.taportal.domain.interview.InterviewService;
import com.taportal.domain.messaging.MessageDispatcher;
import com.taportal.domain.messaging.MessagePolicy;
import com.taportal.domain.notification.NotificationService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A move to a stage the candidate is told of waits a few minutes before it is told, so a stage the
 * pipeline only passed through is never announced (StagesReached).
 */
class JourneyStageWaitTest {

    private final JourneyUnits units = mock(JourneyUnits.class);
    private final StagesReached stages = mock(StagesReached.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final JourneyService journey = new JourneyService(units, mock(MessageDispatcher.class),
            mock(InterviewService.class), mock(MessagePolicy.class), notifications,
            mock(PlatformTransactionManager.class), stages, 2);

    @Test
    void aMoveIsNotedNotTold() {
        UUID app = UUID.randomUUID();

        journey.stageNoted(app, JourneyPoint.NOT_SELECTED, "INTERVIEW");

        verify(stages).note(app, JourneyPoint.NOT_SELECTED, "INTERVIEW");
        verifyNoInteractions(units);
    }

    @Test
    void aStageWhoseWaitIsOverIsToldOnceAndCleared() {
        UUID hired = UUID.randomUUID();
        UUID retired = UUID.randomUUID();
        when(stages.reachedBefore(any())).thenReturn(List.of(
                new StagesReached.Reached(hired, "HIRED", "OFFER"),
                new StagesReached.Reached(retired, "A_POINT_NO_LONGER_ON_THE_JOURNEY", null)));
        when(units.stageReached(hired, JourneyPoint.HIRED, "OFFER")).thenReturn(List.of());
        OffsetDateTime before = OffsetDateTime.now();

        journey.tellStagesReached();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(stages).reachedBefore(cutoff.capture());
        assertThat(Duration.between(cutoff.getValue(), before).toSeconds()).as("two minutes of grace").isBetween(110L, 130L);
        verify(units).stageReached(hired, JourneyPoint.HIRED, "OFFER");
        verify(stages).done(hired, "HIRED");
        verify(stages).done(retired, "A_POINT_NO_LONGER_ON_THE_JOURNEY");
    }

    @Test
    void aStageThatFailsToBeToldIsClearedAndSomeoneIsTold() {
        UUID app = UUID.randomUUID();
        when(stages.reachedBefore(any())).thenReturn(List.of(new StagesReached.Reached(app, "ASSESSMENT_INVITE", "INTERVIEW")));
        when(units.stageReached(app, JourneyPoint.ASSESSMENT_INVITE, "INTERVIEW")).thenThrow(new IllegalStateException("boom"));

        journey.tellStagesReached();

        verify(stages).done(app, "ASSESSMENT_INVITE");
        verify(notifications).notifyRole(any(), any(), any(), any(), any());
    }
}
