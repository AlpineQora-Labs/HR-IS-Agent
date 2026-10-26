package com.taportal.domain.briefing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** The records' codes and times in the words a recruiter reads. Times are Eastern, as on every other screen. */
final class Words {

    static final ZoneId ET = ZoneId.of("America/New_York");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.US);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    // A candidate's own words can carry contact details; these patterns take them out before a
    // briefing is built. They catch the usual shapes, not every possible one.
    private static final String EMAIL = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";
    private static final String NUMBER = "\\+?\\d[\\d\\s().-]{5,}\\d";

    private Words() {
    }

    /** "Tue Oct 21, 2:00 PM ET". */
    static String when(OffsetDateTime t) {
        return t == null ? "no time set" : WHEN.format(t.atZoneSameInstant(ET)) + " ET";
    }

    /** "Oct 21". */
    static String day(OffsetDateTime t) {
        return t == null ? "an unknown day" : DAY.format(t.atZoneSameInstant(ET));
    }

    /** "2:00 PM ET". */
    static String clock(OffsetDateTime t) {
        return t == null ? "no time set" : CLOCK.format(t.atZoneSameInstant(ET)) + " ET";
    }

    /** A span of hours as a person says it: "5 hours", "3 days". */
    static String span(long hours) {
        if (hours < 1) {
            return "less than an hour";
        }
        if (hours < 48) {
            return hours == 1 ? "1 hour" : hours + " hours";
        }
        return (hours / 24) + " days";
    }

    static String stage(String code) {
        return switch (code == null ? "" : code) {
            case "APPLIED" -> "Applied";
            case "SCREENED" -> "Screened";
            case "MATCHED" -> "Matched";
            case "INTERVIEW" -> "Interview";
            case "ASSESSMENT" -> "Assessment";
            case "OFFER" -> "Offer";
            case "HIRED" -> "Hired";
            case "REJECTED" -> "Not taken forward";
            case "WITHDRAWN" -> "Withdrawn";
            default -> plain(code);
        };
    }

    static String interviewType(String code) {
        return switch (code == null ? "" : code) {
            case "AI_PHONE_SCREEN" -> "AI phone screen";
            case "AI_INTERVIEW" -> "AI interview";
            case "PHONE_SCREEN" -> "phone screen";
            case "ONE_WAY_VIDEO" -> "one-way video interview";
            case "LIVE_VIDEO" -> "live video interview";
            case "PANEL" -> "panel interview";
            case "ONSITE" -> "onsite interview";
            default -> code == null || code.isBlank() ? "interview" : plain(code).toLowerCase(Locale.ROOT);
        };
    }

    static String interviewStatus(String code) {
        return switch (code == null ? "" : code) {
            case "REQUESTED" -> "requested, no times offered yet";
            case "SLOTS_PROPOSED" -> "times offered, none picked yet";
            case "SCHEDULED" -> "booked";
            case "COMPLETED" -> "completed";
            case "NO_SHOW" -> "marked a no-show";
            case "CANCELED" -> "cancelled";
            default -> plain(code).toLowerCase(Locale.ROOT);
        };
    }

    /** How the time was set, or null when the records do not say. */
    static String bookedVia(String code) {
        return switch (code == null ? "" : code) {
            case "CHAT" -> "the candidate picked the time in the careers chat";
            case "TEXT" -> "the candidate picked the time by text";
            case "WEB_PAGE" -> "the candidate picked the time on the scheduling page";
            case "TEAM" -> "the hiring team booked the time";
            default -> null;
        };
    }

    /** "SOME_CODE" as "Some code". */
    static String plain(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String s = code.replace('_', ' ').toLowerCase(Locale.ROOT).trim();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "6", "6.5": a number of years without trailing zeros. */
    static String years(BigDecimal y) {
        return y == null ? null : y.stripTrailingZeros().toPlainString();
    }

    /** "[S1]", "[S1] and [S2]", "[S1], [S2] and [S3]". */
    static String and(List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    /** Text with anything that looks like an email address or a phone number taken out. */
    static String scrub(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll(EMAIL, "[an email address]")
                .replaceAll(NUMBER, "[a number]")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** {@link #scrub} and cut to a line's length: for a candidate's own words. */
    static String quote(String text) {
        String s = scrub(text);
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
    }

    /** The text as a sentence: trimmed, ending in a full stop. */
    static String sentence(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            return s;
        }
        char last = s.charAt(s.length() - 1);
        return last == '.' || last == '?' || last == '!' ? s : s + ".";
    }

    /** The text with its first letter in lower case: "When the interview is booked" inside a sentence. */
    static String lowerFirst(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    /** The text with its first letter in upper case. */
    static String upperFirst(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** "1 interview needs" or "3 interviews need". */
    static String count(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
