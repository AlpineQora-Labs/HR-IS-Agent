package com.taportal.domain.messaging;

/**
 * Keeps a text message to the characters every phone can show in a
 * 160-character message. One curly quote or long dash turns the whole
 * message into 70-character parts; an emoji does the same. Pure.
 */
public final class SmsText {

    /** The basic set every handset has, and the few extras that cost two characters. */
    private static final String BASIC =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ"
                    + "ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
                    + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
                    + "¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String EXTENDED = "^{}\\[~]|€";

    private SmsText() {
    }

    /** The text with look-alike characters made plain and anything a phone may not show removed. */
    public static String plain(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            switch (cp) {
                case 0x2018, 0x2019, 0x201A, 0x2032 -> sb.append('\'');
                case 0x201C, 0x201D, 0x201E, 0x2033 -> sb.append('"');
                case 0x2013, 0x2014, 0x2212 -> sb.append('-');
                case 0x2026 -> sb.append("...");
                case 0x00A0, 0x2009, 0x202F -> sb.append(' ');
                case 0x2022 -> sb.append('-');
                default -> {
                    if (cp < 0x10000 && (BASIC.indexOf(cp) >= 0 || EXTENDED.indexOf(cp) >= 0)) {
                        sb.append((char) cp);
                    } else {
                        sb.append(withoutMarks(cp));
                    }
                }
            }
        });
        return sb.toString().replaceAll("[ \\t]+\\n", "\n").replaceAll(" {2,}", " ").trim();
    }

    /**
     * A letter the basic set lacks, as the letter it is built on: í as i, ç as
     * c, ë as e. A name loses its accent, not its letter. What has no plain
     * form — an emoji, a letter of another script — is left out.
     */
    private static String withoutMarks(int codePoint) {
        String letter = switch (codePoint) {
            case 0x00DF -> "ss";            // ß is in the basic set; kept here for its capital
            case 0x1E9E -> "SS";
            case 0x0141 -> "L";
            case 0x0142 -> "l";
            case 0x0110 -> "D";
            case 0x0111 -> "d";
            case 0x0152 -> "OE";
            case 0x0153 -> "oe";
            default -> java.text.Normalizer.normalize(
                    new String(Character.toChars(codePoint)), java.text.Normalizer.Form.NFD);
        };
        StringBuilder plain = new StringBuilder();
        letter.codePoints().forEach(c -> {
            if (c < 0x80 && BASIC.indexOf(c) >= 0) {
                plain.append((char) c);
            }
        });
        return plain.toString();
    }

    /** How many messages a carrier would bill this as. */
    public static int parts(String text) {
        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            length += EXTENDED.indexOf(text.charAt(i)) >= 0 ? 2 : 1;
        }
        return length <= 160 ? 1 : (length + 152) / 153;
    }
}
