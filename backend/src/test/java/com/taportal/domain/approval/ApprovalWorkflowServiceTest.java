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
            new ApprovalWorkflowService(repository, mock(ApprovalRequestRepository.class), JSON);

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
    @DisplayName("Test")
    class Simulate {

        private final SimulateRequest flaggedStored = new SimulateRequest("IN_PERSON", 30.0, true, null, null);

        @Test
        void withNoDraftTheStoredWorkflowIsEvaluated() {
            alreadyStored("e1", "Event", true, SOUND);

            SimulateResponse res = service.simulate("e1", flaggedStored);

            assertThat(res.path()).containsExactly("trigger", "rule", "a", "end");
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
        void withNeitherADraftNorAStoredWorkflowThereIsNothingToTest() {
            assertThatThrownBy(() -> service.simulate("missing", flaggedStored))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}
