package com.taportal.domain.briefing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hands out one placeholder per person a briefing names, so names stay on the server.
 * The same name always gets the same placeholder within one briefing.
 */
final class Placeholders {

    private final Map<String, String> byName = new LinkedHashMap<>();
    private final Map<String, String> people = new LinkedHashMap<>();
    private int candidates;
    private int staff;

    /** A candidate: [C1], [C2] and so on. */
    String candidate(String name) {
        return token(name, "C", "the candidate");
    }

    /** Someone on the hiring team, an interviewer, recruiter or hiring manager: [S1], [S2] and so on. */
    String staff(String name) {
        return token(name, "S", "someone on the hiring team");
    }

    /** Placeholder to name, in the order they were first handed out. */
    Map<String, String> people() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(people));
    }

    /** Free text (a candidate's reply, an interviewer's notes) with the names handed out so far replaced. */
    String hideIn(String text) {
        return PeopleNames.hide(people, text);
    }

    private String token(String name, String kind, String unnamed) {
        // The overview writes a dash where it found no name; that is not a person to hide.
        if (name == null || name.isBlank() || name.trim().equals("—")) {
            return unnamed;
        }
        String key = kind + ":" + PeopleNames.key(name);
        return byName.computeIfAbsent(key, k -> {
            String t = "[" + kind + ("C".equals(kind) ? ++candidates : ++staff) + "]";
            people.put(t, name.trim());
            return t;
        });
    }
}
