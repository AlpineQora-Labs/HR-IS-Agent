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
            assertThat(codes(ApprovalRouteEngine.validate(eventApproval("Flagged critical", true, false), null)))
                    .containsExactly("RULE_NO_OTHERWISE");
        }

        @Test
        void aRuleNeedsAPathForYes() {
            assertThat(codes(ApprovalRouteEngine.validate(eventApproval("Flagged critical", false, true), null)))
                    .containsExactly("RULE_NO_YES");
        }

        @Test
        void alwaysDoesNotNeedAnOtherwisePath() {
            assertThat(ApprovalRouteEngine.validate(eventApproval("Always", true, false), null)).isEmpty();
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
                      {"id":"a","type":"approval","data":{}},
                      {"id":"a","type":"approval","data":{}}
                    ],"edges":[]}""");

            assertThat(codes(ApprovalRouteEngine.validate(graph, null)))
                    .containsExactlyInAnyOrder("DUPLICATE_STEP", "NO_START");
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
