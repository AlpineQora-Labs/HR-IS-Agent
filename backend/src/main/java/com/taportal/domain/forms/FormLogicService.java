package com.taportal.domain.forms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authoritative evaluator for form logic — the if/then show/hide rules that ride
 * inside a {@link FormDefinition}'s schema JSON.
 *
 * <p>Per the three-tier architecture standard, conditional visibility is a
 * <em>business rule</em> and therefore lives in the middleware. The React
 * builder/preview evaluates the same rules client-side purely for instant UX
 * feedback — that evaluation is a mirror, never the authority. This service is
 * the authority: it re-computes visibility from the submitted answers and
 * decides what is stored and what is required.</p>
 *
 * <h3>Enforcement on submission</h3>
 * <ul>
 *   <li>Answers for questions the rules hide are <b>stripped</b> (a client bug
 *       or a manipulated request cannot smuggle in hidden answers)</li>
 *   <li>Required questions that are <b>visible</b> must have a non-blank answer
 *       (422 otherwise) — "required if visible", matching the client mirror</li>
 *   <li>Display-only blocks (header, image) are never required and never stored</li>
 * </ul>
 *
 * <h3>Rule semantics (must stay in lock-step with fieldVisible() in
 * frontend/src/pages/admin/FormBuilder.tsx)</h3>
 * <ul>
 *   <li>A field with one or more SHOW rules starts hidden and appears when any
 *       show-rule matches (OR)</li>
 *   <li>Any matching HIDE rule hides the field, overriding shows</li>
 *   <li>Operators: equals / not_equals (case-insensitive; multi-answers are
 *       "a, b" joined and match per-option) and contains (substring)</li>
 *   <li>A rule can carry further conditions ({@code also}); it matches only when its first
 *       condition and every further one hold ("School is X <em>and</em> Year is 2027")</li>
 *   <li>A REQUIRE rule makes its target required while it matches. A question is required
 *       when it is marked required or any of its require-rules matches, and only while visible</li>
 * </ul>
 *
 * <h3>Choices that depend on another answer</h3>
 * <p>A form can say that a choice question's options depend on another question's answer
 * ({@code choices}: target, source, and the options for each answer of the source). While the
 * source has an answer the form lists options for, the target offers those and accepts only
 * those; otherwise it offers its own full list. So "Country → School" shows only that
 * country's schools.</p>
 *
 * @see FormDefinition
 * @see com.taportal.api.FormController
 */
@Service
public class FormLogicService {

    /** Display-only field types: render content, collect no answer. */
    private static final Set<String> DISPLAY_TYPES = Set.of("header", "image");

    private final ObjectMapper objectMapper;

    public FormLogicService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * A parsed, answerable-or-display field from the schema, in form order.
     * Public so other renderers (the Aria conversational flow, mapping helpers)
     * share this service's single parse of the schema.
     */
    public record Field(String id, String label, String type, boolean required, List<String> options) {}

    /** One comparison: a question's answer against a value. */
    private record Cond(String sourceId, String op, String value) {}

    /** {@code also}: further conditions that must hold as well. */
    private record Rule(String action, String targetId, String sourceId, String op, String value, List<Cond> also) {}

    /** A choice question whose options depend on another question's answer. Keys are lower-cased answers. */
    private record Choice(String targetId, String sourceId, Map<String, List<String>> options) {}

