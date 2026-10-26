package com.taportal.aria;

import com.taportal.domain.briefing.Briefing;
import java.util.List;

/**
 * The recruiter assistant's voice: puts a {@link Briefing} into words for a recruiter.
 *
 * <p>The seam mirrors {@link AssistantBrain} on the candidate side: a scripted implementation that
 * always works offline, and a Claude-backed one chosen in {@link AriaConfig} that falls back to it.
 * Both receive names as placeholders and return text with the placeholders still in it;
 * {@link RecruiterAssistant} puts the names back. Neither changes anything: they only write.
 */
public interface RecruiterBrain {

    /** "Brief me": what stands out, then where things stand. */
    String BRIEF = "BRIEF";
    /** "Explain why": what is waiting, held back or not done, and the reason the records give. */
    String WHY = "WHY";
    /** A question in the recruiter's own words. */
    String ASK = "ASK";

    /**
     * @param intent   {@link #BRIEF}, {@link #WHY} or {@link #ASK}
     * @param question for {@link #ASK}, the recruiter's question with names replaced by placeholders; otherwise null
     * @param briefing what the records say
     * @param history  earlier turns of this chat, oldest first, names replaced by placeholders
     * @return the answer, never null
     */
    Answer answer(String intent, String question, Briefing briefing, List<Turn> history);

    /** One earlier turn of the chat. */
    record Turn(boolean fromRecruiter, String text) {
    }

    /**
     * @param text    the answer, with placeholders
     * @param byModel true when a model wrote the wording, false when the rules did
     */
    record Answer(String text, boolean byModel) {
    }
}
