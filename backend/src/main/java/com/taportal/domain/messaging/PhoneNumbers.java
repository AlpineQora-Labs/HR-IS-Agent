package com.taportal.domain.messaging;

import java.util.regex.Pattern;

/**
 * Phone numbers in one form — E.164, for example {@code +17045550101} — so a
 * number typed by a candidate and the number a text arrives from can be
 * compared. Pure: no state, no lookups.
 */
public final class PhoneNumbers {

    /** Letters or symbols a phone number never contains. Spaces, dots, dashes and brackets are allowed. */
    private static final Pattern NOT_A_NUMBER = Pattern.compile("[^0-9\\s().+\\-]");

    /** "x123", "ext 5", "#22" at the end: an extension, which a text can't reach. */
    private static final Pattern EXTENSION = Pattern.compile("(?i)\\s*(?:x|ext\\.?|#)\\s*\\d+\\s*$");

    private PhoneNumbers() {
    }

    /**
     * @return the number in E.164, or {@code null} when the text is not a phone
     *         number a message could be sent to. A ten-digit number with no
     *         country code is taken to be North American.
     */
    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String number = EXTENSION.matcher(raw.trim()).replaceFirst("");
        if (NOT_A_NUMBER.matcher(number).find()) {
            return null;
        }
        String digits = number.replaceAll("\\D", "");
        if (number.startsWith("+")) {
            return digits.length() >= 8 && digits.length() <= 15 && digits.charAt(0) != '0' ? "+" + digits : null;
        }
        if (digits.length() == 10 && digits.charAt(0) >= '2') {
            return "+1" + digits;
        }
        if (digits.length() == 11 && digits.charAt(0) == '1' && digits.charAt(1) >= '2') {
            return "+" + digits;
        }
        return null;
    }

    /** The number with all but its last four digits hidden, for logs and staff alerts. */
    public static String masked(String e164) {
        if (e164 == null || e164.length() < 6) {
            return "a number";
        }
        return "the number ending " + e164.substring(e164.length() - 4);
    }
}
