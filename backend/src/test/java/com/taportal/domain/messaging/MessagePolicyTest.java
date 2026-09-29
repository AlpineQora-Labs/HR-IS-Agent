package com.taportal.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taportal.domain.messaging.MessagePolicy.Decision;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessagePolicyTest {

    private static final String NUMBER = "+17045550101";
    private static final OffsetDateTime AGREED = OffsetDateTime.parse("2026-09-01T12:00:00Z");

    private final SmsOptOutRepository optOuts = mock(SmsOptOutRepository.class);
    private final MessagePolicy policy = new MessagePolicy(optOuts);

    @Test
    void aTextNobodyAskedForNeedsAgreement() {
        assertThat(policy.text(NUMBER, AGREED, false, "INTERVIEW_INVITE").send()).isTrue();

        Decision held = policy.text(NUMBER, null, false, "INTERVIEW_INVITE");
        assertThat(held.send()).isFalse();
        assertThat(held.reason()).isEqualTo("No agreement to be texted on record");
    }

    @Test
    @DisplayName("an answer to a text the candidate sent needs no agreement: they texted us")
    void anAnswerNeedsNoAgreement() {
        assertThat(policy.text(NUMBER, null, true, "STATUS_REPLY").send()).isTrue();
    }

    @Test
    void aTextNeedsANumber() {
        assertThat(policy.text(null, AGREED, true, "HELP").reason()).isEqualTo("No mobile number a text can reach");
        assertThat(policy.text(" ", AGREED, false, "HELP").send()).isFalse();
    }

    @Test
    @DisplayName("after STOP nothing is texted, agreed or not, asked for or not")
    void stopAlwaysWins() {
        when(optOuts.existsById(NUMBER)).thenReturn(true);

        for (String point : new String[] {"INTERVIEW_INVITE", "INTERVIEW_REMINDER_1H", "STATUS_REPLY", "PICK_A_NUMBER"}) {
            Decision d = policy.text(NUMBER, AGREED, true, point);
            assertThat(d.send()).as(point).isFalse();
            assertThat(d.reason()).isEqualTo("This number opted out of texts (STOP)");
        }
    }

    @Test
    void afterStopOnlyTheConfirmationHelpAndStartAreAnswered() {
        when(optOuts.existsById(NUMBER)).thenReturn(true);

        for (String point : new String[] {"OPT_OUT", "OPT_IN", "HELP"}) {
            assertThat(policy.text(NUMBER, null, true, point).send()).as(point).isTrue();
        }
    }

    @Test
    void anEmailNeedsAnAddressThatCanBeOne() {
        assertThat(policy.email("raj.patel@example.com").send()).isTrue();
        assertThat(policy.email(" raj.patel@example.co.uk ").send()).isTrue();
        assertThat(policy.email("").reason()).isEqualTo("No email address");
        assertThat(policy.email(null).reason()).isEqualTo("No email address");
        for (String bad : new String[] {"I don't have one", "raj@", "@example.com", "raj@example", "raj patel@example.com", "a@b..com"}) {
            assertThat(policy.email(bad).send()).as(bad).isFalse();
        }
    }

    @Test
    void stopIsRecordedOnceAndStartTakesItBack() {
        when(optOuts.existsById(NUMBER)).thenReturn(false);
        policy.optOut(NUMBER);
        verify(optOuts).save(any(SmsOptOut.class));

        when(optOuts.existsById(NUMBER)).thenReturn(true);
        policy.optOut(NUMBER);
        verify(optOuts).save(any(SmsOptOut.class)); // still once

        policy.optIn(NUMBER);
        verify(optOuts).deleteById(NUMBER);
    }

    @Test
    void startFromANumberThatNeverStoppedChangesNothing() {
        when(optOuts.existsById(NUMBER)).thenReturn(false);
        policy.optIn(NUMBER);
        verify(optOuts, never()).deleteById(any());
    }
}
