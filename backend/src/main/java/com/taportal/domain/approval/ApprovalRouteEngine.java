package com.taportal.domain.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.taportal.api.ApprovalDtos.RequiredApproval;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single authority for what a workflow means: given a workflow definition
 * and the facts of one request, which route does the request take, and is the
 * definition sound enough to be switched on.
 *
 * <p>Pure by design — no repositories, no clock, no Spring — so Test and live
 * routing share one code path and every rule here is unit-testable.
 *
 * <p>Two guarantees the previous walk did not give:
 * <ul>
 *   <li>a rule the engine cannot evaluate is never treated as true;
 *   <li>a rule whose answer has no line leaving it never falls through to the
 *       other answer.
 * </ul>
 * In both cases the walk stops and the reason is reported in
 * {@link Route#problems()} so the caller can refuse to guess. A walk that
 * stops short of an outcome without having asked anybody to approve is
 * reported the same way, so no request is ever left without an owner.
 */
public final class ApprovalRouteEngine {

    public static final String ALWAYS = "Always";

    /** Requests with less lead time than this are "short notice". */
    static final int SHORT_NOTICE_DAYS = 14;

    /**
     * Rule texts the engine can evaluate. The display text is still the key
     * (stored graphs carry it), so rewording one here or in the UI is a
     * breaking change.
     */
    static final String SHORT_NOTICE = "Short notice (under " + SHORT_NOTICE_DAYS + " days)";

    public static final Set<String> KNOWN_RULES = Set.of(
            ALWAYS,
            "In-person event",
            "Virtual event",
            SHORT_NOTICE,
            "Flagged critical");

    /** Stored graphs name their start step this; the walk has always begun here. */
    private static final String LEGACY_START = "trigger";

    /** What is known about one request when its route is decided. */
    public record Facts(String eventFormat, Double daysNotice, Boolean flaggedCritical) {
    }

    /**
     * @param problems plain-English reasons the route cannot be trusted as
     *                 drawn; when non-empty the caller must not treat the
     *                 approvals as the whole route
     */
    public record Route(
            boolean autoApproved,
            List<String> path,
            List<RequiredApproval> approvals,
            List<String> notes,
            List<String> problems) {
    }

    /** One reason a definition must not be switched on. */
    public record Issue(String code, String nodeId, String message) {
    }

    private ApprovalRouteEngine() {
    }

    /* ---------------------------------------------------------------- route */

    public static Route evaluate(JsonNode graph, JsonNode levels, boolean autoApprove, Facts facts) {
        List<String> notes = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        // Within policy = nothing about the request demands human eyes: not
        // flagged for review and not submitted on short notice.
        boolean autoApproved = autoApprove && !isFlagged(facts) && !isShortNotice(facts);

        if (!hasNodes(graph)) {
            return fromLevels(levels, facts, autoApproved, notes, problems);
        }

        // When two steps share an id the LAST definition wins: that is the one
        // the canvas draws, and the one routing has always followed.
        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        graph.path("nodes").forEach(n -> nodes.put(n.path("id").asText(), n));
        Map<String, List<JsonNode>> out = new HashMap<>();
        graph.path("edges").forEach(e -> out.computeIfAbsent(e.path("source").asText(), k -> new ArrayList<>()).add(e));

        String start = startId(nodes, out);
        List<String> path = new ArrayList<>();
        List<RequiredApproval> approvals = new ArrayList<>();
        // Auto-approve is a property of the request and the policy, not of the
        // drawing, so it is decided before the drawing is consulted.
        if (!autoApproved && start == null) {
            problems.add("This workflow has no starting point.");
            return new Route(false, path, approvals, notes, problems);
        }
        path.add(start != null ? start : LEGACY_START);

        if (autoApproved) {
            notes.add("Request falls within the auto-approve policy — chain skipped.");
            String policy = firstOfType(nodes, "policy", "policy");
            if (policy != null) {
                path.add(policy);
            }
            String end = firstOfType(nodes, "end", "end");
            path.add(end != null ? end : "end");
            return new Route(true, path, approvals, notes, problems);
        }

        String cur = start;
        Set<String> visited = new HashSet<>(List.of(start));
        while (cur != null) {
            List<JsonNode> outgoing = out.getOrDefault(cur, List.of());
            JsonNode curNode = nodes.get(cur);
            String type = curNode == null ? "" : curNode.path("type").asText();

            JsonNode next;
            if ("condition".equals(type)) {
                String rule = ruleText(curNode);
                Boolean answer = evalRule(rule, facts);
                if (answer == null) {
                    problems.add("The rule “" + rule + "” can't be checked — it isn't a rule the system knows.");
                    break;
                }
                String want = answer ? "yes" : "no";
                next = outgoing.stream()
                        .filter(e -> want.equals(e.path("sourceHandle").asText(null)))
                        .findFirst()
                        .orElse(null);
                if (next == null) {
                    problems.add("The rule “" + rule + "” has no path for "
                            + (answer ? "YES" : "NO (otherwise)") + " — the request can't continue past it.");
                    break;
                }
                if (!answer) {
                    notes.add("Condition not met — took the NO branch.");
                }
            } else {
                // Skip the policy branch when the request isn't auto-approved.
                next = outgoing.stream()
                        .filter(e -> !isPolicy(nodes.get(e.path("target").asText()), e.path("target").asText()))
                        .findFirst()
                        .orElse(null);
            }
            if (next == null) {
                break;
            }
            String target = next.path("target").asText();
            if (!visited.add(target)) {
                break; // cycle guard
            }
            path.add(target);
            JsonNode tn = nodes.get(target);
            String targetType = tn == null ? "" : tn.path("type").asText();
            if ("approval".equals(targetType)) {
                approvals.add(new RequiredApproval(
                        target,
                        tn.path("data").path("label").asText("Approval"),
                        tn.path("data").path("approverRole").asText("")));
                if (tn.path("data").path("emailAttached").asBoolean(false)) {
                    notes.add(emailNote("Step email", tn));
                }
            }
            if ("email".equals(targetType)) {
                notes.add(emailNote("Email notification", tn));
            }
            if ("exception".equals(targetType)) {
                notes.add("Routed to exception — " + tn.path("data").path("label").asText("Exception"));
            }
            cur = target;
        }
        // Stopping anywhere but an outcome, having asked nobody, is not a
        // route: a step with no line out, a loop, a line to a missing step.
        // Routes that did reach an approver are left exactly as they were.
        if (problems.isEmpty() && approvals.isEmpty() && !isOutcome(nodes, path.get(path.size() - 1))) {
            problems.add("The route stops before reaching an outcome and asks nobody to approve"
                    + " \u2014 check the lines leaving the last step.");
        }
        return new Route(false, path, approvals, notes, problems);
    }

    /** Linear fallback when the canvas has never been saved: evaluate the level chain. */
    private static Route fromLevels(
            JsonNode levels, Facts facts, boolean autoApproved, List<String> notes, List<String> problems) {
        List<String> path = new ArrayList<>(List.of("trigger"));
        List<RequiredApproval> approvals = new ArrayList<>();
        if (autoApproved) {
            notes.add("Request falls within the auto-approve policy — chain skipped.");
            path.add("end");
            return new Route(true, path, approvals, notes, problems);
        }
        int i = 0;
        if (levels != null) {
            for (JsonNode lv : levels) {
                i++;
                String rule = lv.path("condition").asText(ALWAYS);
                Boolean answer = evalRule(rule, facts);
                if (answer == null) {
                    // Cannot tell whether this sign-off applies: require it
                    // rather than skip it, and say why.
                    problems.add("Level " + i + " uses the rule “" + rule
                            + "”, which the system can't check — the level is required to be safe.");
                }
                if (answer == null || answer) {
                    String id = "lvl-" + lv.path("id").asText(String.valueOf(i));
                    path.add(id);
                    approvals.add(new RequiredApproval(id, "Level " + i, lv.path("approverRole").asText("")));
                } else {
                    notes.add("Level " + i + " skipped — condition \"" + rule + "\" not met.");
                }
            }
        }
        path.add("end");
        return new Route(false, path, approvals, notes, problems);
    }

    /* ----------------------------------------------------------- validation */

    /**
     * Everything that makes a definition unsafe to switch on. A rule must have
     * a line for every answer it can give: "Always" can only answer YES, every
     * other rule can answer either way.
     */
    public static List<Issue> validate(JsonNode graph, JsonNode levels) {
        List<Issue> issues = new ArrayList<>();
        if (!hasNodes(graph)) {
            int i = 0;
            if (levels != null) {
                for (JsonNode lv : levels) {
                    i++;
                    String rule = lv.path("condition").asText(ALWAYS);
                    if (!KNOWN_RULES.contains(rule)) {
                        issues.add(new Issue("LEVEL_RULE_UNKNOWN", lv.path("id").asText(String.valueOf(i)),
                                "Level " + i + " uses the rule “" + rule + "”, which the system can't check."));
                    }
                }
            }
            return issues;
        }

        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (JsonNode n : graph.path("nodes")) {
            String id = n.path("id").asText();
            if (nodes.put(id, n) != null && duplicates.add(id)) {
                issues.add(new Issue("DUPLICATE_STEP", id, "Two steps share the same identity, so one of them is ignored."));
            }
        }
        Map<String, List<JsonNode>> out = new HashMap<>();
        graph.path("edges").forEach(e -> out.computeIfAbsent(e.path("source").asText(), k -> new ArrayList<>()).add(e));
        if (startId(nodes, out) == null) {
            issues.add(new Issue("NO_START", null, "This workflow has no starting point."));
        }

        for (JsonNode n : nodes.values()) {
            if (!"condition".equals(n.path("type").asText())) {
                continue;
            }
            String id = n.path("id").asText();
            String rule = ruleText(n);
            String named = "The rule “" + rule + "”";
            if (!KNOWN_RULES.contains(rule)) {
                issues.add(new Issue("RULE_UNKNOWN", id, named + " isn't one the system can check."));
            }
            int yes = 0;
            int no = 0;
            int unmarked = 0;
            for (JsonNode e : out.getOrDefault(id, List.of())) {
                String handle = e.path("sourceHandle").asText(null);
                if ("yes".equals(handle)) {
                    yes++;
                } else if ("no".equals(handle)) {
                    no++;
                } else {
                    unmarked++;
                }
            }
            if (yes == 0) {
                issues.add(new Issue("RULE_NO_YES", id, named + " has no path for YES."));
            }
            if (no == 0 && !ALWAYS.equals(rule)) {
                issues.add(new Issue("RULE_NO_OTHERWISE", id, named + " has no path for NO (otherwise)."));
            }
            if (yes > 1 || no > 1) {
                issues.add(new Issue("RULE_DUPLICATE_PATH", id,
                        named + " has more than one line for " + (yes > 1 ? "YES" : "NO") + " — only one is followed."));
            }
            if (unmarked > 0) {
                issues.add(new Issue("RULE_UNMARKED_PATH", id,
                        "A line leaving the rule \u201c" + rule + "\u201d isn't marked YES or NO, so it is never followed."));
            }
        }
        return issues;
    }

    /* ---------------------------------------------------------------- rules */

    /** @return the rule's answer, or {@code null} when the engine does not know the rule */
    static Boolean evalRule(String rule, Facts facts) {
        if (rule == null || !KNOWN_RULES.contains(rule)) {
            return null;
        }
        if (SHORT_NOTICE.equals(rule)) {
            return isShortNotice(facts);
        }
        return switch (rule) {
            case ALWAYS -> true;
            case "In-person event" -> facts != null && "IN_PERSON".equalsIgnoreCase(facts.eventFormat());
            case "Virtual event" -> facts != null && "VIRTUAL".equalsIgnoreCase(facts.eventFormat());
            case "Flagged critical" -> isFlagged(facts);
            default -> null; // listed as known but given no meaning: no answer
        };
    }

    private static boolean isFlagged(Facts facts) {
        return facts != null && Boolean.TRUE.equals(facts.flaggedCritical());
    }

    private static boolean isShortNotice(Facts facts) {
        return facts != null && facts.daysNotice() != null && facts.daysNotice() < SHORT_NOTICE_DAYS;
    }

    /* -------------------------------------------------------------- helpers */

    private static boolean hasNodes(JsonNode graph) {
        return graph != null && graph.path("nodes").size() > 0;
    }

    private static String ruleText(JsonNode conditionNode) {
        return conditionNode.path("data").path("condition").asText(ALWAYS);
    }

    /**
     * The walk starts at the trigger step, found by what it is rather than what
     * it is called. Older graphs may only name it: lines leaving the legacy id
     * are still a start, as they always were.
     */
    private static String startId(Map<String, JsonNode> nodes, Map<String, List<JsonNode>> out) {
        String byType = firstOfType(nodes, "trigger", LEGACY_START);
        if (byType != null) {
            return byType;
        }
        return out.containsKey(LEGACY_START) ? LEGACY_START : null;
    }

    /** An outcome is where a route is allowed to end. */
    private static boolean isOutcome(Map<String, JsonNode> nodes, String id) {
        JsonNode n = nodes.get(id);
        String type = n == null ? "" : n.path("type").asText();
        return "end".equals(type) || "exception".equals(type) || "end".equals(id);
    }

    /** First node of {@code type}; failing that, a node carrying the legacy id. */
    private static String firstOfType(Map<String, JsonNode> nodes, String type, String legacyId) {
        for (Map.Entry<String, JsonNode> e : nodes.entrySet()) {
            if (type.equals(e.getValue().path("type").asText())) {
                return e.getKey();
            }
        }
        return nodes.containsKey(legacyId) ? legacyId : null;
    }

    private static boolean isPolicy(JsonNode node, String id) {
        return "policy".equals(id) || (node != null && "policy".equals(node.path("type").asText()));
    }

    /** Human note for an email step/badge the walk passed through. */
    private static String emailNote(String kind, JsonNode node) {
        String tmpl = node.path("data").path("emailTemplateName").asText("");
        String trig = node.path("data").path("emailTrigger").asText("");
        StringBuilder sb = new StringBuilder(kind);
        sb.append(tmpl.isBlank() ? " — no template chosen yet" : " — sends \"" + tmpl + "\"");
        if (!trig.isBlank()) {
            sb.append(" (").append(trig.toLowerCase()).append(")");
        }
        return sb.toString();
    }
}
