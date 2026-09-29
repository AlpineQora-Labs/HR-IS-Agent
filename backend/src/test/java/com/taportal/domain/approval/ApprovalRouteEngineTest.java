package com.taportal.domain.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.api.ApprovalDtos.RequiredApproval;
import com.taportal.domain.approval.ApprovalRouteEngine.Facts;
import com.taportal.domain.approval.ApprovalRouteEngine.Issue;
import com.taportal.domain.approval.ApprovalRouteEngine.Route;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Routing and switch-on checks for approval workflows. Pure: no Spring context
 * and no database, so these run anywhere.
 */
class ApprovalRouteEngineTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Facts STANDARD = new Facts("IN_PERSON", 30.0, false);
    private static final Facts FLAGGED = new Facts("IN_PERSON", 30.0, true);

    /**
     * trigger -> Level 1 (Hiring Manager) -> rule -> YES: exception / NO: end.
     * Mirrors the seeded "Event approval" workflow.
     */
    private static JsonNode eventApproval(String rule, boolean wireYes, boolean wireNo) {
        StringBuilder edges = new StringBuilder("""
                {"id":"e1","source":"trigger","target":"lvl"},
                {"id":"e2","source":"lvl","target":"rule"}""");
        if (wireYes) {
            edges.append("""
                    ,{"id":"e3","source":"rule","target":"exc","sourceHandle":"yes"}""");
        }
        if (wireNo) {
            edges.append("""
                    ,{"id":"e4","source":"rule","target":"end","sourceHandle":"no"}""");
        }
        return parse("""
                {"nodes":[
                  {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                  {"id":"lvl","type":"approval","data":{"label":"Level 1","approverRole":"Hiring Manager"}},
                  {"id":"rule","type":"condition","data":{"condition":"%s"}},
                  {"id":"exc","type":"exception","data":{"label":"Compliance review"}},
                  {"id":"end","type":"end","data":{"label":"Approved"}}
                ],"edges":[%s]}""".formatted(rule, edges));
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static List<String> codes(List<Issue> issues) {
        return issues.stream().map(Issue::code).toList();
    }

    /** Only what would stop the workflow being switched on. */
    private static List<String> blocking(List<Issue> issues) {
        return issues.stream().filter(Issue::blocks).map(Issue::code).toList();
    }

    @Nested
    @DisplayName("routing a request")
    class Routing {

        @Test
        void aMetRuleTakesTheYesPath() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged critical", true, true), null, false, FLAGGED);

            assertThat(route.path()).containsExactly("trigger", "lvl", "rule", "exc");
            assertThat(route.approvals()).extracting(RequiredApproval::role).containsExactly("Hiring Manager");
            assertThat(route.problems()).isEmpty();
        }

        @Test
        void anUnmetRuleTakesTheOtherwisePath() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged critical", true, true), null, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "lvl", "rule", "end");
            assertThat(route.problems()).isEmpty();
        }

        @Test
        @DisplayName("an unmet rule with no NO path stops — it never falls through to YES")
        void missingOtherwiseNeverFallsThroughToYes() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged critical", true, false), null, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "lvl", "rule");
            assertThat(route.path()).doesNotContain("exc");
            assertThat(route.problems()).singleElement().asString().contains("no path for NO");
        }

        @Test
        void aMetRuleWithNoYesPathStops() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged critical", false, true), null, false, FLAGGED);

            assertThat(route.path()).containsExactly("trigger", "lvl", "rule");
            assertThat(route.path()).doesNotContain("end");
            assertThat(route.problems()).singleElement().asString().contains("no path for YES");
        }

        @Test
        @DisplayName("a rule the engine does not know is never treated as true")
        void unknownRuleIsNotTrue() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Reschedule requested", true, true), null, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "lvl", "rule");
            assertThat(route.problems()).singleElement().asString().contains("Reschedule requested");
        }

        @Test
        void aRewordedRuleIsUnknownRatherThanSilentlyTrue() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged as critical", true, true), null, false, FLAGGED);

            assertThat(route.problems()).isNotEmpty();
            assertThat(route.path()).doesNotContain("exc", "end");
        }

        @Test
        void alwaysOnlyNeedsItsYesPath() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Always", true, false), null, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "lvl", "rule", "exc");
            assertThat(route.problems()).isEmpty();
        }

        @Test
        void approvalsCollectedBeforeAProblemAreKept() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged critical", true, false), null, false, STANDARD);

            assertThat(route.approvals()).extracting(RequiredApproval::nodeId).containsExactly("lvl");
        }

        @Test
        void theStartIsFoundByTypeNotByItsId() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"start-here","type":"trigger","data":{}},
                      {"id":"a","type":"approval","data":{"label":"Level 1","approverRole":"Admin"}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"start-here","target":"a"},
                      {"id":"e2","source":"a","target":"end"}
                    ]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, STANDARD);

            assertThat(route.path()).containsExactly("start-here", "a", "end");
            assertThat(route.approvals()).hasSize(1);
        }

        @Test
        void aGraphWithNoTriggerReportsItInsteadOfRoutingNowhere() {
            JsonNode graph = parse("""
                    {"nodes":[{"id":"a","type":"approval","data":{}}],"edges":[]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, STANDARD);

            assertThat(route.path()).isEmpty();
            assertThat(route.problems()).singleElement().asString().contains("no starting point");
        }

        @Test
        void aLoopBackEndsTheWalkInsteadOfSpinning() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"a","type":"step","data":{}},
                      {"id":"b","type":"step","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"b"},
                      {"id":"e3","source":"b","target":"a"}
                    ]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "a", "b");
        }

        @Test
        @DisplayName("when two steps share an id the last one drawn is followed, as before")
        void aSharedIdFollowsTheLastDefinition() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"n-approval-1","type":"approval","data":{"label":"First","approverRole":"Recruiter"}},
                      {"id":"end","type":"end","data":{}},
                      {"id":"n-approval-1","type":"approval","data":{"label":"Last","approverRole":"Hiring Manager"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"n-approval-1"},
                      {"id":"e2","source":"n-approval-1","target":"end"}
                    ]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, STANDARD);

            assertThat(route.approvals()).singleElement()
                    .isEqualTo(new RequiredApproval("n-approval-1", "Last", "Hiring Manager"));
        }

        @Test
        void aSharedRuleIdFollowsTheLastDefinition() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"rule","type":"condition","data":{"condition":"Virtual event"}},
                      {"id":"rule","type":"condition","data":{"condition":"Flagged critical"}},
                      {"id":"a","type":"approval","data":{"label":"Compliance","approverRole":"Compliance"}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"rule"},
                      {"id":"e2","source":"rule","target":"a","sourceHandle":"yes"},
                      {"id":"e3","source":"rule","target":"end","sourceHandle":"no"},
                      {"id":"e4","source":"a","target":"end"}
                    ]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, FLAGGED);

            assertThat(route.path()).containsExactly("trigger", "rule", "a", "end");
        }

        @Test
        @DisplayName("a route that asks nobody and stops short of an outcome is reported")
        void aDeadEndThatAskedNobodyIsAProblem() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"s","type":"step","data":{}}
                    ],"edges":[{"id":"e1","source":"trigger","target":"s"}]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, STANDARD);

            assertThat(route.approvals()).isEmpty();
            assertThat(route.problems()).singleElement().asString().contains("asks nobody to approve");
        }

        @Test
        void aTriggerWithNoLineOutIsAProblem() {
            JsonNode graph = parse("""
                    {"nodes":[{"id":"trigger","type":"trigger","data":{}}],"edges":[]}""");

            assertThat(ApprovalRouteEngine.evaluate(graph, null, false, STANDARD).problems()).hasSize(1);
        }

        @Test
        void aLoopThatAskedNobodyIsAProblem() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"rule","type":"condition","data":{"condition":"Flagged critical"}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"rule"},
                      {"id":"e2","source":"rule","target":"end","sourceHandle":"yes"},
                      {"id":"e3","source":"rule","target":"trigger","sourceHandle":"no"}
                    ]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "rule");
            assertThat(route.problems()).hasSize(1);
        }

        @Test
        void aLineToAMissingStepIsAProblem() {
            JsonNode graph = parse("""
                    {"nodes":[{"id":"trigger","type":"trigger","data":{}}],
                     "edges":[{"id":"e1","source":"trigger","target":"gone"}]}""");

            assertThat(ApprovalRouteEngine.evaluate(graph, null, false, STANDARD).problems()).hasSize(1);
        }

        @Test
        @DisplayName("a route that reached an approver is left as it was, even with no outcome drawn")
        void aDeadEndAfterAnApproverIsUnchanged() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"a","type":"approval","data":{"label":"Level 1","approverRole":"Admin"}}
                    ],"edges":[{"id":"e1","source":"trigger","target":"a"}]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, STANDARD);

            assertThat(route.approvals()).hasSize(1);
            assertThat(route.problems()).isEmpty();
        }

        @Test
        void endingAtAnOutcomeWithNoApproverIsNotAProblem() {
            Route route = ApprovalRouteEngine.evaluate(parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[{"id":"e1","source":"trigger","target":"end"}]}"""), null, false, STANDARD);

            assertThat(route.problems()).isEmpty();
        }

        @Test
        void linesLeavingTheLegacyStartIdAreStillWalked() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"a","type":"approval","data":{"label":"Level 1","approverRole":"Admin"}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"end"}
                    ]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "a", "end");
            assertThat(route.approvals()).hasSize(1);
        }

        @Test
        void thePolicyBranchIsSkippedWhenNotAutoApproved() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"fast","type":"policy","data":{}},
                      {"id":"a","type":"approval","data":{"label":"Level 1","approverRole":"Admin"}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e0","source":"trigger","target":"fast"},
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"end"},
                      {"id":"e3","source":"fast","target":"end"}
                    ]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "a", "end");
        }
    }

    @Nested
    @DisplayName("which lines a request followed")
    class Lines {

        /** A rule just put on a line: both of its answers lead to the same step. */
        private final JsonNode bothToTheSameStep = parse("""
                {"nodes":[
                  {"id":"trigger","type":"trigger","data":{}},
                  {"id":"rule","type":"condition","data":{"condition":"Flagged critical"}},
                  {"id":"a","type":"approval","data":{"label":"Level 1","approverRole":"Admin"}},
                  {"id":"end","type":"end","data":{}}
                ],"edges":[
                  {"id":"in","source":"trigger","target":"rule"},
                  {"id":"y","source":"rule","target":"a","sourceHandle":"yes"},
                  {"id":"n","source":"rule","target":"a","sourceHandle":"no"},
                  {"id":"out","source":"a","target":"end"}
                ]}""");

        @Test
        void oneLineIsNamedForEachStepAfterTheFirst() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged critical", true, true), null, false, FLAGGED);

            assertThat(route.path()).containsExactly("trigger", "lvl", "rule", "exc");
            assertThat(route.lines()).containsExactly("e1", "e2", "e3");
        }

        @Test
        @DisplayName("when both answers lead to the same step, only the answer given is followed")
        void theAnswerGivenIsTheLineFollowed() {
            Route met = ApprovalRouteEngine.evaluate(bothToTheSameStep, null, false, FLAGGED);
            Route unmet = ApprovalRouteEngine.evaluate(bothToTheSameStep, null, false, STANDARD);

            assertThat(met.path()).isEqualTo(unmet.path());
            assertThat(met.lines()).containsExactly("in", "y", "out");
            assertThat(unmet.lines()).containsExactly("in", "n", "out");
        }

        @Test
        void aRouteThatStopsNamesOnlyTheLinesItFollowed() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged critical", true, false), null, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "lvl", "rule");
            assertThat(route.lines()).containsExactly("e1", "e2");
        }

        @Test
        void anAutoApprovedRequestFollowsTheBypass() {
            JsonNode withBypass = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"lvl","type":"approval","data":{"label":"Level 1","approverRole":"Admin"}},
                      {"id":"policy","type":"policy","data":{}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"lvl"},
                      {"id":"e2","source":"lvl","target":"end"},
                      {"id":"p1","source":"trigger","target":"policy"},
                      {"id":"p2","source":"policy","target":"end"}
                    ]}""");

            Route route = ApprovalRouteEngine.evaluate(withBypass, null, true, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "policy", "end");
            assertThat(route.lines()).containsExactly("p1", "p2");
        }

        @Test
        @DisplayName("a chain of levels has no lines drawn: each step after the first has a blank entry")
        void theLevelChainHasNoLinesToName() {
            JsonNode levels = parse("""
                    [{"id":"l1","approverRole":"hiring_manager","condition":"Always"}]""");

            Route route = ApprovalRouteEngine.evaluate(null, levels, false, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "lvl-l1", "end");
            assertThat(route.lines()).containsExactly("", "");
        }

        @Test
        @DisplayName("whatever is drawn and whatever the request, there is one line for each step after the first")
        void linesAlwaysMatchThePath() {
            List<JsonNode> drawings = List.of(
                    eventApproval("Flagged critical", true, true),
                    eventApproval("Flagged critical", true, false),
                    eventApproval("Reworded rule", true, true),
                    bothToTheSameStep,
                    // auto-approve with no bypass card, and with no outcome drawn
                    parse("""
                            {"nodes":[{"id":"trigger","type":"trigger","data":{}},{"id":"a","type":"approval","data":{"approverRole":"Admin"}}],
                             "edges":[{"id":"e1","source":"trigger","target":"a"}]}"""),
                    // a bypass card that is drawn but not wired
                    parse("""
                            {"nodes":[{"id":"trigger","type":"trigger","data":{}},{"id":"policy","type":"policy","data":{}},{"id":"end","type":"end","data":{}}],
                             "edges":[{"id":"e1","source":"trigger","target":"end"}]}"""),
                    // no start at all
                    parse("""
                            {"nodes":[{"id":"a","type":"approval","data":{"approverRole":"Admin"}},{"id":"end","type":"end","data":{}}],
                             "edges":[{"id":"e1","source":"a","target":"end"}]}"""),
                    // a loop, and a line to a step that is gone
                    parse("""
                            {"nodes":[{"id":"trigger","type":"trigger","data":{}},{"id":"s","type":"step","data":{}},{"id":"t","type":"step","data":{}}],
                             "edges":[{"id":"e1","source":"trigger","target":"s"},{"id":"e2","source":"s","target":"t"},{"id":"e3","source":"t","target":"s"}]}"""),
                    parse("""
                            {"nodes":[{"id":"trigger","type":"trigger","data":{}},{"id":"a","type":"approval","data":{"approverRole":"Admin"}}],
                             "edges":[{"id":"e1","source":"trigger","target":"a"},{"source":"a","target":"gone"}]}"""));
            JsonNode levels = parse("""
                    [{"id":"l1","approverRole":"hiring_manager","condition":"Always"},{"id":"l2","approverRole":"admin","condition":"Flagged critical"}]""");

            for (boolean autoApprove : new boolean[] {false, true}) {
                for (Facts facts : List.of(STANDARD, FLAGGED)) {
                    for (JsonNode drawing : drawings) {
                        Route route = ApprovalRouteEngine.evaluate(drawing, null, autoApprove, facts);
                        assertThat(route.lines()).as("%s auto=%s %s", drawing, autoApprove, facts)
                                .hasSize(Math.max(0, route.path().size() - 1));
                    }
                    Route chain = ApprovalRouteEngine.evaluate(null, levels, autoApprove, facts);
                    assertThat(chain.lines()).hasSize(chain.path().size() - 1);
                }
            }
        }
    }

    @Nested
    @DisplayName("auto-approve within policy")
    class AutoApprove {

        @Test
        void aStandardRequestSkipsTheChain() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged critical", true, true), null, true, STANDARD);

            assertThat(route.autoApproved()).isTrue();
            assertThat(route.approvals()).isEmpty();
            assertThat(route.path()).containsExactly("trigger", "end");
        }

        @Test
        void aFlaggedRequestIsNeverAutoApproved() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("Flagged critical", true, true), null, true, FLAGGED);

            assertThat(route.autoApproved()).isFalse();
            assertThat(route.approvals()).hasSize(1);
        }

        @Test
        void aShortNoticeRequestIsNeverAutoApproved() {
            Route route = ApprovalRouteEngine.evaluate(
                    eventApproval("Flagged critical", true, true), null, true, new Facts("IN_PERSON", 3.0, false));

            assertThat(route.autoApproved()).isFalse();
        }

        @Test
        @DisplayName("auto-approve is decided by the request, not by whether a start step was drawn")
        void aGraphWithNoStartStillAutoApproves() {
            JsonNode graph = parse("""
                    {"nodes":[{"id":"a","type":"approval","data":{}}],"edges":[]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, true, STANDARD);

            assertThat(route.autoApproved()).isTrue();
            assertThat(route.problems()).isEmpty();
        }

        @Test
        void thePolicyStepIsFoundByType() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"fast-lane","type":"policy","data":{}},
                      {"id":"done","type":"end","data":{}}
                    ],"edges":[]}""");

            Route route = ApprovalRouteEngine.evaluate(graph, null, true, STANDARD);

            assertThat(route.path()).containsExactly("trigger", "fast-lane", "done");
        }
    }

    @Nested
    @DisplayName("what each rule means")
    class Rules {

        @Test
        void shortNoticeIsStrictlyUnderFourteenDays() {
            assertThat(ApprovalRouteEngine.evalRule("Short notice (under 14 days)", new Facts(null, 13.0, null))).isTrue();
            assertThat(ApprovalRouteEngine.evalRule("Short notice (under 14 days)", new Facts(null, 14.0, null))).isFalse();
        }

        @Test
        void unknownLeadTimeIsNotShortNotice() {
            assertThat(ApprovalRouteEngine.evalRule("Short notice (under 14 days)", new Facts(null, null, null))).isFalse();
        }

        @Test
        void eventFormatRulesAreMutuallyExclusive() {
            Facts virtual = new Facts("VIRTUAL", 30.0, false);

            assertThat(ApprovalRouteEngine.evalRule("Virtual event", virtual)).isTrue();
            assertThat(ApprovalRouteEngine.evalRule("In-person event", virtual)).isFalse();
        }

        @Test
        void anUnsetFlagIsNotFlagged() {
            assertThat(ApprovalRouteEngine.evalRule("Flagged critical", new Facts(null, null, null))).isFalse();
        }

        @Test
        void unknownAndMissingRulesHaveNoAnswer() {
            assertThat(ApprovalRouteEngine.evalRule("Salary over band", STANDARD)).isNull();
            assertThat(ApprovalRouteEngine.evalRule(null, STANDARD)).isNull();
        }

        @Test
        @DisplayName("every rule the engine lists as known has a meaning of its own")
        void everyKnownRuleHasAnAnswer() {
            assertThat(ApprovalRouteEngine.KNOWN_RULES).containsExactlyInAnyOrder(
                    "Always", "In-person event", "Virtual event", "Short notice (under 14 days)", "Flagged critical");
            // A request for which each rule's answer differs from "short notice",
            // so a rule that fell through to another rule's meaning would show.
            Facts facts = new Facts("VIRTUAL", 30.0, true);
            assertThat(ApprovalRouteEngine.evalRule("Always", facts)).isTrue();
            assertThat(ApprovalRouteEngine.evalRule("In-person event", facts)).isFalse();
            assertThat(ApprovalRouteEngine.evalRule("Virtual event", facts)).isTrue();
            assertThat(ApprovalRouteEngine.evalRule("Short notice (under 14 days)", facts)).isFalse();
            assertThat(ApprovalRouteEngine.evalRule("Flagged critical", facts)).isTrue();
        }
    }

    @Nested
    @DisplayName("the level chain, when no canvas was saved")
    class Levels {

        private final JsonNode levels = parse("""
                [{"id":"l1","approverRole":"hiring_manager","condition":"Always"},
                 {"id":"l2","approverRole":"admin","condition":"Flagged critical"}]""");

        @Test
        void aLevelAppliesOnlyWhenItsRuleIsMet() {
            assertThat(ApprovalRouteEngine.evaluate(null, levels, false, STANDARD).approvals()).hasSize(1);
            assertThat(ApprovalRouteEngine.evaluate(null, levels, false, FLAGGED).approvals()).hasSize(2);
        }

        @Test
        void anEmptyGraphFallsBackToTheLevels() {
            JsonNode empty = parse("""
                    {"nodes":[],"edges":[]}""");

            assertThat(ApprovalRouteEngine.evaluate(empty, levels, false, FLAGGED).approvals()).hasSize(2);
        }

        @Test
        @DisplayName("a level whose rule can't be checked is required, not skipped")
        void anUncheckableLevelIsRequired() {
            JsonNode odd = parse("""
                    [{"id":"l1","approverRole":"admin","condition":"Bill rate over threshold"}]""");

            Route route = ApprovalRouteEngine.evaluate(null, odd, false, STANDARD);

            assertThat(route.approvals()).hasSize(1);
            assertThat(route.problems()).singleElement().asString().contains("Bill rate over threshold");
        }
    }

    @Nested
    @DisplayName("checks before a workflow can be switched on")
    class Validation {

        @Test
        void aCompleteWorkflowHasNothingToFix() {
            assertThat(ApprovalRouteEngine.validate(eventApproval("Flagged critical", true, true), null)).isEmpty();
        }

        @Test
        void aRuleNeedsAPathForOtherwise() {
            assertThat(blocking(ApprovalRouteEngine.validate(eventApproval("Flagged critical", true, false), null)))
                    .containsExactly("RULE_NO_OTHERWISE");
        }

        @Test
        void aRuleNeedsAPathForYes() {
            assertThat(blocking(ApprovalRouteEngine.validate(eventApproval("Flagged critical", false, true), null)))
                    .containsExactly("RULE_NO_YES");
        }

        @Test
        void alwaysDoesNotNeedAnOtherwisePath() {
            assertThat(blocking(ApprovalRouteEngine.validate(eventApproval("Always", true, false), null))).isEmpty();
        }

        @Test
        void anUnknownRuleIsReported() {
            assertThat(codes(ApprovalRouteEngine.validate(eventApproval("Reschedule requested", true, true), null)))
                    .containsExactly("RULE_UNKNOWN");
        }

        @Test
        void twoLinesFromTheSameAnswerAreReported() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"rule","type":"condition","data":{"condition":"Flagged critical"}},
                      {"id":"a","type":"end","data":{}},
                      {"id":"b","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"rule"},
                      {"id":"e2","source":"rule","target":"a","sourceHandle":"yes"},
                      {"id":"e3","source":"rule","target":"b","sourceHandle":"yes"},
                      {"id":"e4","source":"rule","target":"b","sourceHandle":"no"}
                    ]}""");

            assertThat(codes(ApprovalRouteEngine.validate(graph, null))).containsExactly("RULE_DUPLICATE_PATH");
        }

        @Test
        void aLineLeavingARuleMustBeMarkedYesOrNo() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"rule","type":"condition","data":{"condition":"Flagged critical"}},
                      {"id":"a","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"rule"},
                      {"id":"e2","source":"rule","target":"a","sourceHandle":"yes"},
                      {"id":"e3","source":"rule","target":"a","sourceHandle":"no"},
                      {"id":"e4","source":"rule","target":"a"}
                    ]}""");

            assertThat(codes(ApprovalRouteEngine.validate(graph, null))).containsExactly("RULE_UNMARKED_PATH");
        }

        @Test
        void aMissingStartAndSharedIdsAreReported() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"a","type":"approval","data":{"approverRole":"Admin"}},
                      {"id":"a","type":"approval","data":{"approverRole":"Admin"}}
                    ],"edges":[]}""");

            assertThat(blocking(ApprovalRouteEngine.validate(graph, null)))
                    .containsExactlyInAnyOrder("DUPLICATE_STEP", "NO_START");
        }

        @Test
        @DisplayName("a rule with nothing chosen blocks, and is not quoted as an empty name")
        void aRuleWithNothingToCheckIsReported() {
            List<Issue> issues = ApprovalRouteEngine.validate(eventApproval("", true, true), null);

            assertThat(codes(issues)).containsExactly("RULE_EMPTY");
            assertThat(issues.get(0).message()).doesNotContain("“”");
            assertThat(issues.get(0).blocks()).isTrue();
        }

        @Test
        void aRuleWithNothingToCheckStopsARequest() {
            Route route = ApprovalRouteEngine.evaluate(eventApproval("", true, true), null, false, FLAGGED);

            assertThat(route.path()).containsExactly("trigger", "lvl", "rule");
            assertThat(route.problems()).singleElement().asString().contains("nothing to check");
        }

        @Test
        void anApprovalWithNobodyToApproveBlocks() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"a","type":"approval","data":{"label":"Level 1","approverRole":""}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"end"}
                    ]}""");

            List<Issue> issues = ApprovalRouteEngine.validate(graph, null);

            assertThat(codes(issues)).containsExactly("APPROVER_NO_ROLE");
            assertThat(issues.get(0).blocks()).isTrue();
        }

        @Test
        @DisplayName("what is merely unfinished is advice: it never blocks a switch-on")
        void unfinishedThingsAreAdvice() {
            JsonNode graph = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"mail","type":"email","data":{"label":"Email notification"}},
                      {"id":"text","type":"sms","data":{"smsTemplateId":"t1","smsTemplateName":"Reminder"}},
                      {"id":"stray","type":"step","data":{"label":"Stray step"}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"mail"},
                      {"id":"e2","source":"mail","target":"text"}
                    ]}""");

            List<Issue> issues = ApprovalRouteEngine.validate(graph, null);

            assertThat(issues).noneMatch(Issue::blocks);
            assertThat(issues).extracting(Issue::code, Issue::nodeId).containsExactlyInAnyOrder(
                    org.assertj.core.groups.Tuple.tuple("MESSAGE_NO_TEMPLATE", "mail"),
                    org.assertj.core.groups.Tuple.tuple("OPEN_END", "text"),
                    // The outcome is not reached either, but the open end before
                    // it is what there is to fix — the outcome itself is never reported.
                    org.assertj.core.groups.Tuple.tuple("UNREACHABLE", "stray"));
        }

        /** trigger -> A -> B, with B's two lines (back to A, on to an exception) in the order given. */
        private JsonNode loopAndOutcome(String first, String second) {
            return parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"a","type":"step","data":{"label":"A"}},
                      {"id":"b","type":"step","data":{"label":"B"}},
                      {"id":"exc","type":"exception","data":{"label":"Review"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"b"},
                      {"id":"e3","source":"b","target":"%s"},
                      {"id":"e4","source":"b","target":"%s"}
                    ]}""".formatted(first, second));
        }

        @Test
        @DisplayName("a line back that is never followed is no concern, and an outcome is not an open end")
        void aSecondLineBackIsNotALoop() {
            assertThat(ApprovalRouteEngine.validate(loopAndOutcome("exc", "a"), null)).isEmpty();
        }

        @Test
        @DisplayName("the order of a step's lines decides: the same drawing with the line back first never reaches its outcome")
        void theFirstLineIsTheOneFollowed() {
            JsonNode graph = loopAndOutcome("a", "exc");

            assertThat(ApprovalRouteEngine.evaluate(graph, null, false, STANDARD).path()).containsExactly("trigger", "a", "b");
            assertThat(ApprovalRouteEngine.validate(graph, null))
                    .extracting(Issue::code, Issue::nodeId, Issue::blocks)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("NOT_FOLLOWED", "exc", false),
                            org.assertj.core.groups.Tuple.tuple("LOOP_BACK", "b", false));
        }

        @Test
        @DisplayName("a workflow that always stops for review is not told to fix its outcome")
        void anUnreachedOutcomeIsNotReported() {
            JsonNode alwaysStops = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"x","type":"exception","data":{"label":"Sent for review"}},
                      {"id":"end","type":"end","data":{"label":"Approved"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"x"}
                    ]}""");

            assertThat(ApprovalRouteEngine.validate(alwaysStops, null)).isEmpty();
        }

        @Test
        void anEmailSentWithAnApprovalNeedsAMessageToo() {
            JsonNode attached = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"a","type":"approval","data":{"label":"Level 1","approverRole":"Admin","emailAttached":true}},
                      {"id":"b","type":"approval","data":{"label":"Level 2","approverRole":"Admin","emailAttached":true,"emailTemplateId":"t-1"}},
                      {"id":"end","type":"end","data":{"label":"Approved"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"b"},
                      {"id":"e3","source":"b","target":"end"}
                    ]}""");

            List<Issue> issues = ApprovalRouteEngine.validate(attached, null);

            assertThat(issues).singleElement().satisfies(issue -> {
                assertThat(issue.code()).isEqualTo("MESSAGE_NO_TEMPLATE");
                assertThat(issue.nodeId()).isEqualTo("a");
                assertThat(issue.blocks()).isFalse();
                assertThat(issue.message()).isEqualTo("The email sent with “Level 1” has no message chosen yet.");
            });
        }

        @Test
        @DisplayName("an approval with nobody to approve it and no message for its email is told both")
        void bothThingsWrongWithAnApprovalAreReported() {
            JsonNode both = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"a","type":"approval","data":{"label":"Level 1","emailAttached":true}},
                      {"id":"end","type":"end","data":{"label":"Approved"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"end"}
                    ]}""");

            assertThat(codes(ApprovalRouteEngine.validate(both, null)))
                    .containsExactly("APPROVER_NO_ROLE", "MESSAGE_NO_TEMPLATE");
        }

        @Test
        @DisplayName("every step is called something a person can find — never a pair of empty quotes")
        void stepsAreNamedForWhatTheyAre() {
            JsonNode unnamed = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"end","type":"end","data":{"label":"Approved"}},
                      {"id":"rule","type":"condition","data":{"condition":"Flagged critical"}},
                      {"id":"blank","type":"condition","data":{"condition":""}},
                      {"id":"a","type":"approval","data":{"label":"  ","approverRole":"Admin"}},
                      {"id":"mail","type":"email","data":{"label":""}},
                      {"id":"text","type":"sms","data":{"smsTemplateName":"Reminder","smsTemplateId":"s-1"}},
                      {"id":"x","type":"exception","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"end"},
                      {"id":"e2","source":"rule","target":"end","sourceHandle":"yes"},
                      {"id":"e3","source":"rule","target":"end","sourceHandle":"no"},
                      {"id":"e4","source":"blank","target":"end","sourceHandle":"yes"},
                      {"id":"e5","source":"blank","target":"end","sourceHandle":"no"},
                      {"id":"e6","source":"a","target":"end"},
                      {"id":"e7","source":"mail","target":"end"},
                      {"id":"e8","source":"text","target":"end"}
                    ]}""");

            List<String> unreached = ApprovalRouteEngine.validate(unnamed, null).stream()
                    .filter(i -> "UNREACHABLE".equals(i.code()))
                    .map(Issue::message)
                    .toList();

            assertThat(unreached).containsExactly(
                    "The rule “Flagged critical” isn't connected to the flow, so it is never reached.",
                    "A rule with nothing to check isn't connected to the flow, so it is never reached.",
                    "An approval with no name isn't connected to the flow, so it is never reached.",
                    "An email isn't connected to the flow, so it is never reached.",
                    "“Reminder” isn't connected to the flow, so it is never reached.",
                    "An exception with no name isn't connected to the flow, so it is never reached.");
            assertThat(ApprovalRouteEngine.validate(unnamed, null))
                    .extracting(Issue::message)
                    .noneMatch(m -> m.contains("“”") || m.contains("This step"));
        }

        @Test
        @DisplayName("a step leads to the first line drawn out of it: what sits on a second line is never reached, and is said so")
        void aStepOnASecondLineIsReported() {
            JsonNode twoLines = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"a","type":"approval","data":{"label":"Manager","approverRole":"Hiring Manager"}},
                      {"id":"b","type":"approval","data":{"label":"Compliance","approverRole":"Admin"}},
                      {"id":"end","type":"end","data":{"label":"Approved"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"trigger","target":"b"},
                      {"id":"e3","source":"a","target":"end"},
                      {"id":"e4","source":"b","target":"end"}
                    ]}""");

            assertThat(ApprovalRouteEngine.evaluate(twoLines, null, false, STANDARD).path())
                    .containsExactly("trigger", "a", "end");
            assertThat(ApprovalRouteEngine.validate(twoLines, null)).singleElement().satisfies(issue -> {
                assertThat(issue.code()).isEqualTo("NOT_FOLLOWED");
                assertThat(issue.nodeId()).isEqualTo("b");
                assertThat(issue.blocks()).isFalse();
                assertThat(issue.message())
                        .isEqualTo("“Compliance” is never reached: a request always takes another line before it.");
            });
        }

        @Test
        void theOtherwiseSideOfAnAlwaysRuleIsNeverReached() {
            JsonNode always = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"r","type":"condition","data":{"condition":"Always"}},
                      {"id":"a","type":"approval","data":{"label":"Level 1","approverRole":"Admin"}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"r"},
                      {"id":"e2","source":"r","target":"end","sourceHandle":"yes"},
                      {"id":"e3","source":"r","target":"a","sourceHandle":"no"},
                      {"id":"e4","source":"a","target":"end"}
                    ]}""");

            assertThat(ApprovalRouteEngine.validate(always, null))
                    .extracting(Issue::code, Issue::nodeId)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("NOT_FOLLOWED", "a"));
        }

        @Test
        @DisplayName("what is wrong with a step no request can come to holds nothing back")
        void aStrayStepDoesNotBlock() {
            JsonNode stray = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"a","type":"approval","data":{"label":"Manager","approverRole":"Hiring Manager"}},
                      {"id":"end","type":"end","data":{"label":"Approved"}},
                      {"id":"lost","type":"approval","data":{"label":"New approval"}},
                      {"id":"odd","type":"condition","data":{"condition":""}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"end"}
                    ]}""");

            List<Issue> issues = ApprovalRouteEngine.validate(stray, null);

            assertThat(blocking(issues)).isEmpty();
            assertThat(codes(issues)).contains("APPROVER_NO_ROLE", "RULE_EMPTY", "UNREACHABLE");
        }

        @Test
        void theSameFaultsOnTheWayDoBlock() {
            JsonNode onTheWay = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"a","type":"approval","data":{"label":"Manager","approverRole":"   "}},
                      {"id":"end","type":"end","data":{"label":"Approved"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"end"}
                    ]}""");

            assertThat(blocking(ApprovalRouteEngine.validate(onTheWay, null))).containsExactly("APPROVER_NO_ROLE");
        }

        @Test
        @DisplayName("a path that leads back, or to a step that is gone, is reported where it ends")
        void pathsThatEndNowhereAreReported() {
            JsonNode loops = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"m","type":"email","data":{"label":"Heads-up","emailTemplateId":"t-1","emailTemplateName":"Heads-up"}},
                      {"id":"r","type":"condition","data":{"condition":"Flagged critical"}},
                      {"id":"x","type":"exception","data":{"label":"Compliance review"}},
                      {"id":"a","type":"approval","data":{"label":"Manager","approverRole":"Admin"}},
                      {"id":"end","type":"end","data":{"label":"Approved"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"m"},
                      {"id":"e2","source":"m","target":"r"},
                      {"id":"e3","source":"r","target":"x","sourceHandle":"yes"},
                      {"id":"e4","source":"r","target":"m","sourceHandle":"no"}
                    ]}""");
            JsonNode gone = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"a","type":"approval","data":{"label":"Manager","approverRole":"Admin"}},
                      {"id":"end","type":"end","data":{"label":"Approved"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"a"},
                      {"id":"e2","source":"a","target":"removed"}
                    ]}""");

            assertThat(ApprovalRouteEngine.validate(loops, null))
                    .extracting(Issue::code, Issue::nodeId, Issue::message)
                    .contains(org.assertj.core.groups.Tuple.tuple("LOOP_BACK", "r",
                            "The rule “Flagged critical” leads back to “Heads-up”, so a request that comes this way never reaches an outcome."));
            assertThat(ApprovalRouteEngine.validate(gone, null))
                    .extracting(Issue::code, Issue::nodeId, Issue::message)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("LINE_TO_NOWHERE", "a",
                            "A line leaving “Manager” leads to a step that no longer exists."));
        }

        @Test
        void aTextMessageWithNothingChosenIsReported() {
            JsonNode text = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"t","type":"sms","data":{"label":"SMS message"}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"t"},
                      {"id":"e2","source":"t","target":"end"}
                    ]}""");

            assertThat(ApprovalRouteEngine.validate(text, null))
                    .extracting(Issue::code, Issue::nodeId)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("MESSAGE_NO_TEMPLATE", "t"));
        }

        @Test
        @DisplayName("the auto-approve card is neither a step to reach nor a next step")
        void theBypassCardIsNotPartOfTheWalk() {
            JsonNode unwired = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"end","type":"end","data":{}},
                      {"id":"policy","type":"policy","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"end"}
                    ]}""");
            JsonNode onlyTheBypass = parse("""
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Event"}},
                      {"id":"end","type":"end","data":{}},
                      {"id":"policy","type":"policy","data":{}}
                    ],"edges":[
                      {"id":"p1","source":"trigger","target":"policy"},
                      {"id":"p2","source":"policy","target":"end"}
                    ]}""");

            assertThat(ApprovalRouteEngine.validate(unwired, null)).isEmpty();
            assertThat(ApprovalRouteEngine.validate(onlyTheBypass, null))
                    .extracting(Issue::code, Issue::nodeId)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("OPEN_END", "trigger"));
        }

        @Test
        void aLevelWithNothingToCheckIsNotQuotedAsAnEmptyName() {
            JsonNode levels = parse("""
                    [{"id":"l1","approverRole":"admin","condition":""}]""");

            assertThat(ApprovalRouteEngine.validate(null, levels)).singleElement()
                    .extracting(Issue::message).isEqualTo("Level 1 has a rule with nothing to check.");
            assertThat(ApprovalRouteEngine.evaluate(null, levels, false, STANDARD).problems()).singleElement()
                    .isEqualTo("Level 1 has a rule with nothing to check — the level is required to be safe.");
        }

        @Test
        void levelRulesAreCheckedWhenThereIsNoCanvas() {
            JsonNode levels = parse("""
                    [{"id":"l1","approverRole":"admin","condition":"Always"},
                     {"id":"l2","approverRole":"admin","condition":"Bill rate over threshold"}]""");

            assertThat(codes(ApprovalRouteEngine.validate(null, levels))).containsExactly("LEVEL_RULE_UNKNOWN");
        }

        @Test
        @DisplayName("the seeded Event approval workflow routes every kind of request without a problem")
        void theEventApprovalWorkflowRoutesEveryRequest() {
            JsonNode graph = eventApproval("Flagged critical", true, true);
            assertThat(ApprovalRouteEngine.validate(graph, null)).isEmpty();

            for (Facts facts : List.of(STANDARD, FLAGGED, new Facts("VIRTUAL", 2.0, true), new Facts(null, null, null))) {
                assertThat(ApprovalRouteEngine.evaluate(graph, null, false, facts).problems()).as(facts.toString()).isEmpty();
            }
        }
    }
}
