package com.taportal.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Engagement (CRM/referrals/events/surveys) response DTOs. */
public final class EngagementDtos {

    private EngagementDtos() {}

    public record Campaign(
            UUID id,
            String name,
            String audience,
            String channel,
            String status,
            int sent,
            int opened,
            int replied,
            double openRate,
            double replyRate) {}

    public record Referral(
            UUID id,
            String referrerName,
            String candidateName,
            String jobTitle,
            String status,
            BigDecimal bonusAmount,
            OffsetDateTime createdAt) {}

    public record EventRow(
            UUID id,
            String name,
            String type,
            String location,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String timezone,
            /** Latest approval status for this event (PENDING/APPROVED/…), null if never routed. */
            String approvalStatus,
            int registrations,
            int attended,
            int hires,
            /** Seat settings: the most it seats (null = no limit), when registration ends, waitlist on or off. */
            Integer capacity,
            OffsetDateTime registrationClosesAt,
            boolean waitlistEnabled,
            int waitlisted,
            int shortlisted,
            /** The form students register with; null for the default. */
            UUID registrationFormId,
            /** The template it was created from, and what it took from it. */
            UUID templateId,
            String country,
            String format,
            /** When it goes on the careers site, once approved; null for as soon as approved. */
            OffsetDateTime publishAt,
            /** Whether students can see it now: approved, and its publishing moment has come. */
            boolean published) {}

    /**
     * What can be changed about an event after it is created. Changing when or where it is held
     * (date, time, timezone, place, format) sends an approved event back for approval.
     */
    public record EventUpdate(
            String name,
            String type,
            String location,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String timezone,
            String format) {}

    /** Create payload for a new recruiting event. */
    public record EventCreate(
            String name,
            String type,
            String location,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String timezone,
            UUID intakeFormId,
            /** The form students register with; null for the default Student registration form. */
            UUID registrationFormId,
            /** The template to start from: the event takes its country, forms, seats and consent wording. */
            UUID templateId,
            /** IN_PERSON | VIRTUAL | HYBRID */
            String format,
            /** When it goes on the careers site, once approved; null for as soon as approved (or the template's default). */
            OffsetDateTime publishAt,
            /** Route this event down the workflow's flagged/exception path. */
            Boolean flaggedCritical) {}

    public record SurveyRow(
            UUID id,
            String name,
            String stage,
            int sent,
            int responses,
            BigDecimal avgSentiment,
            Integer nps,
            double responseRate) {}
}
