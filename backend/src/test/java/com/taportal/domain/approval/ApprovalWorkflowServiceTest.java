package com.taportal.domain.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.api.ApprovalDtos.CheckIssue;
import com.taportal.api.ApprovalDtos.SimulateRequest;
import com.taportal.api.ApprovalDtos.SimulateResponse;
import com.taportal.api.ApprovalDtos.WorkflowDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Saving and testing workflows: what may be switched on, and what Test evaluates. */
class ApprovalWorkflowServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** trigger -> rule "Flagged critical" -> YES: approval -> end; NO wired only when asked. */
    private static final String SOUND = graph(true);
    private static final String MISSING_OTHERWISE = graph(false);

    private static String graph(boolean wireNo) {
        return """
                {"nodes":[
                  {"id":"trigger","type":"trigger","data":{}},
                  {"id":"rule","type":"condition","data":{"condition":"Flagged critical"}},
                  {"id":"a","type":"approval","data":{"label":"Compliance","approverRole":"Admin"}},
                  {"id":"end","type":"end","data":{}}
                ],"edges":[
                  {"id":"e1","source":"trigger","target":"rule"},
                  {"id":"e2","source":"rule","target":"a","sourceHandle":"yes"},
                  {"id":"e3","source":"a","target":"end"}%s
                ]}""".formatted(wireNo ? """
                ,{"id":"e4","source":"rule","target":"end","sourceHandle":"no"}""" : "");
    }

    private final Map<String, ApprovalWorkflowRecord> stored = new LinkedHashMap<>();
    private final ApprovalWorkflowRepository repository = mock(ApprovalWorkflowRepository.class);
    private final ApprovalWorkflowService service =
            new ApprovalWorkflowService(repository, mock(ApprovalRequestRepository.class), JSON,
                    mock(com.taportal.domain.journey.JourneyTemplates.class));

    @BeforeEach
    void anInMemoryStore() {
        when(repository.findByWfKey(anyString()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.<String>getArgument(0))));
        when(repository.save(any(ApprovalWorkflowRecord.class))).thenAnswer(call -> {
            ApprovalWorkflowRecord r = call.getArgument(0);
            stored.put(r.getWfKey(), r);
            return r;
        });
        when(repository.findByOrderByWfKey()).thenAnswer(call -> new ArrayList<>(stored.values()));
        when(repository.findByOrderByCreatedAtDesc()).thenAnswer(call -> new ArrayList<>(stored.values()));
    }

    private void alreadyStored(String key, String trigger, boolean enabled, String graphJson) {
        stored.put(key, new ApprovalWorkflowRecord(null, key, key, trigger, enabled, false, "[]", graphJson, null, null));
    }

    private static WorkflowDto dto(String key, String trigger, boolean enabled, String graphJson) {
        return new WorkflowDto(key, key, trigger, enabled, false, parse("[]"), graphJson == null ? null : parse(graphJson), null);
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void refused(Runnable save) {
        assertThatThrownBy(save::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Nested
    @DisplayName("switching a workflow on")
    class SwitchOn {

        @Test
        void aSoundEventWorkflowCanBeSwitchedOn() {
            assertThatCode(() -> service.saveAll(List.of(dto("e1", "Event", true, SOUND)))).doesNotThrowAnyException();
        }

        @Test
        void anEventWorkflowWithAnUnanswerableRuleCannot() {
            refused(() -> service.saveAll(List.of(dto("e1", "Event", true, MISSING_OTHERWISE))));
        }

        @Test
        void theRefusalNamesTheWorkflowAndTheRule() {
            assertThatThrownBy(() -> service.saveAll(List.of(dto("e1", "Event", true, MISSING_OTHERWISE))))
                    .hasMessageContaining("e1")
                    .hasMessageContaining("Flagged critical")
                    .hasMessageContaining("NO (otherwise)");
        }

        @Test
        void switchingOnAStoredDraftIsChecked() {
            alreadyStored("e1", "Event", false, MISSING_OTHERWISE);

            // The list page sends no graph; the stored one is what would go live.
            refused(() -> service.saveAll(List.of(dto("e1", "Event", true, null))));
        }

        @Test
        void redrawingALiveWorkflowIsChecked() {
            alreadyStored("e1", "Event", true, SOUND);

            refused(() -> service.saveAll(List.of(dto("e1", "Event", true, MISSING_OTHERWISE))));
        }

        @Test
        void retargetingALiveDesignOntoARoutedTriggerIsChecked() {
            alreadyStored("cj1", "Candidate journey", true, MISSING_OTHERWISE);

            refused(() -> service.saveAll(List.of(dto("cj1", "Event", true, null))));
        }
    }

    @Nested
    @DisplayName("saves that must never be blocked")
    class AlwaysSaves {

        @Test
        void aDraftSavesHoweverIncomplete() {
            assertThatCode(() -> service.saveAll(List.of(dto("draft", "Event", false, MISSING_OTHERWISE))))
                    .doesNotThrowAnyException();
        }

        @Test
        void aDesignOnlyTriggerSavesEvenWhenSwitchedOn() {
            assertThatCode(() -> service.saveAll(List.of(dto("cj1", "Candidate journey", true, MISSING_OTHERWISE))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a workflow that was already live and unsound does not block other saves")
        void anUnrelatedSaveIsNotHeldHostage() {
            alreadyStored("e1", "Event", true, MISSING_OTHERWISE);

            assertThatCode(() -> service.saveAll(List.of(dto("draft", "Offer", false, SOUND)))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the list page re-sending that workflow unchanged does not block the batch either")
        void resendingAnUnchangedLiveWorkflowIsNotRechecked() {
            alreadyStored("e1", "Event", true, MISSING_OTHERWISE);

            assertThatCode(() -> service.saveAll(List.of(
                    dto("e1", "Event", true, null),
                    dto("draft", "Offer", false, SOUND)))).doesNotThrowAnyException();
        }

        @Test
        void switchingAnUnsoundWorkflowOffIsAllowed() {
            alreadyStored("e1", "Event", true, MISSING_OTHERWISE);

            assertThatCode(() -> service.saveAll(List.of(dto("e1", "Event", false, null)))).doesNotThrowAnyException();
            assertThat(stored.get("e1").isEnabled()).isFalse();
        }

        @Test
        void aPayloadWithoutAGraphNeverErasesTheStoredOne() {
            alreadyStored("e1", "Event", true, SOUND);

            service.saveAll(List.of(dto("e1", "Event", true, null)));

            assertThat(parse(stored.get("e1").getGraphJson())).isEqualTo(parse(SOUND));
        }
    }

    @Nested
    @DisplayName("what is left to finish")
    class Check {

        @Test
        void aDrawingIsCheckedWithoutBeingStored() {
            var issues = service.check(parse(MISSING_OTHERWISE), null, "Event");

            assertThat(issues).extracting(CheckIssue::code).containsExactly("RULE_NO_OTHERWISE");
            assertThat(stored).isEmpty();
        }

        @Test
        @DisplayName("what would refuse a save is marked as holding the workflow back")
        void onARoutedTriggerTheIssueHoldsTheWorkflowBack() {
            assertThat(service.check(parse(MISSING_OTHERWISE), null, "event"))
                    .singleElement().extracting(CheckIssue::blocking).isEqualTo(true);
        }

        @Test
        @DisplayName("a design-only workflow is never told something is needed before switching on")
        void onADesignOnlyTriggerNothingHoldsItBack() {
            var issues = service.check(parse(MISSING_OTHERWISE), null, "Candidate journey");

            assertThat(issues).extracting(CheckIssue::code).containsExactly("RULE_NO_OTHERWISE");
            assertThat(issues).extracting(CheckIssue::blocking).containsExactly(false);
            // … which is exactly what saving it does.
            assertThatCode(() -> service.saveAll(List.of(dto("cj1", "Candidate journey", true, MISSING_OTHERWISE))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("what the canvas says is needed is exactly what a save would refuse, whatever the trigger")
        void neededMeansASaveWouldBeRefused() {
            for (String trigger : new String[] {"Event", "event", "EVENT", " Event ", "Events", "Offer", "Candidate journey", "", "  "}) {
                stored.clear(); // nothing here rolls a refused save back
                boolean needed = service.check(parse(MISSING_OTHERWISE), null, trigger).stream().anyMatch(CheckIssue::blocking);
                boolean refused;
                try {
                    service.saveAll(List.of(dto("w-" + trigger.hashCode(), trigger, true, MISSING_OTHERWISE)));
                    refused = false;
                } catch (ResponseStatusException e) {
                    refused = true;
                }

                assertThat(needed).as("trigger '%s'", trigger).isEqualTo(refused);
            }
        }

        @Test
        void withNoTriggerNothingIsHeldBack() {
            assertThat(service.check(parse(MISSING_OTHERWISE), null, null))
                    .singleElement().extracting(CheckIssue::blocking).isEqualTo(false);
        }

        @Test
        @DisplayName("going back to an earlier step is part of a journey, and only a concern where requests are routed")
        void aLineThatLeadsBackIsOnlyReportedWhereRequestsAreRouted() {
            String loops = """
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{"label":"Applies"}},
                      {"id":"s","type":"step","data":{"label":"Self-schedule"}},
                      {"id":"r","type":"condition","data":{"condition":"Flagged critical"}},
                      {"id":"end","type":"end","data":{"label":"Done"}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"s"},
                      {"id":"e2","source":"s","target":"r"},
                      {"id":"e3","source":"r","target":"s","sourceHandle":"yes"},
                      {"id":"e4","source":"r","target":"end","sourceHandle":"no"}
                    ]}""";

            assertThat(service.check(parse(loops), null, "Candidate journey")).isEmpty();
            assertThat(service.check(parse(loops), null, "Event"))
                    .extracting(CheckIssue::code, CheckIssue::blocking, CheckIssue::nodeId)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("LOOP_BACK", false, "r"));
        }

        @Test
        @DisplayName("advice alone never stops a workflow being switched on")
        void adviceDoesNotBlockASwitchOn() {
            String unfinished = """
                    {"nodes":[
                      {"id":"trigger","type":"trigger","data":{}},
                      {"id":"mail","type":"email","data":{"label":"Email notification"}},
                      {"id":"a","type":"approval","data":{"label":"Level 1","approverRole":"Admin"}},
                      {"id":"end","type":"end","data":{}}
                    ],"edges":[
                      {"id":"e1","source":"trigger","target":"mail"},
                      {"id":"e2","source":"mail","target":"a"},
                      {"id":"e3","source":"a","target":"end"}
                    ]}""";

            assertThat(service.check(parse(unfinished), null, "Event")).isNotEmpty()
                    .allSatisfy(issue -> assertThat(issue.blocking()).isFalse());
            assertThatCode(() -> service.saveAll(List.of(dto("e1", "Event", true, unfinished)))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Test")
    class Simulate {

        private final SimulateRequest flaggedStored = new SimulateRequest("IN_PERSON", 30.0, true, null, null);

        @Test
        void withNoDraftTheStoredWorkflowIsEvaluated() {
            alreadyStored("e1", "Event", true, SOUND);

            SimulateResponse res = service.simulate("e1", flaggedStored);

            assertThat(res.path()).containsExactly("trigger", "rule", "a", "end");
            assertThat(res.lines()).containsExactly("e1", "e2", "e3");
        }

        @Test
        @DisplayName("a draft is evaluated instead of what is stored, and nothing is saved")
        void aDraftIsEvaluatedWithoutBeingStored() {
            alreadyStored("e1", "Event", true, SOUND);

            SimulateResponse res = service.simulate("e1",
                    new SimulateRequest("IN_PERSON", 30.0, false, parse(MISSING_OTHERWISE), null));

            assertThat(res.problems()).singleElement().asString().contains("no path for NO");
            assertThat(parse(stored.get("e1").getGraphJson())).isEqualTo(parse(SOUND));
        }

        @Test
        void aDraftCanBeTestedBeforeTheWorkflowWasEverStored() {
            SimulateResponse res = service.simulate("brand-new",
                    new SimulateRequest("IN_PERSON", 30.0, true, parse(SOUND), null));

            assertThat(res.requiredApprovals()).hasSize(1);
            assertThat(stored).isEmpty();
        }

        @Test
        void theUnsavedAutoApproveSettingIsHonoured() {
            alreadyStored("e1", "Event", true, SOUND);

            SimulateResponse res = service.simulate("e1",
                    new SimulateRequest("IN_PERSON", 30.0, false, parse(SOUND), true));

            assertThat(res.autoApproved()).isTrue();
        }

        @Test
        @DisplayName("a candidate journey is not routed: there is nothing to test, and it is not called broken")
        void aJourneyIsNotRouted() {
            alreadyStored("cj1", "Candidate journey", true, SOUND);

            assertThatThrownBy(() -> service.simulate("cj1", flaggedStored))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                        assertThat(e.getReason()).contains("not routed");
                    });
            // A draft says what it is a draft of.
            assertThatThrownBy(() -> service.simulate("brand-new",
                    new SimulateRequest("IN_PERSON", 30.0, true, parse(SOUND), null, "candidate journey")))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        }

        @Test
        void withNeitherADraftNorAStoredWorkflowThereIsNothingToTest() {
            assertThatThrownBy(() -> service.simulate("missing", flaggedStored))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}
