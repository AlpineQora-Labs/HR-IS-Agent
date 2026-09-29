package com.taportal.aria;

import com.taportal.domain.job.KnockoutQuestion;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline, heuristic {@link AnswerInterpreter}. Preserves the engine's original
 * literal parsing: profile fields are taken as-typed, knockout answers are
 * passed through (the rule evaluator normalizes booleans/numbers itself), and a
 * slot is matched by the time as it was offered, or by its number when the
 * number is the whole reply.
 */
public class ScriptedInterpreter implements AnswerInterpreter {

    /** "2", "option 2", "#2": a number and nothing else. */
    private static final Pattern NUMBER_ALONE =
            Pattern.compile("^(?:option|number|no\\.?|choice|#)?\\s*([1-9])$", Pattern.CASE_INSENSITIVE);

    @Override
    public String extractField(String fieldType, String userText) {
        return userText == null ? "" : userText.trim();
    }

    @Override
    public String normalizeAnswer(KnockoutQuestion question, String userText) {
        return userText == null ? "" : userText.trim();
    }

    @Override
    public int chooseSlot(List<String> slotLabels, String userText) {
        if (slotLabels == null || slotLabels.isEmpty() || userText == null) {
            return 0;
        }
        String trimmed = userText.trim();
        // The time itself, as offered: what arrives when the candidate presses it.
        for (int i = 0; i < slotLabels.size(); i++) {
            if (slotLabels.get(i).equalsIgnoreCase(trimmed)) {
                return i + 1;
            }
        }
        // A number picks a time only when it is the whole reply: "Oct 1 at 12:30 PM" names a
        // time, it does not ask for the first one.
        Matcher m = NUMBER_ALONE.matcher(trimmed);
        if (m.matches()) {
            int idx = Integer.parseInt(m.group(1));
            return idx <= slotLabels.size() ? idx : 0;
        }
        // Part of a time ("12:30 PM") when it can only mean one of them.
        if (trimmed.length() >= 4) {
            String said = trimmed.toLowerCase();
            int found = 0;
            for (int i = 0; i < slotLabels.size(); i++) {
                if (slotLabels.get(i).toLowerCase().contains(said)) {
                    if (found != 0) {
                        return 0;
                    }
                    found = i + 1;
                }
            }
            return found;
        }
        return 0;
    }
}
