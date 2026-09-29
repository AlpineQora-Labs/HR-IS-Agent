package com.taportal.domain.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One text message or email, to or from a candidate. Every message is written
 * here before it is handed to a gateway, and every message that was decided
 * against is written here too, with the reason — so what a candidate was
 * told, and what they were not told and why, can always be read back.
 */
@Entity
@Table(name = "candidate_message")
@Getter
@Setter
@NoArgsConstructor
public class CandidateMessage {

    public static final String SMS = "SMS";
    public static final String EMAIL = "EMAIL";

    public static final String OUTBOUND = "OUTBOUND";
    public static final String INBOUND = "INBOUND";

    /** Written, waiting to be handed to the gateway. */
    public static final String QUEUED = "QUEUED";
    /** Claimed by a dispatcher; the gateway has it or is about to. */
    public static final String SENDING = "SENDING";
    public static final String SENT = "SENT";
    /** Something is wrong that a person has to put right. {@link #reason} says what. */
    public static final String FAILED = "FAILED";
    /** Deliberately not sent: no agreement, opted out, already told. {@link #reason} says which. */
    public static final String SUPPRESSED = "SUPPRESSED";
    /** Inbound. */
    public static final String RECEIVED = "RECEIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "candidate_id")
    private UUID candidateId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "interview_id")
    private UUID interviewId;

    /** SMS | EMAIL */
    @Column(nullable = false, length = 10)
    private String channel;

    /** OUTBOUND | INBOUND */
    @Column(nullable = false, length = 10)
    private String direction;

    /** The phone number (E.164) or email address written to, or received from. */
    @Column
    private String address;

    @Column
    private String subject;

    /** What was sent: text for a text message, HTML for an email. */
    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** The journey point this message belongs to; see JourneyPoint. */
    @Column(length = 40)
    private String point;

    @Column(name = "template_id")
    private UUID templateId;

    /** The template's name when the message was written, kept for the record. */
    @Column(name = "template_name", length = 160)
    private String templateName;

    @Column(nullable = false, length = 12)
    private String status;

    @Column
    private String reason;

    @Column(length = 40)
    private String provider;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    /** Says what this message is, so the same thing is never sent twice. Unique when set. */
    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    /**
     * The numbered times this message offered, as JSON:
     * {@code [{"n":1,"startsAt":"…","endsAt":"…"}]}. Times, not slot ids — so
     * "2" still means the second time the candidate read, whatever was
     * proposed again since.
     */
    @Column(columnDefinition = "text")
    private String offer;

    /** When the offer stopped being open: picked, replaced, or withdrawn. */
    @Column(name = "offer_closed_at")
    private OffsetDateTime offerClosedAt;

    /** Inbound: when it was acted on. Null means received but not yet handled. */
    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;
}
