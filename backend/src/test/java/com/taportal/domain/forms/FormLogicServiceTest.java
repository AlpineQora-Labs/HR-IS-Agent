package com.taportal.domain.forms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("A form's rules: several conditions, required-when, and choices that depend on an answer")
class FormLogicServiceTest {

    private final FormLogicService logic = new FormLogicService(new ObjectMapper());

    /** Country, school, year, visa, sponsor: with the rules and dependent choices each test adds. */
    private static String form(String rules, String choices) {
        return """
                {"rows":[
                  {"id":"r1","slots":[{"id":"country","type":"dropdown","label":"Country","required":true,"options":["India","United Kingdom"]}]},
                  {"id":"r2","slots":[{"id":"school","type":"dropdown","label":"School","required":true,"options":["IIT Bombay","LSE","Other"]}]},
                  {"id":"r3","slots":[{"id":"year","type":"number","label":"Graduation year","required":false}]},
                  {"id":"r4","slots":[{"id":"visa","type":"yesno","label":"Do you need a visa?","required":false}]},
                  {"id":"r5","slots":[{"id":"sponsor","type":"short","label":"Visa type","required":false}]}
                ],"rules":[%s],"choices":[%s]}
                """.formatted(rules, choices);
    }

    private static String answers(String... idThenValue) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < idThenValue.length; i += 2) {
            out.append(i == 0 ? "" : ",").append("{\"fieldId\":\"%s\",\"label\":\"x\",\"value\":\"%s\"}"
                    .formatted(idThenValue[i], idThenValue[i + 1]));
        }
        return out.append("]").toString();
    }

    private List<String> visible(String schema, Map<String, String> given) {
        return logic.visibleAnswerableFields(schema, given).stream().map(FormLogicService.Field::id).toList();
    }

    private static final String SHOW_SPONSOR_FOR_UK_VISA = """
            {"id":"a","action":"show","targetId":"sponsor","sourceId":"country","op":"equals","value":"United Kingdom",
             "also":[{"sourceId":"visa","op":"equals","value":"Yes"}]}""";

    @Test
    @DisplayName("a rule with a further condition matches only when both hold")
    void allConditionsMustHold() {
        String schema = form(SHOW_SPONSOR_FOR_UK_VISA, "");
        assertThat(visible(schema, Map.of("country", "United Kingdom"))).doesNotContain("sponsor");
        assertThat(visible(schema, Map.of("visa", "Yes"))).doesNotContain("sponsor");
        assertThat(visible(schema, Map.of("country", "United Kingdom", "visa", "Yes"))).contains("sponsor");
    }

    @Test
    @DisplayName("a further condition left unfinished asks nothing")
    void unfinishedConditionIsIgnored() {
        String schema = form("""
                {"id":"a","action":"show","targetId":"sponsor","sourceId":"country","op":"equals","value":"India",
                 "also":[{"sourceId":"visa","op":"equals","value":""}]}""", "");
        assertThat(visible(schema, Map.of("country", "India"))).contains("sponsor");
    }

    @Test
    @DisplayName("a rule whose further condition is on a deleted question is ignored whole")
    void conditionOnADeletedQuestion() {
        String schema = form("""
                {"id":"a","action":"show","targetId":"sponsor","sourceId":"country","op":"equals","value":"India",
                 "also":[{"sourceId":"gone","op":"equals","value":"Yes"}]}""", "");
        // Ignored, so the question it would have gated always shows.
        assertThat(visible(schema, Map.of())).contains("sponsor");
    }

    @Test
    @DisplayName("a require-rule makes a question required while it matches, and only then")
    void requiredWhen() {
        String schema = form("""
                {"id":"a","action":"require","targetId":"year","sourceId":"country","op":"equals","value":"India"}""", "");
        assertThatThrownBy(() -> logic.validateAndFilter(schema, answers("country", "India", "school", "IIT Bombay")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Graduation year");
        // Another country: the year stays optional.
        logic.validateAndFilter(schema, answers("country", "United Kingdom", "school", "LSE"));
        // And the question says so to whoever renders it.
        assertThat(logic.visibleAnswerableFields(schema, Map.of("country", "India")).stream()
                .filter(f -> f.id().equals("year")).findFirst().orElseThrow().required()).isTrue();
        assertThat(logic.visibleAnswerableFields(schema, Map.of("country", "United Kingdom")).stream()
                .filter(f -> f.id().equals("year")).findFirst().orElseThrow().required()).isFalse();
    }

    @Test
    @DisplayName("a require-rule does not hide or show anything")
    void requireDoesNotGate() {
        String schema = form("""
                {"id":"a","action":"require","targetId":"year","sourceId":"country","op":"equals","value":"India"}""", "");
        assertThat(visible(schema, Map.of())).contains("year");
    }

    private static final String SCHOOL_BY_COUNTRY = """
            {"id":"c","targetId":"school","sourceId":"country",
             "options":{"India":["IIT Bombay","Other"],"United Kingdom":["LSE","Other"]}}""";

    @Test
    @DisplayName("a dependent choice offers only the options its source's answer allows")
    void dependentOptions() {
        String schema = form("", SCHOOL_BY_COUNTRY);
        assertThat(logic.visibleAnswerableFields(schema, Map.of("country", "India")).stream()
                .filter(f -> f.id().equals("school")).findFirst().orElseThrow().options())
                .containsExactly("IIT Bombay", "Other");
        // Until the country is chosen, the whole list.
        assertThat(logic.visibleAnswerableFields(schema, Map.of()).stream()
                .filter(f -> f.id().equals("school")).findFirst().orElseThrow().options())
                .containsExactly("IIT Bombay", "LSE", "Other");
    }

    @Test
    @DisplayName("an answer the dependency does not allow is refused")
    void dependentAnswerMustBeAllowed() {
        String schema = form("", SCHOOL_BY_COUNTRY);
        assertThatThrownBy(() -> logic.validateAndFilter(schema, answers("country", "India", "school", "LSE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not one of the choices for School");
        logic.validateAndFilter(schema, answers("country", "India", "school", "iit bombay"));
    }
}
