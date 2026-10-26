package com.taportal.aria;

import com.taportal.domain.briefing.Briefing;
import com.taportal.domain.briefing.Briefing.Why;
import java.util.List;
import java.util.Locale;

/**
 * The recruiter assistant without a model: the briefing laid out as it is. It always works,
 * offline and with no key, and it is what the model's wording falls back to.
 *
 * <p>A typed question is matched on a few words only. A question about why something is stuck
 * gets the reasons; a question asking for a summary gets the briefing; anything else is told
 * what this version can do, rather than guessed at.
 */
public class ScriptedRecruiterBrain implements RecruiterBrain {

    private static final List<String> ASKS_WHY = List.of(
            "why", "stuck", "waiting", "held", "not sent", "didn't", "did not", "hasn't", "has not", "blocked", "late");
    private static final List<String> ASKS_BRIEF = List.of(
            "brief", "summar", "status", "where", "overview", "what's", "what is", "tell me", "update", "today");

    @Override
    public Answer answer(String intent, String question, Briefing briefing, List<Turn> history) {
        String i = ASK.equals(intent) ? route(question) : intent;
        if (WHY.equals(i)) {
            return new Answer(why(briefing), false);
        }
        if (BRIEF.equals(i)) {
            return new Answer(brief(briefing), false);
        }
        return new Answer("Without the model I can only lay out the records for " + briefing.about()
                + ". Ask me to brief you, or to explain why something is waiting.", false);
    }

    /** "Brief me": the reasons first when there are any, then the sections. */
    static String brief(Briefing b) {
        StringBuilder s = new StringBuilder("Here is where things stand for ").append(b.about()).append(".");
        if (!b.whys().isEmpty()) {
            s.append(" ").append(b.whys().size() == 1 ? "One item needs a look" : b.whys().size() + " items need a look")
                    .append("; ask me to explain why for the reasons.");
        }
        s.append("\n").append(BriefingText.sections(b));
        return s.toString().trim();
    }

    /** "Explain why": each reason on its own line, or that nothing is waiting. */
    static String why(Briefing b) {
        if (b.whys().isEmpty()) {
            return "Nothing for " + b.about() + " is waiting, held back or not sent.";
        }
        StringBuilder s = new StringBuilder("For ").append(b.about()).append(":\n");
        for (Why w : b.whys()) {
            s.append("- ").append(BriefingText.why(w)).append("\n");
        }
        if (!b.gaps().isEmpty() && b.gaps().get(0).contains("left out")) {
            s.append(b.gaps().get(0));
        }
        return s.toString().trim();
    }

    private static String route(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (ASKS_WHY.stream().anyMatch(q::contains)) {
            return WHY;
        }
        if (ASKS_BRIEF.stream().anyMatch(q::contains)) {
            return BRIEF;
        }
        return ASK;
    }
}
