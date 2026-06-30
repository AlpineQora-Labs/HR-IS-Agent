package com.olivia.domain.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olivia.domain.application.Application;
import com.olivia.domain.application.ApplicationRepository;
import com.olivia.domain.application.ScreeningAnswer;
import com.olivia.domain.application.ScreeningAnswerRepository;
import com.olivia.domain.candidate.Candidate;
import com.olivia.domain.candidate.CandidateRepository;
import com.olivia.domain.interview.Interview;
import com.olivia.domain.interview.InterviewRepository;
import com.olivia.domain.interview.InterviewSlot;
import com.olivia.domain.interview.InterviewSlotRepository;
import com.olivia.domain.job.Job;
import com.olivia.domain.job.JobRepository;
import com.olivia.domain.job.KnockoutQuestion;
import com.olivia.domain.job.KnockoutQuestionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The Olivia state machine. Deterministic, scripted, but LLM-ready (all copy is
 * produced by the {@link AssistantBrain} seam). State is persisted as JSON on
 * {@code conversation.context}.
 *
 * <p>Flow:
 * GREETING → COLLECT_NAME → COLLECT_EMAIL → COLLECT_PHONE → KNOCKOUT (iterate
 * the job's knockout questions, evaluating each disqualify rule) →
 * [pass] OFFER_SCHEDULE → DONE | [fail] DECLINED.
 */
@Service
public class ConversationEngine {

    public static final String STEP_GREETING = "GREETING";
    public static final String STEP_COLLECT_NAME = "COLLECT_NAME";
    public static final String STEP_COLLECT_EMAIL = "COLLECT_EMAIL";
    public static final String STEP_COLLECT_PHONE = "COLLECT_PHONE";
    public static final String STEP_KNOCKOUT = "KNOCKOUT";
    public static final String STEP_OFFER_SCHEDULE = "OFFER_SCHEDULE";
    public static final String STEP_DONE = "DONE";
    public static final String STEP_DECLINED = "DECLINED";

    private final AssistantBrain brain;
    private final AnswerInterpreter interpreter;
    private final ObjectMapper objectMapper;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final CandidateRepository candidates;
    private final ApplicationRepository applications;
    private final ScreeningAnswerRepository screeningAnswers;
    private final JobRepository jobs;
    private final KnockoutQuestionRepository knockoutQuestions;
    private final InterviewSlotRepository slots;
    private final InterviewRepository interviews;

    public ConversationEngine(
            AssistantBrain brain,
            AnswerInterpreter interpreter,
            ObjectMapper objectMapper,
            ConversationRepository conversations,
            MessageRepository messages,
            CandidateRepository candidates,
            ApplicationRepository applications,
            ScreeningAnswerRepository screeningAnswers,
            JobRepository jobs,
            KnockoutQuestionRepository knockoutQuestions,
            InterviewSlotRepository slots,
            InterviewRepository interviews) {
        this.brain = brain;
        this.interpreter = interpreter;
        this.objectMapper = objectMapper;
        this.conversations = conversations;
        this.messages = messages;
        this.candidates = candidates;
        this.applications = applications;
        this.screeningAnswers = screeningAnswers;
        this.jobs = jobs;
        this.knockoutQuestions = knockoutQuestions;
        this.slots = slots;
        this.interviews = interviews;
    }

    // ---- mutable engine state, serialized to conversation.context ----
    static final class State {
        public String step = STEP_GREETING;
        public String name;
        public String email;
        public String phone;
        public int knockoutIndex; // index into the ordered knockout questions
    }

    /** Result of advancing the engine: the latest Olivia messages + quick-reply options. */
    public record Turn(List<Message> messages, List<String> options) {}

    // =====================================================================
    // Public API
    // =====================================================================

    /** Begin a conversation: emit greeting and the first prompt. */
    public Turn start(Conversation conversation) {
        State state = new State();
        Job job = loadJob(conversation.getJobId());

        List<Message> emitted = new ArrayList<>();
        emitted.add(record(conversation, STEP_GREETING, brain.greeting(job, conversation.getLanguage())));

        // The greeting already asks for the name, so advance straight to COLLECT_NAME.
        state.step = STEP_COLLECT_NAME;
        persist(conversation, state);
        return new Turn(emitted, optionsFor(state, job));
    }

    /** Process a candidate reply and advance the machine. */
    public Turn reply(Conversation conversation, String text) {
        State state = readState(conversation);
        Job job = loadJob(conversation.getJobId());

        // Record the candidate's message first.
        recordCandidate(conversation, state.step, text);

        List<Message> emitted = new ArrayList<>();
        switch (state.step) {
            case STEP_COLLECT_NAME -> {
                state.name = interpreter.extractField("NAME", text);
                emitted.add(record(conversation, STEP_COLLECT_NAME,
                        brain.acknowledge(STEP_COLLECT_NAME, state.name)));
                state.step = STEP_COLLECT_EMAIL;
                emitted.add(record(conversation, STEP_COLLECT_EMAIL,
                        brain.ask(STEP_COLLECT_EMAIL, job, null)));
            }
            case STEP_COLLECT_EMAIL -> {
                state.email = interpreter.extractField("EMAIL", text);
                state.step = STEP_COLLECT_PHONE;
                emitted.add(record(conversation, STEP_COLLECT_PHONE,
                        brain.ask(STEP_COLLECT_PHONE, job, null)));
            }
            case STEP_COLLECT_PHONE -> {
                state.phone = interpreter.extractField("PHONE", text);
                // Profile complete: create candidate + application.
                createCandidateAndApplication(conversation, state, job);
                emitted.add(record(conversation, STEP_COLLECT_PHONE,
                        brain.acknowledge(STEP_COLLECT_PHONE, text)));
                enterKnockout(conversation, state, job, emitted);
            }
            case STEP_KNOCKOUT -> handleKnockoutAnswer(conversation, state, job, text, emitted);
            case STEP_OFFER_SCHEDULE -> handleScheduleChoice(conversation, state, job, text, emitted);
            default -> emitted.add(record(conversation, state.step,
                    "This conversation has already wrapped up. Thanks again!"));
        }

        persist(conversation, state);
        return new Turn(emitted, optionsFor(state, job));
    }

    // =====================================================================
    // Step handlers
    // =====================================================================

    private void enterKnockout(Conversation conversation, State state, Job job, List<Message> emitted) {
        List<KnockoutQuestion> qs = knockoutQuestions.findByJobIdOrderByOrdinal(job.getId());
        if (qs.isEmpty()) {
            // No screening — pass straight through to scheduling.
            markScreened(conversation);
            offerSchedule(conversation, state, job, emitted);
            return;
        }
        state.step = STEP_KNOCKOUT;
        state.knockoutIndex = 0;
        emitted.add(record(conversation, STEP_KNOCKOUT, brain.ask(STEP_KNOCKOUT, job, qs.get(0))));
    }

    private void handleKnockoutAnswer(
            Conversation conversation, State state, Job job, String text, List<Message> emitted) {
        List<KnockoutQuestion> qs = knockoutQuestions.findByJobIdOrderByOrdinal(job.getId());
        if (state.knockoutIndex >= qs.size()) {
            // Defensive: nothing left to ask, treat as passed.
            markScreened(conversation);
            offerSchedule(conversation, state, job, emitted);
            return;
        }
        KnockoutQuestion q = qs.get(state.knockoutIndex);
        // Understand the free-text reply, then evaluate the (unchanged) disqualify rule.
        String answer = interpreter.normalizeAnswer(q, text);
        boolean disqualified = isDisqualified(q, answer);

        // Persist the screening answer (passed = !disqualified).
        Application app = applications.findById(conversation.getApplicationId()).orElseThrow();
        ScreeningAnswer ans = new ScreeningAnswer();
        ans.setApplicationId(app.getId());
        ans.setQuestionId(q.getId());
        ans.setAnswer(answer);
        ans.setPassed(!disqualified);
        screeningAnswers.save(ans);

        if (disqualified) {
            decline(conversation, state, job, emitted);
            return;
        }

        // Advance to the next question, or pass.
        state.knockoutIndex++;
        if (state.knockoutIndex < qs.size()) {
            emitted.add(record(conversation, STEP_KNOCKOUT,
                    brain.ask(STEP_KNOCKOUT, job, qs.get(state.knockoutIndex))));
        } else {
            markScreened(conversation);
            offerSchedule(conversation, state, job, emitted);
        }
    }

    private void offerSchedule(Conversation conversation, State state, Job job, List<Message> emitted) {
        List<InterviewSlot> open = openSlots(job);
        state.step = STEP_OFFER_SCHEDULE;
        emitted.add(record(conversation, STEP_OFFER_SCHEDULE, brain.schedulePrompt(open)));
        if (open.isEmpty()) {
            // No slots to book — conversation is effectively done.
            state.step = STEP_DONE;
            conversation.setStatus("COMPLETED");
            conversations.save(conversation);
        }
    }

    private void handleScheduleChoice(
            Conversation conversation, State state, Job job, String text, List<Message> emitted) {
        List<InterviewSlot> open = openSlots(job);
        List<String> labels = new ArrayList<>();
        for (InterviewSlot slot : open) {
            labels.add(ScriptedBrain.formatSlot(slot));
        }
        int pick = interpreter.chooseSlot(labels, text);
        InterviewSlot chosen = (pick >= 1 && pick <= open.size()) ? open.get(pick - 1) : null;
        if (chosen == null) {
            emitted.add(record(conversation, STEP_OFFER_SCHEDULE,
                    "Sorry, I didn't catch which time you'd like. Please reply with the number "
                            + "(e.g. \"1\") or the date and time."));
            return;
        }

        // Book the slot, create the interview, advance the application.
        chosen.setBooked(true);
        slots.save(chosen);

        Application app = applications.findById(conversation.getApplicationId()).orElseThrow();
        Interview interview = new Interview();
        interview.setApplicationId(app.getId());
        interview.setSlotId(chosen.getId());
        interview.setType("AI_PHONE_SCREEN");
        interview.setScheduledAt(chosen.getStartsAt());
        interview.setDurationMin(30);
        interview.setStatus("SCHEDULED");
        interview = interviews.save(interview);

        app.setStage("INTERVIEW");
        applications.save(app);

        emitted.add(record(conversation, STEP_DONE, brain.confirmation(job, interview)));
        state.step = STEP_DONE;
        conversation.setStatus("COMPLETED");
        conversations.save(conversation);
    }

    private void decline(Conversation conversation, State state, Job job, List<Message> emitted) {
        Application app = applications.findById(conversation.getApplicationId()).orElseThrow();
        app.setStage("REJECTED");
        app.setKnockoutPassed(false);
        app.setRejectionReason("Did not meet knockout screening criteria");
        applications.save(app);

        emitted.add(record(conversation, STEP_DECLINED, brain.decline(job)));
        state.step = STEP_DECLINED;
        conversation.setStatus("COMPLETED");
        conversations.save(conversation);
    }

    // =====================================================================
    // Side effects
    // =====================================================================

    private void createCandidateAndApplication(Conversation conversation, State state, Job job) {
        Candidate candidate = new Candidate();
        candidate.setName(state.name != null ? state.name : "Unknown");
        candidate.setEmail(state.email != null ? state.email : "");
        candidate.setPhone(state.phone);
        candidate.setYearsExperience(BigDecimal.ZERO);
        candidate.setSource("CAREER_SITE");
        candidate.setPreferredLanguage(conversation.getLanguage());
        candidate.setLifecycle("ACTIVE");
        candidate = candidates.save(candidate);

        Application app = new Application();
        app.setCandidateId(candidate.getId());
        app.setJobId(job.getId());
        app.setStage("APPLIED");
        app.setSource(conversation.getChannel());
        app = applications.save(app);

        conversation.setCandidateId(candidate.getId());
        conversation.setApplicationId(app.getId());
        conversations.save(conversation);
    }

    private void markScreened(Conversation conversation) {
        Application app = applications.findById(conversation.getApplicationId()).orElseThrow();
        app.setStage("SCREENED");
        app.setKnockoutPassed(true);
        applications.save(app);
    }

    // =====================================================================
    // Knockout rule evaluation
    // =====================================================================

    /** Evaluate the disqualify rule (op + value) against the candidate's answer. */
    static boolean isDisqualified(KnockoutQuestion q, String rawAnswer) {
        String op = q.getDisqualifyOp();
        String ruleValue = q.getDisqualifyValue();
        if (op == null || op.isBlank() || ruleValue == null) {
            return false; // no rule → never disqualifies
        }
        String answer = rawAnswer == null ? "" : rawAnswer.trim();

        return switch (op.toUpperCase()) {
            case "EQ" -> equalsNormalized(answer, ruleValue);
            case "NEQ" -> !equalsNormalized(answer, ruleValue);
            case "NOT_IN" -> !inList(answer, ruleValue);
            case "LT", "LTE", "GT", "GTE" -> compareNumeric(op, answer, ruleValue);
            default -> false;
        };
    }

    private static boolean equalsNormalized(String answer, String value) {
        return normalizeBoolean(answer).equalsIgnoreCase(normalizeBoolean(value));
    }

    /** Map common affirmative/negative phrasings to canonical true/false. */
    private static String normalizeBoolean(String s) {
        String t = s.trim();
        String lower = t.toLowerCase();
        return switch (lower) {
            case "yes", "y", "yeah", "yep", "true", "sure" -> "true";
            case "no", "n", "nope", "false" -> "false";
            default -> t;
        };
    }

    private static boolean inList(String answer, String csv) {
        for (String part : csv.split(",")) {
            if (part.trim().equalsIgnoreCase(answer.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean compareNumeric(String op, String answer, String value) {
        Double a = parseNumber(answer);
        Double v = parseNumber(value);
        if (a == null || v == null) {
            return false; // can't compare → don't disqualify
        }
        return switch (op.toUpperCase()) {
            case "LT" -> a < v;
            case "LTE" -> a <= v;
            case "GT" -> a > v;
            case "GTE" -> a >= v;
            default -> false;
        };
    }

    private static Double parseNumber(String s) {
        if (s == null) {
            return null;
        }
        String cleaned = s.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // =====================================================================
    // Quick-reply options
    // =====================================================================

    private List<String> optionsFor(State state, Job job) {
        if (STEP_KNOCKOUT.equals(state.step)) {
            List<KnockoutQuestion> qs = knockoutQuestions.findByJobIdOrderByOrdinal(job.getId());
            if (state.knockoutIndex < qs.size()) {
                return optionsForQuestion(qs.get(state.knockoutIndex));
            }
        }
        if (STEP_OFFER_SCHEDULE.equals(state.step)) {
            List<String> opts = new ArrayList<>();
            for (InterviewSlot slot : openSlots(job)) {
                opts.add(ScriptedBrain.formatSlot(slot));
            }
            return opts;
        }
        return List.of();
    }

    private static List<String> optionsForQuestion(KnockoutQuestion q) {
        String type = q.getAnswerType() == null ? "" : q.getAnswerType().toUpperCase();
        if ("BOOLEAN".equals(type)) {
            return List.of("Yes", "No");
        }
        if ("CHOICE".equals(type) && q.getChoices() != null && !q.getChoices().isBlank()) {
            List<String> opts = new ArrayList<>();
            for (String choice : q.getChoices().split(",")) {
                if (!choice.isBlank()) {
                    opts.add(choice.trim());
                }
            }
            return opts;
        }
        return List.of();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private List<InterviewSlot> openSlots(Job job) {
        return slots.findByJobIdAndBookedFalse(job.getId());
    }

    private Job loadJob(UUID jobId) {
        if (jobId == null) {
            return null;
        }
        return jobs.findById(jobId).orElse(null);
    }

    private Message record(Conversation conversation, String step, String body) {
        Message m = new Message();
        m.setConversationId(conversation.getId());
        m.setSender("OLIVIA");
        m.setBody(body);
        m.setIntent(step);
        return messages.save(m);
    }

    private Message recordCandidate(Conversation conversation, String step, String body) {
        Message m = new Message();
        m.setConversationId(conversation.getId());
        m.setSender("CANDIDATE");
        m.setBody(body);
        m.setIntent(step);
        return messages.save(m);
    }

    // ---- state (de)serialization ----

    State readState(Conversation conversation) {
        String json = conversation.getContext();
        if (json == null || json.isBlank()) {
            State s = new State();
            s.step = STEP_GREETING;
            return s;
        }
        try {
            return objectMapper.readValue(json, State.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Corrupt conversation context", ex);
        }
    }

    private void persist(Conversation conversation, State state) {
        try {
            conversation.setContext(objectMapper.writeValueAsString(state));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize conversation context", ex);
        }
        conversations.save(conversation);
    }

    /** Expose the current step for DTO mapping without re-parsing elsewhere. */
    public String currentStep(Conversation conversation) {
        return readState(conversation).step;
    }

    /** Compute quick-reply options for the conversation's current step (for GET). */
    public List<String> optionsFor(Conversation conversation) {
        State state = readState(conversation);
        Job job = loadJob(conversation.getJobId());
        if (job == null) {
            return List.of();
        }
        return optionsFor(state, job);
    }
}
