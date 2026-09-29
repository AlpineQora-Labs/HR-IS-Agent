package com.taportal.aria.sms;

import com.taportal.aria.sms.SmsIntent.Kind;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what a text message asks for. Deterministic and pure: the same text
 * always gives the same answer, with nothing looked up.
 *
 * <p>Two rules matter more than the rest:
 * <ul>
 *   <li>The words carriers treat as opting out are treated as opting out here,
 *       so the portal and the carrier never disagree about who may be texted.
 *       That includes CANCEL: a candidate who meant their interview is told
 *       how to change it in the reply.
 *   <li>A number books a time only when it is the whole message. "Can we do
 *       2pm instead" asks for something else, and books nothing.
 * </ul>
 */
public final class SmsIntentParser {

    /** Opt-out when one of these is the whole message. */
    private static final Set<String> STOP_WORDS = Set.of(
            "stop", "stopall", "stop all", "unsubscribe", "cancel", "end", "quit", "revoke", "optout", "opt out");
    /** "Stop please", "stop texting me": STOP said in a sentence, and meant. */
    private static final Pattern STOP_FIRST = Pattern.compile("^stop\\b(?!.*\\breschedul)");
    private static final Set<String> START_WORDS = Set.of("start", "unstop", "subscribe");
    private static final Set<String> THANKS_WORDS = Set.of(
            "thanks", "thank you", "thankyou", "thx", "ty", "ok", "okay", "k", "yes", "y", "yep", "got it",
            "great", "perfect", "sounds good", "see you then", "will do", "confirmed", "confirm", "c");
    private static final Set<String> HELP_WORDS = Set.of("help", "info", "?");
    private static final Set<String> TIMES_WORDS = Set.of("times", "time", "slots", "options", "schedule");

    /** "2", "#2", "option 2", "number 2" — and nothing else in the message. */
    private static final Pattern PICK = Pattern.compile("^(?:option|number|no\\.?|choice|#)?\\s*([1-9])$");

    private static final Pattern STATUS = Pattern.compile("\\bstatus\\b|^(?:any )?updates?$|\\bany (?:news|update)s?\\b");

    /** The phrases web chat already understands as asking to move an interview. */
    private static final Pattern RESCHEDULE = Pattern.compile(
            "reschedul|can'?t make|cannot make|change (?:the |my )?(?:time|interview)"
                    + "|move (?:the |my )?interview|different time|new time|another time");

    private static final Pattern MORE = Pattern.compile(
            "^more$|^none$|none of (?:these|them|those)|(?:other|more|different) (?:times|options|days?)"
                    + "|(?:don'?t|do not|doesn'?t|does not|won'?t) work|not available");

    private SmsIntentParser() {
    }

    public static SmsIntent parse(String text) {
        String t = tidy(text);
        if (t.isEmpty()) {
            return SmsIntent.of(Kind.UNCLEAR);
        }
        if (STOP_WORDS.contains(t) || STOP_FIRST.matcher(t).find()) {
            return SmsIntent.of(Kind.OPT_OUT);
        }
        if (START_WORDS.contains(t)) {
            return SmsIntent.of(Kind.OPT_IN);
        }
        if (HELP_WORDS.contains(t)) {
            return SmsIntent.of(Kind.HELP);
        }
        Matcher pick = PICK.matcher(t);
        if (pick.matches()) {
            return SmsIntent.pick(Integer.parseInt(pick.group(1)));
        }
        if (RESCHEDULE.matcher(t).find()) {
            return SmsIntent.of(Kind.RESCHEDULE);
        }
        if (MORE.matcher(t).find()) {
            return SmsIntent.of(Kind.MORE);
        }
        if (STATUS.matcher(t).find()) {
            return SmsIntent.of(Kind.STATUS);
        }
        if (TIMES_WORDS.contains(t)) {
            return SmsIntent.of(Kind.TIMES);
        }
        if (THANKS_WORDS.contains(t) || t.startsWith("thanks") || t.startsWith("thank you")) {
            return SmsIntent.of(Kind.THANKS);
        }
        return SmsIntent.of(Kind.UNCLEAR);
    }

    /** Lower case, one space between words, no punctuation at the end. */
    private static String tidy(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim().toLowerCase().replace('’', '\'').replaceAll("\\s+", " ");
        if (t.equals("?")) {
            return t;
        }
        return t.replaceAll("[.!?,;:]+$", "").trim();
    }
}
