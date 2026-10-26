package com.taportal.domain.briefing;

import java.util.List;
import java.util.Map;

/**
 * What the portal's records say about the thing a recruiter is looking at: the day's interviews,
 * one candidate, or one job. {@link BriefingService} builds it from the records; nothing in it is
 * written by a model. The recruiter assistant puts it into words.
 *
 * <p><b>No names, no contact details.</b> Every person is a placeholder: {@code [C1]} for a
 * candidate, {@code [S1]} for someone on the hiring team. {@link #people} maps each placeholder to
 * the name, and the assistant puts the names back only after the wording is written, so a model
 * never sees them. Phone numbers and email addresses are left out altogether, and so is anything
 * that looks like one in a candidate's own words.
 *
 * @param scope    DAY, CANDIDATE or JOB
 * @param about    what the briefing is about, for example "[C1], applying for Branch Manager"
 * @param sections what stands where, grouped under a title, one sentence per line
 * @param whys     what is waiting, held back or not done, why, and the next step when there is one
 * @param gaps     what these records do not hold, so an answer says so instead of guessing
 * @param people   placeholder to name, in the order they first appear
 * @param links    screens of the portal the answer can point to
 */
public record Briefing(
        String scope,
        String about,
        List<Section> sections,
        List<Why> whys,
        List<String> gaps,
        Map<String, String> people,
        List<Link> links) {

    public static final String DAY = "DAY";
    public static final String CANDIDATE = "CANDIDATE";
    public static final String JOB = "JOB";

    /** A group of facts under a title, for example "Needs an outcome". */
    public record Section(String title, List<String> lines) {
    }

    /**
     * Something that is not moving, and the reason the records give for it.
     *
     * @param what    what the recruiter sees, for example "[C1] is listed under Awaiting candidate"
     * @param because the reason the records give
     * @param next    what can be done about it in the portal, or null when the records suggest nothing
     */
    public record Why(String what, String because, String next) {
    }

    /** A screen of the portal: its label (which may hold placeholders) and its path in the app. */
    public record Link(String label, String path) {
    }
}
