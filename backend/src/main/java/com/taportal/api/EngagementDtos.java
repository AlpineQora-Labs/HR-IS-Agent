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
            int registrations,
            int attended,
            int hires) {}

    /** Create payload for a new recruiting event. */
    public record EventCreate(
            String name,
            String type,
            String location,
            OffsetDateTime startsAt) {}

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
