package com.taportal.domain.messaging;

import java.util.regex.Pattern;

/**
 * Whether a candidate who gave a number, asked for one to text, also said it
 * is not to be texted. Giving a number in answer to "what number can we text"
 * is agreement; "call me on 704 555 0101 but please don't text" is not. Pure.
 */
public final class TextAgreement {

    private static final Pattern REFUSES = Pattern.compile(
            "\\b(?:do not|don't|dont|no|not|never|rather not|prefer not to)"
                    + " (?:be )?(?:text|texts|texted|texting|sms|message|messages|messaged)\\b"
                    + "|\\b(?:call|calls|phone calls?|voice|email|emails) only\\b"
                    + "|\\bonly (?:call|calls|phone|email)\\b"
                    + "|\\bland ?line\\b"
                    + "|\\b(?:can't|cant|cannot|can not) (?:get|receive|take) (?:texts|text messages|sms)\\b",
            Pattern.CASE_INSENSITIVE);

    private TextAgreement() {
    }

    /**
     * @param answer what the candidate wrote when asked for a number to text
     * @return true when the answer says texts are not wanted
     */
    public static boolean refused(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String said = answer.replace('\u2019', '\'').replaceAll("\\s+", " ");
        return REFUSES.matcher(said).find();
    }
}
