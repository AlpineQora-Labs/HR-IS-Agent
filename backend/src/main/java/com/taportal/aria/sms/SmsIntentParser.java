package com.taportal.aria.sms;

import com.taportal.aria.sms.SmsIntent.Kind;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what a text message asks for. Deterministic and pure: the same text
 * always gives the same answer, with nothing looked up.
 *
 * <p>Three rules matter more than the rest:
 * <ul>
 *   <li>The words carriers treat as opting out are treated as opting out here,
 *       so the portal and the carrier never disagree about who may be texted.
 *       That includes CANCEL: a candidate who meant their interview is told
 *       how to change it in the reply. Opting out said in a sentence — "please
 *       stop texting me" — is opting out too.
 *   <li>A number books a time only when it is the whole message. "Can we do
 *       2pm instead" asks for something else, and books nothing.
 *   <li>Where a text can be read two ways, the reading that changes nothing
 *       wins. Only RESCHEDULE asked for plainly moves an interview; a sentence
 *       that may be asking is read as {@link Kind#RESCHEDULE_HINTED}, and
 *       "thanks for rescheduling" asks for nothing at all.
 * </ul>
 */
public final class SmsIntentParser {

    /** Opt-out when one of these is the whole message. */
    private static final Set<String> STOP_WORDS = Set.of(
            "stop", "stopall", "stop all", "unsubscribe", "cancel", "end", "quit", "revoke", "optout", "opt out");
    /** Opting out said in a sentence, and meant: "Stop please", "please stop texting me", "do not text me". */
    private static final Pattern STOP_SAID = Pattern.compile(
            "^stop\\b"
                    + "|\\b(?:stop|quit) (?:texting|messaging|sending|contacting|asking|bothering|spamming)\\b"
                    + "|\\b(?:do not|don't|dont) (?:text|message|contact) me\\b"
                    + "|\\bno more (?:texts|messages)\\b"
                    + "|\\bunsubscribe\\b|\\bremove me\\b|\\btake me off\\b|\\bopt(?:ing)? out\\b");
    private static final Set<String> START_WORDS = Set.of("start", "unstop", "subscribe");
    private static final Set<String> THANKS_WORDS = Set.of(
            "thanks", "thank you", "thankyou", "thx", "ty", "ok", "okay", "k", "yes", "y", "yep", "got it",
            "great", "perfect", "sounds good", "see you then", "will do", "confirmed", "confirm", "c");
    /** Agreement said in a sentence: nothing is being asked. */
    private static final Pattern THANKS_SAID = Pattern.compile(
            "^thanks\\b|^thank you\\b|\\bsee you (?:then|there)\\b|\\bi'?ll be there\\b"
                    + "|\\b(?:that|this|it) works\\b|\\bworks for me\\b");
    private static final Set<String> HELP_WORDS = Set.of("help", "info", "?");
    private static final Set<String> TIMES_WORDS = Set.of("times", "time", "slots", "options", "schedule");

    /** "2", "#2", "option 2", "number 2", "2 please" — and nothing else in the message. */
    private static final Pattern PICK = Pattern.compile(
            "^(?:option|number|no\\.?|choice|#)?\\s*([1-9])(?: please| pls)?$");

    private static final Pattern STATUS = Pattern.compile("\\bstatus\\b|^(?:any )?updates?$|\\bany (?:news|update)s?\\b");

    /** The word itself. "Rescheduling" and "rescheduled" speak of a move that has been made. */
    private static final Pattern RESCHEDULE_WORD = Pattern.compile("\\bre-?schedule\\b");
    /** RESCHEDULE asked for plainly: the keyword, or the keyword in a request and nothing else. */
    private static final Pattern RESCHEDULE_PLAIN = Pattern.compile(
            "^(?:please |pls )?"
                    + "(?:(?:i|we) (?:need|want|have|would like|wish) to |i'd like to |(?:can|could|may) (?:i|we|you) "
                    + "|need to |want to |help me )?"
                    + "(?:please |pls )?re-?schedule"
                    + "(?: (?:please|pls|it|this|that|my interview|the interview|my time|interview))*$");
    /** "No need to reschedule": the word is there, the request is not. */
    private static final Pattern NOT_ASKING = Pattern.compile(
            "\\b(?:no need|not need|don't need|dont need|do not need|don't have|do not have|won't need"
                    + "|no longer need|don't want|do not want|never ?mind)\\b.*\\bre-?schedul");
    /** Sentences that may be asking to move an interview. Nothing is moved on the strength of one. */
    private static final Pattern RESCHEDULE_HINT = Pattern.compile(
            "\\bre-?schedule\\b"
                    + "|\\b(?:can't|cant|cannot|can not|won't be able to|unable to|not able to)"
                    + " (?:make|attend|do) (?:it|that|this|the|any|those|these|my)\\b"
                    + "|\\b(?:change|move) (?:the |my )?(?:time|interview)\\b"
                    + "|\\b(?:need|want|like|prefer|request|get|have) (?:a |an )?(?:new|different|another) time\\b"
                    + "|\\b(?:can|could|may) (?:i|we|you) .*\\b(?:new|different|another) time\\b");

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
        if (STOP_WORDS.contains(t) || (STOP_SAID.matcher(t).find() && !RESCHEDULE_WORD.matcher(t).find())) {
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
        if (NOT_ASKING.matcher(t).find()) {
            return SmsIntent.of(Kind.THANKS);
        }
        if (RESCHEDULE_PLAIN.matcher(t).matches()) {
            return SmsIntent.of(Kind.RESCHEDULE);
        }
        // From here on, the reading that changes nothing comes first.
        if (STATUS.matcher(t).find()) {
            return SmsIntent.of(Kind.STATUS);
        }
        if (RESCHEDULE_HINT.matcher(t).find()) {
            return SmsIntent.of(Kind.RESCHEDULE_HINTED);
        }
        if (MORE.matcher(t).find()) {
            return SmsIntent.of(Kind.MORE);
        }
        if (TIMES_WORDS.contains(t)) {
            return SmsIntent.of(Kind.TIMES);
        }
        if (THANKS_WORDS.contains(t) || THANKS_SAID.matcher(t).find()) {
            return SmsIntent.of(Kind.THANKS);
        }
        return SmsIntent.of(Kind.UNCLEAR);
    }

    /** Lower case, one space between words, plain quotes, nothing wrapped around it or trailing after it. */
    private static String tidy(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim().toLowerCase()
                .replace('\u2019', '\'').replace('\u2018', '\'')
                .replace('\u201c', '"').replace('\u201d', '"')
                .replaceAll("\\s+", " ");
        if (t.equals("?")) {
            return t;
        }
        t = t.replaceAll("^[\"']+|[\"']+$", "").trim(); // STOP typed in quotation marks is still STOP
        t = t.replaceAll("[.!?,;:]+$", "").trim();
        t = t.replaceAll("^[\"']+|[\"']+$", "").trim();
        return t.replace("opt-out", "opt out");
    }
}
