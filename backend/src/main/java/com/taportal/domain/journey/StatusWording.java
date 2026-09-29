package com.taportal.domain.journey;

import java.time.OffsetDateTime;

/**
 * Where an application stands, in words a candidate would use. The one place
 * a stage becomes a sentence: screens and messages never word it themselves.
 * Pure.
 */
public final class StatusWording {

    private StatusWording() {
    }

    /**
     * @param stage           the application's stage
     * @param interviewStatus the status of its interview, or null when it has none
     * @param interviewAt     when that interview is booked for, or null
     * @param now             the moment of asking: an interview booked for a time gone by has been held
     * @return a sentence fragment that reads after "Your application for X: " — no capital, no full stop
     */
    public static String of(String stage, String interviewStatus, OffsetDateTime interviewAt, OffsetDateTime now) {
        String s = stage == null ? "" : stage;
        return switch (s) {
            case "APPLIED" -> "we have it and it is under review";
            case "SCREENED", "MATCHED" -> "you passed the first screening and the hiring team is reviewing it";
            case "INTERVIEW" -> interview(interviewStatus, interviewAt, now);
            case "ASSESSMENT" -> "an assessment is your next step, and the details are in your email";
            case "OFFER" -> "an offer is being prepared and your recruiter will be in touch";
            case "HIRED" -> "you are hired. Welcome to the team";
            case "REJECTED" -> "we are not moving forward with it. Thank you for your interest";
            case "WITHDRAWN" -> "it was withdrawn";
            default -> "it is with the hiring team";
        };
    }

    public static String of(String stage, String interviewStatus, OffsetDateTime interviewAt) {
        return of(stage, interviewStatus, interviewAt, OffsetDateTime.now());
    }

    /** True for the stages at which an application is no longer running. */
    public static boolean closed(String stage) {
        return "REJECTED".equals(stage) || "WITHDRAWN".equals(stage) || "HIRED".equals(stage);
    }

    private static String interview(String status, OffsetDateTime at, OffsetDateTime now) {
        String st = status == null ? "" : status;
        if ("SCHEDULED".equals(st) && at != null && !at.isAfter(now)) {
            st = "COMPLETED";
        }
        return switch (st) {
            case "SCHEDULED" -> at == null
                    ? "your interview is booked"
                    : "your interview is confirmed for " + JourneyTimes.when(at) + ". Reply RESCHEDULE to change it";
            case "SLOTS_PROPOSED" -> "you are invited to interview. Reply TIMES to see the times";
            case "COMPLETED" -> "your interview has taken place and the team is reviewing";
            case "NO_SHOW" -> "we missed you at your interview, and your recruiter will be in touch";
            case "CANCELED" -> "your interview was cancelled, and your recruiter will be in touch";
            default -> "you are selected for interview and we are arranging times";
        };
    }
}
