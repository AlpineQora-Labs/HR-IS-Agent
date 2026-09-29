package com.taportal.domain.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.domain.approval.ApprovalWorkflowRecord;
import com.taportal.domain.approval.ApprovalWorkflowRepository;
import com.taportal.domain.messaging.CandidateMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reads the drawn candidate journey — the enabled workflow whose trigger is
 * "Candidate journey" — for what it says about messages: which points have a
 * Text step, which an Email step, and which template each step names.
 *
 * <p>The drawing decides wording and channels. It does not decide the order of
 * events: those come from the candidate and the hiring team.
 *
 * <p>A step counts when a line leads to it from the start. A step cut out of
 * the flow is not part of the journey and sends nothing — which is what the
 * drawing shows, and what the check of the drawing says.
 */
@Service
public class JourneyPlan {

    public static final String TRIGGER = "Candidate journey";

    private final ApprovalWorkflowRepository workflows;
    private final ObjectMapper json;

    public JourneyPlan(ApprovalWorkflowRepository workflows, ObjectMapper json) {
        this.workflows = workflows;
        this.json = json;
    }

    /**
     * @param on    a journey is drawn and switched on
     * @param steps for each point and channel drawn, the template its step names — or no
     *              value when the step names none
     */
    public record Plan(boolean on, Map<String, Optional<UUID>> steps) {

        static final Plan OFF = new Plan(false, Map.of());

        public boolean draws(JourneyPoint point, String channel) {
            return steps.containsKey(key(point, channel));
        }

        public Optional<UUID> template(JourneyPoint point, String channel) {
            return steps.getOrDefault(key(point, channel), Optional.empty());
        }

        private static String key(JourneyPoint point, String channel) {
            return point.name() + "|" + channel;
        }
    }

    /** Something about the drawing that will not do what its author expects. */
    public record Advice(String code, String nodeId, String message) {
    }

    public Plan current() {
        ApprovalWorkflowRecord wf = workflows.findFirstByTriggerTypeIgnoreCaseAndEnabledTrue(TRIGGER).orElse(null);
        if (wf == null) {
            return Plan.OFF;
        }
        return read(parse(wf.getGraphJson()));
    }

    /** Pure: the plan a drawing describes. */
    public static Plan read(JsonNode graph) {
        Map<String, Optional<UUID>> steps = new HashMap<>();
        for (Step step : stepsOf(graph)) {
            if (step.point == null || !allowed(step)) {
                continue;
            }
            // The first step drawn for a point and channel is the one that counts.
            steps.putIfAbsent(Plan.key(step.point, step.channel), Optional.ofNullable(step.templateId));
        }
        return new Plan(true, Map.copyOf(steps));
    }

    /** Pure: what about a drawing will not do what its author expects. */
    public static List<Advice> advise(JsonNode graph) {
        List<Advice> advice = new ArrayList<>();
        Set<String> met = new HashSet<>();
        for (Step step : stepsOf(graph)) {
            String what = CandidateMessage.SMS.equals(step.channel) ? "text message" : "email";
            if (step.point == null) {
                advice.add(new Advice("STEP_NOT_BOUND", step.nodeId,
                        "A " + what + " step does not say when it is sent — choose “Sent when”."));
                continue;
            }
            if (!allowed(step)) {
                advice.add(new Advice("STEP_WRONG_CHANNEL", step.nodeId,
                        "“" + step.point.label() + "” is sent by text only — this email step is never used."));
                continue;
            }
            if (!met.add(Plan.key(step.point, step.channel))) {
                advice.add(new Advice("STEP_REPEATED", step.nodeId,
                        "There is more than one " + what + " step for “" + step.point.label()
                                + "” — only the first is used."));
            }
        }
        return advice;
    }

    private record Step(String nodeId, String channel, JourneyPoint point, UUID templateId) {
    }

    private static boolean allowed(Step step) {
        return CandidateMessage.SMS.equals(step.channel) ? step.point.byText() : step.point.byEmail();
    }

    /** The message steps a line leads to from the start, in the order they are drawn. */
    private static List<Step> stepsOf(JsonNode graph) {
        List<Step> steps = new ArrayList<>();
        if (graph == null) {
            return steps;
        }
        Set<String> reached = reached(graph);
        for (JsonNode node : graph.path("nodes")) {
            String type = node.path("type").asText("");
            JsonNode data = node.path("data");
            if (!reached.contains(node.path("id").asText())) {
                continue;
            }
            if ("sms".equals(type)) {
                steps.add(new Step(node.path("id").asText(), CandidateMessage.SMS,
                        bound(data), uuid(data.path("smsTemplateId").asText(""))));
            } else if ("email".equals(type)) {
                steps.add(new Step(node.path("id").asText(), CandidateMessage.EMAIL,
                        bound(data), uuid(data.path("emailTemplateId").asText(""))));
            }
        }
        return steps;
    }

    /** Every step a line leads to from the start, the start included. */
    static Set<String> reached(JsonNode graph) {
        String start = null;
        for (JsonNode node : graph.path("nodes")) {
            if ("trigger".equals(node.path("type").asText(""))) {
                start = node.path("id").asText();
                break;
            }
        }
        Set<String> seen = new HashSet<>();
        if (start == null) {
            return seen;
        }
        Map<String, List<String>> next = new HashMap<>();
        for (JsonNode edge : graph.path("edges")) {
            next.computeIfAbsent(edge.path("source").asText(), k -> new ArrayList<>()).add(edge.path("target").asText());
        }
        List<String> todo = new ArrayList<>(List.of(start));
        seen.add(start);
        while (!todo.isEmpty()) {
            String at = todo.remove(todo.size() - 1);
            for (String to : next.getOrDefault(at, List.of())) {
                if (seen.add(to)) {
                    todo.add(to);
                }
            }
        }
        return seen;
    }

    /** A message step with a point and a template chosen, for whoever checks the template. */
    public record Chosen(String nodeId, String channel, JourneyPoint point, UUID templateId) {
    }

    /** Pure: the steps of a drawing that name a template, whether or not the template will do. */
    public static List<Chosen> chosen(JsonNode graph) {
        return stepsOf(graph).stream()
                .filter(st -> st.point != null && st.templateId != null && allowed(st))
                .map(st -> new Chosen(st.nodeId, st.channel, st.point, st.templateId))
                .toList();
    }

    /** A step is bound to a drawn point by its key. */
    private static JourneyPoint bound(JsonNode data) {
        return JourneyPoint.of(data.path("journeyPoint").asText("")).filter(JourneyPoint::drawn).orElse(null);
    }

    private static UUID uuid(String text) {
        try {
            return text == null || text.isBlank() ? null : UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private JsonNode parse(String graphJson) {
        try {
            return graphJson == null || graphJson.isBlank() ? null : json.readTree(graphJson);
        } catch (Exception e) {
            return null;
        }
    }
}
