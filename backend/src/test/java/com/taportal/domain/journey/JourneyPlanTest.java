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

    /** The steps given, drawn one after another from the start. */
    private static JsonNode graph(String nodes) {
        java.util.List<String> ids = new java.util.ArrayList<>(java.util.List.of("trigger"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\"id\":\"([^\"]+)\"").matcher(nodes);
        while (m.find()) {
            ids.add(m.group(1));
        }
        StringBuilder edges = new StringBuilder();
        for (int i = 1; i < ids.size(); i++) {
            edges.append(i > 1 ? "," : "").append("{\"id\":\"e%d\",\"source\":\"%s\",\"target\":\"%s\"}"
                    .formatted(i, ids.get(i - 1), ids.get(i)));
        }
        return drawing("{\"id\":\"trigger\",\"type\":\"trigger\",\"data\":{}}" + (nodes.isEmpty() ? "" : "," + nodes),
                edges.toString());
    }

    private static JsonNode drawing(String nodes, String edges) {
        try {
            return new ObjectMapper().readTree("{\"nodes\":[" + nodes + "],\"edges\":[" + edges + "]}");
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
    @DisplayName("a step cut out of the flow is not part of the journey: it sends nothing")
    void aStepNoLineLeadsToSendsNothing() {
        String start = "{\"id\":\"trigger\",\"type\":\"trigger\",\"data\":{}}";
        JsonNode cut = drawing(
                start + "," + text("invite", "INTERVIEW_INVITE", TEXT_TEMPLATE) + ","
                        + text("remind", "INTERVIEW_REMINDER_24H", TEXT_TEMPLATE) + ","
                        + email("remindByEmail", "INTERVIEW_REMINDER_24H", EMAIL_TEMPLATE),
                "{\"id\":\"e1\",\"source\":\"trigger\",\"target\":\"invite\"},"
                        // the reminders lead to each other, and nothing leads to them
                        + "{\"id\":\"e2\",\"source\":\"remind\",\"target\":\"remindByEmail\"}");

        Plan plan = JourneyPlan.read(cut);

        assertThat(plan.draws(JourneyPoint.INTERVIEW_INVITE, CandidateMessage.SMS)).isTrue();
        assertThat(plan.draws(JourneyPoint.INTERVIEW_REMINDER_24H, CandidateMessage.SMS)).isFalse();
        assertThat(plan.draws(JourneyPoint.INTERVIEW_REMINDER_24H, CandidateMessage.EMAIL)).isFalse();
    }

    @Test
    @DisplayName("both sides of a rule are part of the journey, and so is a step a line leads back to")
    void everySideOfARuleIsReached() {
        String start = "{\"id\":\"trigger\",\"type\":\"trigger\",\"data\":{}}";
        String rule = "{\"id\":\"rule\",\"type\":\"condition\",\"data\":{\"condition\":\"Candidate asks for status\"}}";
        JsonNode graph = drawing(
                start + "," + rule + "," + text("status", "STATUS_REPLY", TEXT_TEMPLATE) + ","
                        + text("invite", "INTERVIEW_INVITE", TEXT_TEMPLATE),
                "{\"id\":\"e1\",\"source\":\"trigger\",\"target\":\"rule\"},"
                        + "{\"id\":\"e2\",\"source\":\"rule\",\"target\":\"status\",\"sourceHandle\":\"yes\"},"
                        + "{\"id\":\"e3\",\"source\":\"rule\",\"target\":\"invite\",\"sourceHandle\":\"no\"},"
                        + "{\"id\":\"e4\",\"source\":\"status\",\"target\":\"rule\"}");

        Plan plan = JourneyPlan.read(graph);

        assertThat(plan.steps()).containsOnlyKeys("STATUS_REPLY|SMS", "INTERVIEW_INVITE|SMS");
    }

    @Test
    void aDrawingWithNoStartDrawsNothing() {
        assertThat(JourneyPlan.read(drawing(text("a", "INTERVIEW_INVITE", TEXT_TEMPLATE), "")).steps()).isEmpty();
    }

    @Test
    void aSoundDrawingGetsNoAdvice() {
        assertThat(JourneyPlan.advise(graph(
                text("a", "INTERVIEW_INVITE", TEXT_TEMPLATE) + "," + email("b", "INTERVIEW_INVITE", EMAIL_TEMPLATE))))
                .isEmpty();
    }
}
