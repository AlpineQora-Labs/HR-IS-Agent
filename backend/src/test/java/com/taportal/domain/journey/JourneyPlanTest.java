package com.taportal.domain.journey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.domain.journey.JourneyPlan.Advice;
import com.taportal.domain.journey.JourneyPlan.Plan;
import com.taportal.domain.messaging.CandidateMessage;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JourneyPlanTest {

    private static final String TEXT_TEMPLATE = "71000000-0000-0000-0000-000000000003";
    private static final String EMAIL_TEMPLATE = "98100000-0000-0000-0000-000000000002";

    private static JsonNode graph(String nodes) {
        try {
            return new ObjectMapper().readTree("{\"nodes\":[" + nodes + "],\"edges\":[]}");
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String text(String id, String point, String template) {
        return "{\"id\":\"%s\",\"type\":\"sms\",\"data\":{\"journeyPoint\":\"%s\",\"smsTemplateId\":\"%s\"}}"
                .formatted(id, point, template);
    }

    private static String email(String id, String point, String template) {
        return "{\"id\":\"%s\",\"type\":\"email\",\"data\":{\"journeyPoint\":\"%s\",\"emailTemplateId\":\"%s\"}}"
                .formatted(id, point, template);
    }

    @Test
    void theDrawingSaysWhichChannelsAPointUsesAndWithWhichWording() {
        Plan plan = JourneyPlan.read(graph(
                text("a", "INTERVIEW_INVITE", TEXT_TEMPLATE) + "," + email("b", "INTERVIEW_INVITE", EMAIL_TEMPLATE)));

        assertThat(plan.on()).isTrue();
        assertThat(plan.draws(JourneyPoint.INTERVIEW_INVITE, CandidateMessage.SMS)).isTrue();
        assertThat(plan.template(JourneyPoint.INTERVIEW_INVITE, CandidateMessage.SMS)).contains(UUID.fromString(TEXT_TEMPLATE));
        assertThat(plan.template(JourneyPoint.INTERVIEW_INVITE, CandidateMessage.EMAIL)).contains(UUID.fromString(EMAIL_TEMPLATE));
    }

    @Test
    @DisplayName("text only: take the email step off the drawing")
    void aChannelWithNoStepIsNotUsed() {
        Plan plan = JourneyPlan.read(graph(text("a", "APPLICATION_RECEIVED", TEXT_TEMPLATE)));

        assertThat(plan.draws(JourneyPoint.APPLICATION_RECEIVED, CandidateMessage.SMS)).isTrue();
        assertThat(plan.draws(JourneyPoint.APPLICATION_RECEIVED, CandidateMessage.EMAIL)).isFalse();
        assertThat(plan.draws(JourneyPoint.INTERVIEW_INVITE, CandidateMessage.SMS)).isFalse();
    }

    @Test
    void aStepWithNoTemplateChosenIsStillAStep() {
        Plan plan = JourneyPlan.read(graph(text("a", "INTERVIEW_CONFIRMED", "")));

        assertThat(plan.draws(JourneyPoint.INTERVIEW_CONFIRMED, CandidateMessage.SMS)).isTrue();
        assertThat(plan.template(JourneyPoint.INTERVIEW_CONFIRMED, CandidateMessage.SMS)).isEmpty();
    }

    @Test
    void theFirstStepDrawnForAPointIsTheOneThatCounts() {
        Plan plan = JourneyPlan.read(graph(
                text("a", "INTERVIEW_INVITE", TEXT_TEMPLATE) + ","
                        + text("b", "INTERVIEW_INVITE", "71000000-0000-0000-0000-000000000099")));

        assertThat(plan.template(JourneyPoint.INTERVIEW_INVITE, CandidateMessage.SMS)).contains(UUID.fromString(TEXT_TEMPLATE));
    }

    @Test
    void whatDoesNotBelongOnTheDrawingIsIgnored() {
        Plan plan = JourneyPlan.read(graph(
                text("a", "", TEXT_TEMPLATE) + ","                        // not bound to a point
                        + text("b", "HELP", TEXT_TEMPLATE) + ","          // a point that is not drawn
                        + text("c", "NOT_A_POINT", TEXT_TEMPLATE) + ","
                        + email("d", "INTERVIEW_REMINDER_1H", EMAIL_TEMPLATE) + "," // sent by text only
                        + text("e", "STATUS_REPLY", "not-an-id")));

        assertThat(plan.steps()).containsOnlyKeys("STATUS_REPLY|SMS");
        assertThat(plan.template(JourneyPoint.STATUS_REPLY, CandidateMessage.SMS)).isEmpty();
    }

    @Test
    void anEmptyOrMissingDrawingDrawsNothing() {
        assertThat(JourneyPlan.read(null).steps()).isEmpty();
        assertThat(JourneyPlan.read(graph("")).steps()).isEmpty();
    }

    @Test
    @DisplayName("what a drawing will not do as its author expects is said, step by step")
    void adviceNamesTheStep() {
        assertThat(JourneyPlan.advise(graph(
                text("a", "", TEXT_TEMPLATE) + ","
                        + email("b", "INTERVIEW_REMINDER_1H", EMAIL_TEMPLATE) + ","
                        + text("c", "INTERVIEW_INVITE", TEXT_TEMPLATE) + ","
                        + text("d", "INTERVIEW_INVITE", TEXT_TEMPLATE) + ","
                        + email("e", "INTERVIEW_INVITE", EMAIL_TEMPLATE))))
                .extracting(Advice::code, Advice::nodeId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("STEP_NOT_BOUND", "a"),
                        org.assertj.core.groups.Tuple.tuple("STEP_WRONG_CHANNEL", "b"),
                        org.assertj.core.groups.Tuple.tuple("STEP_REPEATED", "d"));
    }

    @Test
    void aSoundDrawingGetsNoAdvice() {
        assertThat(JourneyPlan.advise(graph(
                text("a", "INTERVIEW_INVITE", TEXT_TEMPLATE) + "," + email("b", "INTERVIEW_INVITE", EMAIL_TEMPLATE))))
                .isEmpty();
    }
}
