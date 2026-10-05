package com.taportal.api;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Campus module DTOs: schools and per-student event rosters. */
public final class CampusDtos {

    private CampusDtos() {}

    /** {@code country}: ISO code; null when not said. */
    public record SchoolRow(UUID id, String name, String location, String tier, String country) {}

    /** A school to add to the list. Tier: CORE | TARGET | OPPORTUNISTIC. */
    public record SchoolCreate(
            @NotBlank @jakarta.validation.constraints.Size(max = 160) String name,
            @jakarta.validation.constraints.Size(max = 160) String location,
            String tier,
            @jakarta.validation.constraints.Size(min = 2, max = 2) String country) {
    }

    /**
     * One student on an event's roster.
     *
     * @param status           REGISTERED | CHECKED_IN | NO_SHOW | WAITLISTED | CANCELLED
     * @param waitlistPosition their place on the waitlist, from 1; null when not waitlisted
     * @param consentAt        when they agreed to their details being kept
     * @param rating           the recruiter's rating at the booth, 1 to 5
     * @param applicationId    the application made when they were put forward for a job
     * @param jobTitle         that job
     * @param applicationStage where that application stands
     */
    public record RegistrationRow(
            UUID id,
            UUID eventId,
            UUID candidateId,
            String name,
            String email,
            UUID schoolId,
            String schoolName,
            String major,
            Integer gradYear,
            String source,
            String status,
            OffsetDateTime checkedInAt,
            Integer waitlistPosition,
            String personalEmail,
            OffsetDateTime consentAt,
            Integer rating,
            String notes,
            boolean shortlisted,
            UUID applicationId,
            String jobTitle,
            String applicationStage) {
    }

    /** Pre-registration or booth walk-in. {@code consent}: the student agreed to their details being kept. */
    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank String email,
            UUID schoolId,
            String major,
            Integer gradYear,
            Boolean walkIn,
            Boolean consent,
            // A second address and a phone number, when the student gave them: both help find a
            // student who is already on record under another address.
            String personalEmail,
            String phone) {

        public RegisterRequest(
                String name, String email, UUID schoolId, String major, Integer gradYear, Boolean walkIn, Boolean consent) {
            this(name, email, schoolId, major, gradYear, walkIn, consent, null, null);
        }
    }

    /** The form a student fills in for one event: the event's own, with its School question listing the schools. */
    public record RegistrationForm(UUID formId, String name, String schema) {}

    /**
     * How one school has done across every event.
     *
     * @param events      events its students came to
     * @param registered  students who signed up and did not cancel
     * @param attended    students checked in
     * @param shortlisted students picked out for follow-up
     * @param putForward  students with an application made from an event
     * @param hired       of those, the ones hired
     */
    public record SchoolReportRow(
            UUID schoolId, String name, String location, String tier, String country,
            int events, int registered, int attended, int shortlisted, int putForward, int hired) {
    }

    public record TransitionRequest(@NotBlank String status) {}

    /** What the recruiter made of a student. Each part is optional; a null rating clears it. */
    public record EvaluationRequest(
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) Integer rating,
            @jakarta.validation.constraints.Size(max = 2000) String notes,
            Boolean shortlisted) {
    }

    /** Put a student forward for a job. */
    public record ApplyRequest(@jakarta.validation.constraints.NotNull UUID jobId) {}

    /** The seat settings of an event. Null capacity: no limit. Null close: open until it starts. */
    public record SeatSettings(
            @jakarta.validation.constraints.Min(1) Integer capacity,
            OffsetDateTime registrationClosesAt,
            boolean waitlistEnabled,
            // The form students register with; null for the default Student registration form.
            UUID registrationFormId,
            // When the event goes on the careers site, once approved; null for as soon as approved.
            OffsetDateTime publishAt) {
    }

    /**
     * What the public registration page needs: the event header, whether it is taking
     * registrations, and the wording the student agrees to.
     *
     * @param registration OPEN | WAITLIST | FULL | CLOSED (see {@code EventSeats})
     * @param seatsLeft    seats still free; null when the event has no limit
     */
    public record EventPublicInfo(
            UUID id, String name, String type, String location,
            OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone,
            String registration, Integer seatsLeft, OffsetDateTime registrationClosesAt, String consentText) {
    }

    /** Form-driven registration: the builder-defined answers array as JSON. */
    public record RegisterFormRequest(@NotBlank String answers, Boolean consent) {}
}
