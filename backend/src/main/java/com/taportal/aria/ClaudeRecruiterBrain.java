package com.taportal.aria;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.taportal.domain.briefing.Briefing;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The recruiter assistant's wording from Claude, grounded only on the {@link Briefing}.
 *
 * <p>What the model is given: the briefing's text (placeholders, never names or contact details),
 * the recruiter's question, and the earlier turns of the chat. It has no tools and cannot change
 * anything. It is told to answer only from the records and to say when something is not in them.
 *
 * <p>On any failure the answer falls back to {@link ScriptedRecruiterBrain}, so the widget always
 * answers: an API error or timeout, a refusal, an answer cut off at the token limit, or an empty one.
 *
 * <p>Notes on the request:
 * <ul>
 *   <li>Effort is LOW: the facts are already chosen and ordered, so the model only has to word them,
 *       and a recruiter waiting on a chat bubble feels every second.</li>
 *   <li>The system prompt is not marked for prompt caching: it is far below the minimum length a
 *       cache entry needs, so the mark would do nothing.</li>
 *   <li>A refusal falls back to the rules' wording rather than to another model: version 2.34.0 of
 *       the Java SDK has no typed setting for the API's server-side fallback.</li>
 * </ul>
 */
public class ClaudeRecruiterBrain implements RecruiterBrain {

    private static final Logger log = LoggerFactory.getLogger(ClaudeRecruiterBrain.class);

    /** Room for adaptive thinking plus a short answer; an answer that hits it falls back. */
    private static final long MAX_TOKENS = 8000L;

    static final String SYSTEM = """
            You answer a recruiter's questions inside the Interview Portal, the recruiting system a bank's \
            hiring teams use. You can only read: you cannot change, send, book or schedule anything, and you \
            never offer to.

            Answer only from the records in the recruiter's message. If something is not in them, say it is \
            not recorded here, and name a screen to open if the records list one. Never guess, estimate, or \
            add a number, date or name that is not in the records.

            People are named by placeholders: [C1] is a candidate, [S1] is someone on the hiring team. Write \
            each placeholder exactly as given; the screen puts the name back. Never invent a name.

            Write for a busy recruiter. Lead with the answer, then the detail. Plain sentences in a calm, \
            professional voice. A short list is fine, one item per line starting with "- ". No headings, bold, \
            tables, emoji or exclamation marks. Stay under 120 words unless the recruiter asks for more.

            Asked for a briefing: say first what needs attention (waiting, overdue, held back, not sent), \
            then where things stand.
            Asked why: for each item under "Why things are as they are", say what, why, and the next step \
            when one is given. Do not add next steps of your own.""";

    private final AnthropicClient client;
    private final String model;
    private final ScriptedRecruiterBrain fallback;

    public ClaudeRecruiterBrain(AnthropicClient client, String model, ScriptedRecruiterBrain fallback) {
        this.client = client;
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public Answer answer(String intent, String question, Briefing briefing, List<Turn> history) {
        try {
            Message resp = client.messages().create(params(intent, question, briefing, history));
            StopReason stop = resp.stopReason().orElse(null);
            if (StopReason.REFUSAL.equals(stop) || StopReason.MAX_TOKENS.equals(stop)) {
                log.warn("Recruiter assistant: model stopped with {}, using the rules' wording", stop);
                return fallback.answer(intent, question, briefing, history);
            }
            String text = resp.content().stream()
                    .flatMap(b -> b.text().stream())
                    .map(t -> t.text())
                    .reduce("", String::concat)
                    .trim();
            if (text.isBlank()) {
                log.warn("Recruiter assistant: model returned no text, using the rules' wording");
                return fallback.answer(intent, question, briefing, history);
            }
            return new Answer(text, true);
        } catch (AnthropicException | IllegalStateException ex) {
            // The message is logged, never the request: it holds candidate records.
            log.warn("Recruiter assistant: model call failed ({}), using the rules' wording", ex.getClass().getSimpleName());
            return fallback.answer(intent, question, briefing, history);
        }
    }

    /** The request: the fixed system prompt, the earlier turns, then the records and the ask. */
    MessageCreateParams params(String intent, String question, Briefing briefing, List<Turn> history) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        for (Turn t : alternating(history)) {
            if (t.fromRecruiter()) {
                b.addUserMessage(t.text());
            } else {
                b.addAssistantMessage(t.text());
            }
        }
        b.addUserMessage("Records:\n" + BriefingText.records(briefing) + "\n\nRecruiter: " + ask(intent, question));
        return b.build();
    }

    private static String ask(String intent, String question) {
        if (BRIEF.equals(intent)) {
            return "Brief me on this.";
        }
        if (WHY.equals(intent)) {
            return "Explain why things are as they are: what is waiting, held back or not sent, and why.";
        }
        return question == null ? "" : question;
    }

    /**
     * The history as the API takes it: starting with the recruiter, alternating, and ending with the
     * assistant so the new question follows. Turns that break the pattern (a greeting shown before the
     * recruiter spoke, two of the same side in a row) are dropped, keeping the latest of a run.
     */
    static List<Turn> alternating(List<Turn> history) {
        List<Turn> out = new ArrayList<>();
        for (Turn t : history == null ? List.<Turn>of() : history) {
            if (t == null || t.text() == null || t.text().isBlank()) {
                continue;
            }
            if (out.isEmpty()) {
                if (t.fromRecruiter()) {
                    out.add(t);
                }
            } else if (out.get(out.size() - 1).fromRecruiter() == t.fromRecruiter()) {
                out.set(out.size() - 1, t);
            } else {
                out.add(t);
            }
        }
        if (!out.isEmpty() && out.get(out.size() - 1).fromRecruiter()) {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
