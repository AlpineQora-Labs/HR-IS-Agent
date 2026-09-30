package com.taportal.aria;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.taportal.domain.interview.Interview;
import com.taportal.domain.interview.InterviewSlot;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.KnockoutQuestion;
import java.util.List;

/**
 * The assistant's wording from Claude. Generates contextual phrasing via the
 * Anthropic API; on any error (or empty output) it falls back to
 * {@link ScriptedBrain} so the funnel never breaks.
 *
 * <p>Screening questions are asked verbatim (delegated to scripted) for
 * compliance fidelity; the interview slot list is rendered deterministically so
 * the numbers candidates reply with always line up.
 */
public class ClaudeBrain implements AssistantBrain {

    private static final String SYSTEM =
            "You are %s, the careers assistant for Bank of America Careers. "
                    + "Speak in the first person, in 1-2 short plain sentences. "
                    + "No markdown, no headings, no emoji, no exclamation marks. "
                    + "Output only the message to the candidate, nothing else.";

    private final AnthropicClient client;
    private final String model;
    private final ScriptedBrain fallback;

    private final String system;

    public ClaudeBrain(AnthropicClient client, String model, ScriptedBrain fallback, String name) {
        this.client = client;
        this.model = model;
        this.fallback = fallback;
        this.system = SYSTEM.formatted(name == null || name.isBlank() ? "Erica" : name.trim());
    }

    private String complete(String userPrompt, long maxTokens) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .addUserMessage(userPrompt)
                .build();
        Message resp = client.messages().create(params);
        String text = resp.content().stream()
                .flatMap(b -> b.text().stream())
                .map(t -> t.text())
                .reduce("", String::concat)
                .trim();
        if (text.isBlank()) {
            throw new IllegalStateException("empty completion");
        }
        return text;
    }

    @Override
    public String greeting(Job job, String language) {
        String title = job != null ? job.getTitle() : "this role";
        try {
            return complete(
                    "Greet a candidate who is interested in the \"" + title + "\" role, briefly say "
                            + "you'll ask a few quick questions to start their application, and end by asking "
                            + "for their full name. Reply language: " + (language == null ? "en" : language) + ".",
                    220);
        } catch (RuntimeException ex) {
            return fallback.greeting(job, language);
        }
    }

    @Override
    public String ask(String step, Job job, KnockoutQuestion question) {
        // Utility prompts and (critically) verbatim screening questions stay scripted.
        return fallback.ask(step, job, question);
    }

    @Override
    public String acknowledge(String step, String userText) {
        try {
            return complete(
                    "The candidate just replied at the \"" + step + "\" step with: \""
                            + safe(userText) + "\". Give a brief, plain one-line acknowledgement. "
                            + "Do not ask a new question.",
                    100);
        } catch (RuntimeException ex) {
            return fallback.acknowledge(step, userText);
        }
    }

    @Override
    public String decline(Job job) {
        try {
            return complete(
                    "Kindly let the candidate know that, based on their screening answers, this role "
                            + "isn't the right match right now, and wish them well in their search. "
                            + "Do not reveal which answer disqualified them.",
                    180);
        } catch (RuntimeException ex) {
            return fallback.decline(job);
        }
    }

    @Override
    public String schedulePrompt(List<InterviewSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            try {
                return complete(
                        "Congratulate the candidate on passing the screening and let them know "
                                + "the team will reach out shortly to set up a quick conversation.",
                        150);
            } catch (RuntimeException ex) {
                return fallback.schedulePrompt(slots);
            }
        }
        // Keep the numbered slot list deterministic so reply-by-number stays reliable.
        try {
            String leadIn = complete(
                    "Congratulate the candidate on passing screening and invite them to pick a time for "
                            + "their interview from a list you're about to show. One or two sentences; "
                            + "do not invent any specific times.",
                    120);
            StringBuilder sb = new StringBuilder(leadIn);
            int i = 1;
            for (InterviewSlot slot : slots) {
                sb.append("\n").append(i++).append(". ").append(ScriptedBrain.formatSlot(slot));
            }
            return sb.toString();
        } catch (RuntimeException ex) {
            return fallback.schedulePrompt(slots);
        }
    }

    @Override
    public String confirmation(Job job, Interview interview) {
        String when = interview != null && interview.getScheduledAt() != null
                ? ScriptedBrain.formatWhen(interview.getScheduledAt())
                : "your selected time";
        try {
            return complete(
                    "Confirm that the candidate's interview is booked for " + when + ". "
                            + "State the date and time exactly as written, including ET. "
                            + "Tell them a confirmation with details will follow, and that the team looks "
                            + "forward to speaking with them.",
                    160);
        } catch (RuntimeException ex) {
            return fallback.confirmation(job, interview);
        }
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        // Keep the prompt single-line and bounded.
        String t = s.replace("\n", " ").trim();
        return t.length() > 280 ? t.substring(0, 280) : t;
    }
}
