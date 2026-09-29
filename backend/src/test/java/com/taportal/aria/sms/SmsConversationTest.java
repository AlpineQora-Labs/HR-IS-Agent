package com.taportal.aria.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.taportal.aria.sms.SmsConversation.Heard;
import com.taportal.domain.journey.JourneyService;
import com.taportal.domain.journey.JourneyUnits.Inbound;
import com.taportal.domain.journey.JourneyUnits.Texter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Aria understands; the journey decides. These check that what is understood reaches the right decision. */
class SmsConversationTest {

    private static final String NUMBER = "+12125550109";

    private final JourneyService journey = mock(JourneyService.class);
    private final SmsConversation aria = new SmsConversation(journey);
    private final Texter texter = new Texter(NUMBER, UUID.randomUUID(), List.of(UUID.randomUUID()), null);

    @BeforeEach
    void aTextArrives() {
        when(journey.receive(eq(NUMBER), any(), any(), any())).thenReturn(new Inbound(texter, false));
    }

    @Test
    void theSendersNumberIsReadInAnyFormItComesIn() {
        aria.receive("(212) 555-0109", "STATUS", "SIMULATOR", "m-1");

        verify(journey).receive(NUMBER, "STATUS", "SIMULATOR", "m-1");
        verify(journey).status(texter);
    }

    @Test
    void aSenderThatIsNotANumberIsNotHeard() {
        assertThat(aria.receive("not a number", "STATUS", "SIMULATOR", null)).isNull();
        verifyNoInteractions(journey);
    }

    @Test
    void eachThingAskedGoesToItsOwnDecision() {
        aria.receive(NUMBER, "2", null, null);
        verify(journey).pick(texter, 2);
        aria.receive(NUMBER, "none of these work", null, null);
        verify(journey).more(texter);
        aria.receive(NUMBER, "times", null, null);
        verify(journey).times(texter);
        aria.receive(NUMBER, "I need to reschedule", null, null);
        verify(journey).reschedule(texter);
        aria.receive(NUMBER, "help", null, null);
        verify(journey).help(texter);
        aria.receive(NUMBER, "who is this?", null, null);
        verify(journey).unclear(texter);
        aria.receive(NUMBER, "STOP", null, null);
        verify(journey).optOut(texter);
        aria.receive(NUMBER, "START", null, null);
        verify(journey).optIn(texter);
    }

    @Test
    @DisplayName("a number inside a sentence books nothing")
    void aSentenceWithANumberBooksNothing() {
        aria.receive(NUMBER, "can we do 2pm instead", null, null);

        verify(journey, never()).pick(any(), anyInt());
        verify(journey).unclear(texter);
    }

    @Test
    void thanksIsHeardAndNotAnswered() {
        Heard heard = aria.receive(NUMBER, "Thanks!", null, null);

        assertThat(heard.understood()).isEqualTo("THANKS");
        verify(journey).handled(texter);
        verify(journey, never()).help(any());
        verify(journey, never()).unclear(any());
    }

    @Test
    @DisplayName("a number that said STOP is answered only when it asks for help or to come back")
    void afterStopOnlyHelpAndStartAreActedOn() {
        when(journey.optedOut(texter)).thenReturn(true);

        aria.receive(NUMBER, "STATUS", null, null);
        aria.receive(NUMBER, "2", null, null);
        aria.receive(NUMBER, "reschedule", null, null);
        verify(journey, never()).status(any());
        verify(journey, never()).pick(any(), anyInt());
        verify(journey, never()).reschedule(any());
        verify(journey).notActedOn(texter, "STATUS");
        verify(journey).notActedOn(texter, "2");
        verify(journey).notActedOn(texter, "reschedule");

        aria.receive(NUMBER, "HELP", null, null);
        verify(journey).help(texter);
        aria.receive(NUMBER, "START", null, null);
        verify(journey).optIn(texter);
    }

    @Test
    @DisplayName("a text delivered twice is acted on once")
    void aTextDeliveredTwiceIsActedOnOnce() {
        when(journey.receive(eq(NUMBER), any(), any(), eq("m-7"))).thenReturn(new Inbound(texter, true));

        Heard heard = aria.receive(NUMBER, "2", "SIMULATOR", "m-7");

        assertThat(heard.again()).isTrue();
        verify(journey, never()).pick(any(), anyInt());
        verify(journey, never()).handled(any());
    }

    @Test
    void everyTextActedOnIsMarkedHandled() {
        aria.receive(NUMBER, "STATUS", null, null);

        verify(journey).handled(texter);
    }
}
