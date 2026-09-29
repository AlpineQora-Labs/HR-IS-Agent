package com.taportal.domain.journey;

import com.taportal.domain.candidate.Candidate;
import java.util.List;

/**
 * Whether candidate records that share a phone number are one person. Every
 * application makes a record of its own, so one person who applied twice is
 * two records — and so are two people who gave the same number: a shared
 * phone, a parent's number, a digit mistyped. Pure.
 *
 * <p>They are taken to be one person when the email is the same, or the name.
 */
public final class SamePerson {

    private SamePerson() {
    }

    /** True when every record is the same person as the first. */
    public static boolean all(List<Candidate> people) {
        if (people == null || people.size() < 2) {
            return true;
        }
        Candidate first = people.get(0);
        return people.stream().allMatch(p -> same(first, p));
    }

    public static boolean same(Candidate a, Candidate b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getId() != null && a.getId().equals(b.getId())) {
            return true;
        }
        String emailA = tidy(a.getEmail());
        if (emailA != null && emailA.equals(tidy(b.getEmail()))) {
            return true;
        }
        String nameA = name(a.getName());
        return nameA != null && nameA.equals(name(b.getName()));
    }

    private static String name(String name) {
        String n = tidy(name);
        return n == null || n.equals("unknown") ? null : n;
    }

    private static String tidy(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toLowerCase().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }
}
