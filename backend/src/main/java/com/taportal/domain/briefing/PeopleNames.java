package com.taportal.domain.briefing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Swaps the people a {@link Briefing} names for their placeholders and back.
 *
 * <p>{@link #hide} runs on anything that leaves the server for a model: a recruiter's question,
 * the earlier turns of the chat, an interviewer's notes. {@link #show} runs on what comes back,
 * before the recruiter reads it.
 */
public final class PeopleNames {

    private PeopleNames() {
    }

    /**
     * The text with every known name replaced by its placeholder. Full names match in any case;
     * a first name on its own matches only when capitalised and only when one person has it, so
     * "mark it completed" is left alone when someone on the panel is called Mark.
     *
     * @param people placeholder to name, as {@link Briefing#people()} holds it
     */
    public static String hide(Map<String, String> people, String text) {
        if (text == null || text.isEmpty() || people == null || people.isEmpty()) {
            return text;
        }
        String out = text;
        // Longest names first, so "Maya Chen-Ross" is replaced before "Maya Chen" could be.
        List<Map.Entry<String, String>> longestFirst = new ArrayList<>(people.entrySet());
        longestFirst.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getValue().length()).reversed());
        for (Map.Entry<String, String> e : longestFirst) {
            out = replaceWord(out, e.getValue(), e.getKey(), true);
        }
        Map<String, List<String>> byFirstName = new HashMap<>();
        for (Map.Entry<String, String> e : people.entrySet()) {
            String first = e.getValue().trim().split("\\s+")[0];
            if (first.length() >= 2 && !first.equals(e.getValue().trim())) {
                byFirstName.computeIfAbsent(first, k -> new ArrayList<>()).add(e.getKey());
            }
        }
        for (Map.Entry<String, List<String>> e : byFirstName.entrySet()) {
            if (e.getValue().size() == 1) {
                out = replaceWord(out, e.getKey(), e.getValue().get(0), false);
            }
        }
        return out;
    }

    /** The text with every placeholder replaced by the name it stands for. */
    public static String show(Map<String, String> people, String text) {
        if (text == null || text.isEmpty() || people == null) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> e : people.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private static String replaceWord(String text, String word, String token, boolean anyCase) {
        if (word == null || word.isBlank()) {
            return text;
        }
        int flags = anyCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word.trim()) + "(?![\\p{L}\\p{N}])", flags);
        return p.matcher(text).replaceAll(Matcher.quoteReplacement(token));
    }

    /** Lower-cased, for comparing names. */
    static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
