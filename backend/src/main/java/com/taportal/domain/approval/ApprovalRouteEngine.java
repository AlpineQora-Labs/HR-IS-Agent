package com.taportal.domain.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.taportal.api.ApprovalDtos.RequiredApproval;
import java.util.ArrayList;
import java.util.Collections;
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
     * @param lines    the line followed into each step of {@code path} after
     *                 the first ("" when no line is drawn for it). Two lines
     *                 can join the same two steps — a rule's YES and NO — so
     *                 the path alone does not say which way a request went
     */
    public record Route(
            boolean autoApproved,
            List<String> path,
            List<RequiredApproval> approvals,
            List<String> notes,
            List<String> problems,
            List<String> lines) {
    }

    /** BLOCKING issues stop a workflow being switched on; ADVICE is what is left to finish. */
    public enum Severity { BLOCKING, ADVICE }

    /** One thing about a definition that is wrong or unfinished. */
    public record Issue(String code, Severity severity, String nodeId, String message) {

        static Issue blocking(String code, String nodeId, String message) {
            return new Issue(code, Severity.BLOCKING, nodeId, message);
        }

        static Issue advice(String code, String nodeId, String message) {
            return new Issue(code, Severity.ADVICE, nodeId, message);
        }

        public boolean blocks() {
            return severity == Severity.BLOCKING;
        }
    }

    static final String NOTHING_TO_CHECK = "A rule has nothing to check yet — choose what it checks.";

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
        List<String> lines = new ArrayList<>();
        List<RequiredApproval> approvals = new ArrayList<>();
        // Auto-approve is a property of the request and the policy, not of the
        // drawing, so it is decided before the drawing is consulted.
        if (!autoApproved && start == null) {
            problems.add("This workflow has no starting point.");
            return new Route(false, path, approvals, notes, problems, lines);
        }
        path.add(start != null ? start : LEGACY_START);

        if (autoApproved) {
            notes.add("Request falls within the auto-approve policy — chain skipped.");
            String policy = firstOfType(nodes, "policy", "policy");
            if (policy != null) {
                lines.add(lineBetween(out, path.get(path.size() - 1), policy));
                path.add(policy);
            }
            String end = firstOfType(nodes, "end", "end");
            lines.add(lineBetween(out, path.get(path.size() - 1), end != null ? end : "end"));
            path.add(end != null ? end : "end");
            return new Route(true, path, approvals, notes, problems, lines);
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
                    problems.add(rule.isBlank()
                            ? NOTHING_TO_CHECK
                            : "The rule “" + rule + "” can't be checked — it isn't a rule the system knows.");
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
            lines.add(next.path("id").asText(""));
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
        return new Route(false, path, approvals, notes, problems, lines);
    }

    /** Linear fallback when the canvas has never been saved: evaluate the level chain. */
    private static Route fromLevels(
            JsonNode levels, Facts facts, boolean autoApproved, List<String> notes, List<String> problems) {
        List<String> path = new ArrayList<>(List.of("trigger"));
        List<RequiredApproval> approvals = new ArrayList<>();
        if (autoApproved) {
            notes.add("Request falls within the auto-approve policy — chain skipped.");
            path.add("end");
            return new Route(true, path, approvals, notes, problems, noLines(path));
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
                    problems.add((rule.isBlank()
                            ? "Level " + i + " has a rule with nothing to check"
                            : "Level " + i + " uses the rule “" + rule + "”, which the system can't check")
                            + " — the level is required to be safe.");
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
        return new Route(false, path, approvals, notes, problems, noLines(path));
    }

    /** A chain of levels has no lines drawn: one blank entry for each step after the first. */
    private static List<String> noLines(List<String> path) {
        return Collections.nCopies(Math.max(0, path.size() - 1), "");
    }

    /* ----------------------------------------------------------- validation */

    /**
     * Everything wrong or unfinished about a definition. BLOCKING issues make it
     * unsafe to switch on: a rule must have a line for every answer it can give
     * ("Always" can only answer YES, every other rule can answer either way),
     * and an approval must name who approves. ADVICE is the rest of the to-do
     * list: steps nothing leads to, steps nothing follows, messages with no
     * template chosen.
     *
     * <p>Lines are followed the way routing follows them: a step leads to the
     * first line drawn out of it, a rule to the line of each answer it can
     * give. So a step that is drawn and connected but that no request can come
     * to is reported — and what is wrong with such a step holds nothing back,
     * because it can't affect where a request goes.
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
                        issues.add(Issue.blocking("LEVEL_RULE_UNKNOWN", lv.path("id").asText(String.valueOf(i)),
                                rule.isBlank()
                                        ? "Level " + i + " has a rule with nothing to check."
                                        : "Level " + i + " uses the rule “" + rule + "”, which the system can't check."));
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
                issues.add(Issue.blocking("DUPLICATE_STEP", id, "Two steps share the same identity, so one of them is ignored."));
            }
        }
        Map<String, List<JsonNode>> out = new HashMap<>();
        graph.path("edges").forEach(e -> out.computeIfAbsent(e.path("source").asText(), k -> new ArrayList<>()).add(e));
        String start = startId(nodes, out);
        if (start == null) {
            issues.add(Issue.blocking("NO_START", null, "This workflow has no starting point."));
        }
        Set<String> connected = start == null ? Set.of() : reachable(start, out);
        Walk walk = start == null ? Walk.NOWHERE : Walk.from(start, nodes, out);

        for (JsonNode n : nodes.values()) {
            String id = n.path("id").asText();
            String type = n.path("type").asText();
            String named = named(n);
            // With no start nothing is walked at all; that is reported on its own.
            boolean met = start == null || walk.steps().contains(id);

            List<Issue> own = new ArrayList<>();
            if ("condition".equals(type)) {
                validateRule(n, out.getOrDefault(id, List.of()), own);
            } else if ("approval".equals(type)) {
                if (text(n, "approverRole").isBlank()) {
                    own.add(Issue.blocking("APPROVER_NO_ROLE", id,
                            capital(named) + " has nobody to approve it — choose a role."));
                }
                if (n.path("data").path("emailAttached").asBoolean(false) && text(n, "emailTemplateId").isBlank()) {
                    own.add(Issue.advice("MESSAGE_NO_TEMPLATE", id,
                            "The email sent with " + named + " has no message chosen yet."));
                }
            } else if (("email".equals(type) && text(n, "emailTemplateId").isBlank())
                    || ("sms".equals(type) && text(n, "smsTemplateId").isBlank())) {
                own.add(Issue.advice("MESSAGE_NO_TEMPLATE", id, capital(named) + " has no message chosen yet."));
            }
            for (Issue issue : own) {
                issues.add(met || !issue.blocks() ? issue : Issue.advice(issue.code(), issue.nodeId(), issue.message()));
            }

            boolean isStart = id.equals(start);
            // The outcome belongs to every workflow and can't be taken out, so
            // a workflow that always stops for review is not told to fix it.
            // Paths that end nowhere are reported where they end: an open end,
            // a line that leads back, a line to a step that is gone.
            boolean isEnd = "end".equals(type) || "end".equals(id);
            if (!isStart && !isEnd && !isPolicy(n, id) && start != null) {
                if (!connected.contains(id)) {
                    issues.add(Issue.advice("UNREACHABLE", id,
                            capital(named) + " isn't connected to the flow, so it is never reached."));
                } else if (!met) {
                    issues.add(Issue.advice("NOT_FOLLOWED", id,
                            capital(named) + " is never reached: a request always takes another line before it."));
                }
            }
            boolean leadsOn = out.getOrDefault(id, List.of()).stream()
                    .anyMatch(e -> !isPolicy(nodes.get(e.path("target").asText()), e.path("target").asText()));
            if (!"condition".equals(type) && !isOutcome(nodes, id) && !isPolicy(n, id) && !leadsOn
                    && (isStart || walk.steps().contains(id))) {
                issues.add(Issue.advice("OPEN_END", id,
                        "Nothing happens after " + named + " — add the next step or an outcome."));
            }
        }
        for (String[] line : walk.nowhere()) {
            issues.add(Issue.advice("LINE_TO_NOWHERE", line[0],
                    "A line leaving " + nameOf(nodes, line[0]) + " leads to a step that no longer exists."));
        }
        for (String[] line : walk.loops()) {
            issues.add(Issue.advice(LOOP_BACK, line[0],
                    capital(nameOf(nodes, line[0])) + " leads back to " + nameOf(nodes, line[1])
                            + ", so a request that comes this way never reaches an outcome."));
        }
        return issues;
    }

    /** A request is routed once; a line that leads back is where it would stop. */
    public static final String LOOP_BACK = "LOOP_BACK";

    /**
     * The steps a request can come to, following lines the way routing does,
     * and the lines on the way that lead back or lead nowhere.
     */
    private record Walk(Set<String> steps, List<String[]> loops, List<String[]> nowhere) {

        static final Walk NOWHERE = new Walk(Set.of(), List.of(), List.of());

        static Walk from(String start, Map<String, JsonNode> nodes, Map<String, List<JsonNode>> out) {
            Walk walk = new Walk(new HashSet<>(), new ArrayList<>(), new ArrayList<>());
            walk.visit(start, new HashSet<>(), nodes, out);
            return walk;
        }

        private void visit(String id, Set<String> onTheWay, Map<String, JsonNode> nodes, Map<String, List<JsonNode>> out) {
            steps.add(id);
            onTheWay.add(id);
            for (JsonNode line : followed(id, nodes, out)) {
                String target = line.path("target").asText();
                if (!nodes.containsKey(target)) {
                    nowhere.add(new String[] {id, target});
                } else if (onTheWay.contains(target)) {
                    loops.add(new String[] {id, target});
                } else if (!steps.contains(target)) {
                    visit(target, onTheWay, nodes, out);
                }
            }
            onTheWay.remove(id);
        }

        /** The lines a request can leave a step by: the same choice {@link #evaluate} makes. */
        private static List<JsonNode> followed(String id, Map<String, JsonNode> nodes, Map<String, List<JsonNode>> out) {
            JsonNode node = nodes.get(id);
            String type = node == null ? "" : node.path("type").asText();
            List<JsonNode> outgoing = out.getOrDefault(id, List.of());
            if (isPolicy(node, id) || isOutcome(nodes, id)) {
                return List.of();
            }
            List<JsonNode> lines = new ArrayList<>();
            if ("condition".equals(type)) {
                // "Always" only ever answers YES; any other rule — even one not
                // chosen yet — may go either way once it can be checked.
                for (String answer : ALWAYS.equals(ruleText(node)) ? List.of("yes") : List.of("yes", "no")) {
                    outgoing.stream()
                            .filter(e -> answer.equals(e.path("sourceHandle").asText(null)))
                            .findFirst()
                            .ifPresent(lines::add);
                }
                return lines;
            }
            outgoing.stream()
                    .filter(e -> !isPolicy(nodes.get(e.path("target").asText()), e.path("target").asText()))
                    .findFirst()
                    .ifPresent(lines::add);
            return lines;
        }
    }

    private static String nameOf(Map<String, JsonNode> nodes, String id) {
        JsonNode n = nodes.get(id);
        return n == null ? "a step" : named(n);
    }

    private static void validateRule(JsonNode n, List<JsonNode> outgoing, List<Issue> issues) {
        String id = n.path("id").asText();
        String rule = ruleText(n);
        String named = named(n);
        String Named = capital(named);
        if (rule.isBlank()) {
            issues.add(Issue.blocking("RULE_EMPTY", id, NOTHING_TO_CHECK));
        } else if (!KNOWN_RULES.contains(rule)) {
            issues.add(Issue.blocking("RULE_UNKNOWN", id, Named + " isn't one the system can check."));
        }
        int yes = 0;
        int no = 0;
        int unmarked = 0;
        for (JsonNode e : outgoing) {
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
            issues.add(Issue.blocking("RULE_NO_YES", id, Named + " has no path for YES."));
        }
        if (no == 0 && !ALWAYS.equals(rule)) {
            issues.add(Issue.blocking("RULE_NO_OTHERWISE", id, Named + " has no path for NO (otherwise)."));
        }
        if (yes > 1 || no > 1) {
            issues.add(Issue.blocking("RULE_DUPLICATE_PATH", id,
                    Named + " has more than one line for " + (yes > 1 ? "YES" : "NO") + " — only one is followed."));
        }
        if (unmarked > 0) {
            issues.add(Issue.blocking("RULE_UNMARKED_PATH", id,
                    "A line leaving " + named + " isn't marked YES or NO, so it is never followed."));
        }
    }

    /** Every step a line can lead to from {@code start}, following lines in any order. */
    private static Set<String> reachable(String start, Map<String, List<JsonNode>> out) {
        Set<String> seen = new HashSet<>(List.of(start));
        List<String> todo = new ArrayList<>(List.of(start));
        while (!todo.isEmpty()) {
            String cur = todo.remove(todo.size() - 1);
            for (JsonNode e : out.getOrDefault(cur, List.of())) {
                String target = e.path("target").asText();
                if (seen.add(target)) {
                    todo.add(target);
                }
            }
        }
        return seen;
    }

    /**
     * How a step is referred to inside a sentence: its own name in quotes, a
     * rule by what it checks, and a step nobody has named by what it is —
     * never a pair of empty quotes.
     */
    private static String named(JsonNode n) {
        String type = n.path("type").asText();
        if ("condition".equals(type)) {
            String rule = ruleText(n);
            return rule.isBlank() ? "a rule with nothing to check" : "the rule “" + rule + "”";
        }
        String own = switch (type) {
            case "email" -> firstText(n, "emailTemplateName", "label");
            case "sms" -> firstText(n, "smsTemplateName", "label");
            default -> firstText(n, "label");
        };
        if (!own.isBlank()) {
            return "“" + own + "”";
        }
        return switch (type) {
            case "approval" -> "an approval with no name";
            case "email" -> "an email";
            case "sms" -> "a text message";
            case "exception" -> "an exception with no name";
            case "trigger" -> "the start";
            case "end" -> "the outcome";
            default -> "a step with no name";
        };
    }

    private static String text(JsonNode n, String field) {
        return n.path("data").path(field).asText("").trim();
    }

    private static String firstText(JsonNode n, String... fields) {
        for (String field : fields) {
            String value = text(n, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /* ---------------------------------------------------------------- rules */

    /** @return the rule's answer, or {@code null} when the engine does not know the rule */
    static Boolean evalRule(String rule, Facts facts) {
        if (rule == null || rule.isBlank() || !KNOWN_RULES.contains(rule)) {
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

    /** The first line drawn from one step to another; "" when there is none. */
    private static String lineBetween(Map<String, List<JsonNode>> out, String from, String to) {
        return out.getOrDefault(from, List.of()).stream()
                .filter(e -> to.equals(e.path("target").asText()))
                .map(e -> e.path("id").asText(""))
                .findFirst()
                .orElse("");
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