    /**
     * Validate a submission against the form's rules and return the sanitized
     * answers JSON that should be persisted.
     *
     * <p>Backward compatibility: answers submitted without {@code fieldId}
     * (pre-rules clients) cannot be matched against rules, so they are stored
     * as-is — permissive by design rather than breaking older payloads.</p>
     *
     * @param schemaJson  the form definition's schema (builder JSON)
     * @param answersJson submitted answers: [{fieldId, label, value}]
     * @return the answers to persist — visible fields only, in submitted order
     * @throws ResponseStatusException 400 if either JSON is unparseable,
     *         422 if a visible required question has no answer
     */
    public String validateAndFilter(String schemaJson, String answersJson) {
        JsonNode schema = parse(schemaJson, "form schema");
        JsonNode answers = parse(answersJson, "answers");
        if (!answers.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answers must be a JSON array");
        }

        List<Field> fields = readFields(schema);
        List<Rule> rules = validRules(readRules(schema), fields);

        // Answers keyed by field id. Entries without an id can't participate in
        // rule evaluation — collected separately and passed through untouched.
        Map<String, String> valueById = new HashMap<>();
        List<JsonNode> legacy = new ArrayList<>();
        for (JsonNode a : answers) {
            String id = a.path("fieldId").asText("");
            if (id.isEmpty()) {
                legacy.add(a);
            } else {
                valueById.put(id, a.path("value").asText(""));
            }
        }

        // The authoritative visibility verdict, computed from submitted answers.
        Map<String, Boolean> visible = new HashMap<>();
        for (Field f : fields) {
            visible.put(f.id(), isVisible(f.id(), rules, valueById));
        }

        // Required-if-visible: fail fast with the question named in the message. A question is
        // required when it is marked so, or while one of its require-rules matches.
        List<Choice> choices = validChoices(readChoices(schema), fields);
        for (Field f : fields) {
            boolean isDisplay = DISPLAY_TYPES.contains(f.type());
            if (!isDisplay && isRequired(f, rules, valueById) && visible.get(f.id())
                    && valueById.getOrDefault(f.id(), "").isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Required question has no answer: " + f.label());
            }
            // A choice that depends on another answer accepts only the options that answer allows.
            List<String> allowed = narrowed(f, choices, valueById);
            String given = valueById.getOrDefault(f.id(), "").trim();
            if (allowed != null && visible.get(f.id()) && !given.isEmpty()
                    && allowed.stream().noneMatch(o -> o.equalsIgnoreCase(given))) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "“" + given + "” is not one of the choices for " + f.label());
            }
        }

        // Persist only what the rules allow: visible, answerable, non-blank.
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode a : answers) {
            String id = a.path("fieldId").asText("");
            if (id.isEmpty()) {
                continue; // re-appended below to preserve legacy payloads
            }
            Field f = fields.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
            boolean answerable = f != null && !DISPLAY_TYPES.contains(f.type());
            if (answerable && visible.get(id) && !a.path("value").asText("").isBlank()) {
                out.add(a);
            }
        }
        legacy.forEach(out::add);
        return out.toString();
    }

    /**
     * Rules whose target or source question no longer exists are ignored.
     * Without this guard, a stale show-rule (its source deleted in the builder)
     * would hide its target forever — the answer that could reveal it can never
     * be given. The client's fieldVisible() applies the same guard.
     */
    private List<Rule> validRules(List<Rule> rules, List<Field> fields) {
        Set<String> known = new java.util.HashSet<>();
        fields.forEach(f -> known.add(f.id()));
        return rules.stream()
                .filter(r -> known.contains(r.targetId()) && known.contains(r.sourceId()))
                // A further condition on a deleted question cannot be judged: the rule is ignored whole,
                // not quietly made broader.
                .filter(r -> r.also().stream().allMatch(c -> known.contains(c.sourceId())))
                .toList();
    }

    private List<Choice> validChoices(List<Choice> choices, List<Field> fields) {
        Set<String> known = new java.util.HashSet<>();
        fields.forEach(f -> known.add(f.id()));
        return choices.stream()
                .filter(c -> known.contains(c.targetId()) && known.contains(c.sourceId()) && !c.targetId().equals(c.sourceId()))
                .toList();
    }

    /** Marked required, or made so by a require-rule that matches. */
    private boolean isRequired(Field f, List<Rule> rules, Map<String, String> valueById) {
        return f.required() || rules.stream()
                .filter(r -> r.action().equals("require") && r.targetId().equals(f.id()) && !r.value().isEmpty())
                .anyMatch(r -> matches(r, valueById));
    }

    /**
     * The options a dependent choice is narrowed to by the answers so far; null when it is not
     * narrowed (it depends on nothing, its source is unanswered, or the form lists nothing for
     * that answer), in which case its own options stand.
     */
    private List<String> narrowed(Field f, List<Choice> choices, Map<String, String> valueById) {
        for (Choice c : choices) {
            if (c.targetId().equals(f.id())) {
                String answer = valueById.getOrDefault(c.sourceId(), "").trim().toLowerCase(Locale.ROOT);
                List<String> options = c.options().get(answer);
                if (!answer.isEmpty() && options != null && !options.isEmpty()) {
                    return options;
                }
            }
        }
        return null;
    }

    /** Mirror of the client's fieldVisible(): show-rules gate, hide-rules override. */
    private boolean isVisible(String fieldId, List<Rule> rules, Map<String, String> valueById) {
        List<Rule> mine = rules.stream()
                .filter(r -> r.targetId().equals(fieldId) && !r.sourceId().isEmpty() && !r.value().isEmpty())
                .filter(r -> r.action().equals("show") || r.action().equals("hide"))
                .toList();
        if (mine.isEmpty()) {
            return true;
        }
        List<Rule> shows = mine.stream().filter(r -> r.action().equals("show")).toList();
        List<Rule> hides = mine.stream().filter(r -> r.action().equals("hide")).toList();
        if (!shows.isEmpty() && shows.stream().noneMatch(r -> matches(r, valueById))) {
            return false;
        }
        return hides.stream().noneMatch(r -> matches(r, valueById));
    }

    /** The rule's first condition and every further one. A further condition left unfinished asks nothing. */
    private boolean matches(Rule r, Map<String, String> valueById) {
        return holds(new Cond(r.sourceId(), r.op(), r.value()), valueById)
                && r.also().stream()
                        .filter(c -> !c.sourceId().isEmpty() && !c.value().isEmpty())
                        .allMatch(c -> holds(c, valueById));
    }

    private boolean holds(Cond r, Map<String, String> valueById) {
        String v = valueById.getOrDefault(r.sourceId(), "").trim().toLowerCase(Locale.ROOT);
        String want = r.value().trim().toLowerCase(Locale.ROOT);
        // Multi-select answers arrive joined as "a, b" — equals matches per option.
        List<String> parts = List.of(v.split(", "));
        return switch (r.op()) {
            case "equals" -> v.equals(want) || parts.contains(want);
            case "not_equals" -> !v.equals(want) && !parts.contains(want);
            default -> v.contains(want); // contains
        };
    }

    /** All fields of a schema, in form order (display blocks included). */
    public List<Field> fields(String schemaJson) {
        return readFields(parse(schemaJson, "form schema"));
    }

    /**
     * Answerable fields currently visible given the in-progress answers, in form order, each as
     * it stands for those answers: required when a require-rule makes it so, and offering only
     * the options its dependency allows.
     */
    public List<Field> visibleAnswerableFields(String schemaJson, Map<String, String> answers) {
        JsonNode schema = parse(schemaJson, "form schema");
        List<Field> all = readFields(schema);
        List<Rule> rules = validRules(readRules(schema), all);
        List<Choice> choices = validChoices(readChoices(schema), all);
        return all.stream()
                .filter(f -> !DISPLAY_TYPES.contains(f.type()))
                .filter(f -> isVisible(f.id(), rules, answers))
                .map(f -> {
                    List<String> narrowed = narrowed(f, choices, answers);
                    return new Field(f.id(), f.label(), f.type(), isRequired(f, rules, answers),
                            narrowed != null ? narrowed : f.options());
                })
                .toList();
    }

    private List<Field> readFields(JsonNode schema) {
        List<Field> out = new ArrayList<>();
        for (JsonNode row : schema.path("rows")) {
            for (JsonNode slot : row.path("slots")) {
                if (slot != null && !slot.isNull() && slot.hasNonNull("id")) {
                    List<String> options = new ArrayList<>();
                    for (JsonNode o : slot.path("options")) {
                        options.add(o.asText());
                    }
                    out.add(new Field(
                            slot.path("id").asText(),
                            slot.path("label").asText("Untitled"),
                            slot.path("type").asText(""),
                            slot.path("required").asBoolean(false),
                            options));
                }
            }
        }
        return out;
    }

    private List<Rule> readRules(JsonNode schema) {
        List<Rule> out = new ArrayList<>();
        for (JsonNode r : schema.path("rules")) {
            List<Cond> also = new ArrayList<>();
            for (JsonNode c : r.path("also")) {
                also.add(new Cond(c.path("sourceId").asText(""), c.path("op").asText("equals"), c.path("value").asText("")));
            }
            out.add(new Rule(
                    r.path("action").asText("show"),
                    r.path("targetId").asText(""),
                    r.path("sourceId").asText(""),
                    r.path("op").asText("equals"),
                    r.path("value").asText(""),
                    also));
        }
        return out;
    }

    private List<Choice> readChoices(JsonNode schema) {
        List<Choice> out = new ArrayList<>();
        for (JsonNode c : schema.path("choices")) {
            Map<String, List<String>> options = new HashMap<>();
            c.path("options").fields().forEachRemaining(e -> {
                List<String> list = new ArrayList<>();
                e.getValue().forEach(o -> {
                    if (!o.asText("").isBlank()) {
                        list.add(o.asText().trim());
                    }
                });
                options.put(e.getKey().trim().toLowerCase(Locale.ROOT), list);
            });
            out.add(new Choice(c.path("targetId").asText(""), c.path("sourceId").asText(""), options));
        }
        return out;
    }

    private JsonNode parse(String json, String what) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unparseable " + what);
        }
    }
}
