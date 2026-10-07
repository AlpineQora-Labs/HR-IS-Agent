package com.taportal.aria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.api.CampusDtos.RegistrationRow;
import com.taportal.domain.engagement.CampusService;
import com.taportal.domain.engagement.RecruitingEvent;
import com.taportal.domain.engagement.RecruitingEventRepository;
import com.taportal.domain.forms.FormDefinitionRepository;
import com.taportal.domain.forms.FormLogicService;
import jakarta.persistence.EntityNotFoundException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Aria's conversational renderer for the EVENT_REGISTRATION form — the QR-code
 * booth flow: a student scans, chats with Aria, and lands on the event roster
 * as a real candidate.
 *
 * <p>Architecture (three-tier + aria module rules):</p>
 * <ul>
 *   <li>The form definition and its if/then rules are the SAME ones the web
 *       form renders — {@link FormLogicService} decides which question comes
 *       next, so a rule like "show Areas of interest when School is Georgia
 *       Tech" makes Aria ask the follow-up dynamically.</li>
 *   <li>Completion goes through {@link CampusService#registerFromForm} — the
 *       single authoritative business path (dedupe, candidate materialization,
 *       roster row, stored answers). Aria owns only the conversation.</li>
 * </ul>
 *
 * <p>Conversation state (current answers keyed by field id) is serialized into
 * {@code conversation.context}, the same pattern the apply engine uses.</p>
 */
@Service
public class EventRegistrationChat {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.ENGLISH);
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    /** One turn of the chat: full transcript + quick-reply chips for the current question. */
    public record Turn(UUID conversationId, boolean done, List<MessageView> messages, List<String> options) {}

    public record MessageView(String sender, String body) {}

    /** Serialized to conversation.context between turns. */
    static final class State {
        public UUID eventId;
        public Map<String, String> answers = new HashMap<>();
        public boolean done;
        /** The student has been shown what they agree to, and has agreed. Nobody is registered before both. */
        public boolean consentAsked;
        public boolean consented;
    }

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final FormDefinitionRepository formDefinitions;
    private final FormLogicService formLogic;
    private final CampusService campusService;
    private final RecruitingEventRepository events;
    private final ObjectMapper objectMapper;
    /** The name the assistant introduces itself by. */
    private final String name;

    public EventRegistrationChat(
            ConversationRepository conversations,
            MessageRepository messages,
            FormDefinitionRepository formDefinitions,
            FormLogicService formLogic,
            CampusService campusService,
            RecruitingEventRepository events,
            ObjectMapper objectMapper,
            @Value("${app.assistant-name:Bank of America Careers}") String name) {
        this.name = name == null || name.isBlank() ? "Bank of America Careers" : name.trim();
        this.conversations = conversations;
        this.messages = messages;
        this.formDefinitions = formDefinitions;
        this.formLogic = formLogic;
        this.campusService = campusService;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    /** Begin: greet with the event context and ask the first visible question. */
    @Transactional
    public Turn start(UUID eventId) {
        RecruitingEvent event = events.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        Conversation conversation = new Conversation();
        conversation.setChannel("WEB_CHAT");
        conversation.setLanguage("en");
        conversation.setKind("EVENT_REG");
        conversation.setStatus("OPEN");
        conversation = conversations.save(conversation);

        State state = new State();
        state.eventId = eventId;

        String when = event.getStartsAt() == null ? ""
                : " on " + event.getStartsAt().atZoneSameInstant(ZONE).format(WHEN);
        // An event that is not taking registrations says so before asking anything.
        String registration = campusService.publicInfo(eventId).registration();
        if ("CLOSED".equals(registration) || "FULL".equals(registration)) {
            state.done = true;
            conversation.setStatus("COMPLETED");
            aria(conversation, "closed", "Hello, this is " + name + ". " + ("FULL".equals(registration)
                    ? event.getName() + " is full, so I can't register you for it."
                    : "Registration for " + event.getName() + " has closed, so I can't register you for it.")
                    + " Have a look at our other events on the careers site.");
            persist(conversation, state);
            return turn(conversation, state);
        }
        aria(conversation, "greeting", "Hello, this is " + name + ". Good to see you at " + event.getName() + when
                + ". Let's get you registered — it takes under a minute."
                + ("WAITLIST".equals(registration)
                        ? " Every seat is taken for now, so this puts you on the waitlist." : ""));

        FormLogicService.Field first = nextUnanswered(state);
        if (first != null) {
            aria(conversation, "ask", prompt(first));
        }
        persist(conversation, state);
        return turn(conversation, state);
    }

    /** One student reply: capture the answer, let the rules pick the next question. */
    @Transactional
    public Turn reply(UUID conversationId, String text) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found: " + conversationId));
        State state = readState(conversation);
        if (state.done) {
            aria(conversation, "done", "You're all set. See you at the event.");
            return turn(conversation, state);
        }

        student(conversation, text);

        FormLogicService.Field current = nextUnanswered(state);
        if (current == null && state.consentAsked && !state.consented) {
            // The one answer that is not a form question: do they agree to their details being kept?
            String said = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
            if (said.matches("^(yes|y|yeah|yep|ok|okay|sure|i agree|agree|agreed)\\b.*")) {
                state.consented = true;
            } else {
                aria(conversation, "consent.reask", "I can't register you without that. "
                        + "If you're happy for us to keep your details, reply Yes. Otherwise you can close this chat "
                        + "and nothing is saved.");
                persist(conversation, state);
                return turn(conversation, state);
            }
        }
        if (current != null) {
            String value = interpret(current, text);
            if (value == null) {
                // Could not match a choice — re-ask with the options restated.
                aria(conversation, "reask", "Sorry, I didn't catch that. " + prompt(current));
                persist(conversation, state);
                return turn(conversation, state);
            }
            if (value.isBlank() && current.required()) {
                aria(conversation, "reask", "I do need that one to register you. " + prompt(current));
                persist(conversation, state);
                return turn(conversation, state);
            }
            state.answers.put(current.id(), value);
        }

        FormLogicService.Field next = nextUnanswered(state);
        if (next != null) {
            aria(conversation, "ask", prompt(next));
            persist(conversation, state);
            return turn(conversation, state);
        }

        // No questions left. Nothing is saved until the student has agreed to it being kept.
        if (!state.consented) {
            state.consentAsked = true;
            aria(conversation, "consent", "One last thing before I save this. " + campusService.consentQuestion(state.eventId)
                    + " Reply Yes to agree.");
            persist(conversation, state);
            return turn(conversation, state);
        }

        // Agreed → the one authoritative registration path.
        try {
            RegistrationRow row = campusService.registerFromForm(state.eventId, answersJson(state), true);
            state.done = true;
            conversation.setStatus("COMPLETED");
            conversation.setCandidateId(row.candidateId());
            aria(conversation, "confirm", "WAITLISTED".equals(row.status())
                    ? "You're on the waitlist, " + firstName(row.name()) + ", at number " + row.waitlistPosition()
                            + ". Every seat is taken for now. If one comes free it is yours, and our team will reach you at "
                            + row.email() + "."
                    : "You're registered, " + firstName(row.name()) + ". "
                            + "We've saved your details — swing by the Bank of America booth and we'll take it from there. "
                            + "We'll reach you at " + row.email() + " if anything changes.");
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.GONE.value()) {
                // Full, or closed, since the chat began.
                state.done = true;
                conversation.setStatus("COMPLETED");
                aria(conversation, "closed", "I'm sorry: " + (ex.getReason() != null
                        ? Character.toLowerCase(ex.getReason().charAt(0)) + ex.getReason().substring(1) : "registration has closed")
                        + ", so I couldn't register you. Nothing was saved.");
            } else if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                state.done = true;
                conversation.setStatus("COMPLETED");
                aria(conversation, "duplicate", "Good news: you're already registered for this event. "
                        + "No need to do anything else. See you there.");
            } else {
                aria(conversation, "error", "Hmm, something didn't add up: "
                        + (ex.getReason() != null ? ex.getReason() : "please try again."));
            }
        }
        persist(conversation, state);
        return turn(conversation, state);
    }

    // ── conversation mechanics ──────────────────────────────────────────────

    /** The first visible, answerable, still-unanswered question — rules decide. */
    private FormLogicService.Field nextUnanswered(State state) {
        // The event's own form, with its School question listing the schools: the same one the web form shows.
        String schema = campusService.registrationForm(state.eventId).schema();
        return formLogic.visibleAnswerableFields(schema, state.answers).stream()
                .filter(f -> !state.answers.containsKey(f.id()))
                .findFirst().orElse(null);
    }

    private String prompt(FormLogicService.Field f) {
        if (!f.options().isEmpty()) {
            StringBuilder sb = new StringBuilder(f.label()).append(" — pick one");
            if (f.type().equals("multi")) {
                sb = new StringBuilder(f.label()).append(" — pick any that apply (comma-separated)");
            }
            sb.append(":");
            int i = 1;
            for (String o : f.options()) {
                sb.append("\n").append(i++).append(". ").append(o);
            }
            return sb.toString();
        }
        if (f.type().equals("yesno")) {
            return f.label() + " (Yes / No)";
        }
        return f.label() + (f.required() ? "" : " (or say \"skip\")");
    }

    /**
     * Turn free text into a stored answer. Returns null when the reply cannot be
     * matched to an option (caller re-asks); blank string means skipped.
     */
    private String interpret(FormLogicService.Field f, String text) {
        String t = text == null ? "" : text.trim();
        if (t.equalsIgnoreCase("skip") && !f.required()) {
            return "";
        }
        if (f.type().equals("yesno")) {
            if (t.toLowerCase(Locale.ROOT).startsWith("y")) return "Yes";
            if (t.toLowerCase(Locale.ROOT).startsWith("n")) return "No";
            return null;
        }
        if (!f.options().isEmpty()) {
            if (f.type().equals("multi")) {
                List<String> picked = new ArrayList<>();
                for (String part : t.split(",")) {
                    String match = matchOption(f.options(), part.trim());
                    if (match != null && !picked.contains(match)) {
                        picked.add(match);
                    }
                }
                return picked.isEmpty() ? null : String.join(", ", picked);
            }
            return matchOption(f.options(), t);
        }
        return t;
    }

    /** Match by number ("2") or case-insensitive text/prefix. */
    private String matchOption(List<String> options, String input) {
        try {
            int idx = Integer.parseInt(input.trim());
            if (idx >= 1 && idx <= options.size()) {
                return options.get(idx - 1);
            }
        } catch (NumberFormatException ignored) { }
        String needle = input.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return null;
        }
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).equals(needle)
                        || o.toLowerCase(Locale.ROOT).startsWith(needle)
                        || o.toLowerCase(Locale.ROOT).contains(needle))
                .findFirst().orElse(null);
    }

    private String answersJson(State state) {
        String schema = campusService.registrationForm(state.eventId).schema();
        var arr = objectMapper.createArrayNode();
        for (FormLogicService.Field f : formLogic.fields(schema)) {
            String v = state.answers.get(f.id());
            if (v != null && !v.isBlank()) {
                arr.add(objectMapper.createObjectNode()
                        .put("fieldId", f.id()).put("label", f.label()).put("value", v));
            }
        }
        return arr.toString();
    }

    private void aria(Conversation c, String intent, String body) {
        Message m = new Message();
        m.setConversationId(c.getId());
        m.setSender("ARIA");
        m.setIntent("evreg." + intent);
        m.setBody(body);
        messages.save(m);
    }

    private void student(Conversation c, String body) {
        Message m = new Message();
        m.setConversationId(c.getId());
        m.setSender("CANDIDATE");
        m.setIntent("evreg.answer");
        m.setBody(body);
        messages.save(m);
    }

    private Turn turn(Conversation conversation, State state) {
        List<MessageView> transcript = messages.findByConversationIdOrderByCreatedAt(conversation.getId()).stream()
                .map(m -> new MessageView(m.getSender(), m.getBody()))
                .toList();
        FormLogicService.Field current = state.done ? null : nextUnanswered(state);
        List<String> options = current == null
                ? (!state.done && state.consentAsked && !state.consented ? List.of("Yes", "No") : List.of())
                : current.type().equals("yesno") ? List.of("Yes", "No") : current.options();
        return new Turn(conversation.getId(), state.done, transcript, options);
    }

    private State readState(Conversation c) {
        try {
            return objectMapper.readValue(c.getContext(), State.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Corrupt registration-chat context", ex);
        }
    }

    private void persist(Conversation c, State state) {
        try {
            c.setContext(objectMapper.writeValueAsString(state));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize registration-chat context", ex);
        }
        conversations.save(c);
    }

    private static String firstName(String name) {
        int sp = name == null ? -1 : name.indexOf(' ');
        return sp > 0 ? name.substring(0, sp) : (name == null ? "there" : name);
    }
}
