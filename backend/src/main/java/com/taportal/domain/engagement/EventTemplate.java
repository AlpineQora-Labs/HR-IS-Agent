package com.taportal.domain.engagement;

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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Everything that is the same for a kind of event ("Campus drive, India"): its type and format,
 * country and timezone, the forms it uses, its seat settings and the consent wording. A new event
 * starts from one and copies these; the event's settings are then its own.
 */
@Entity
@Table(name = "event_template")
@Getter
@Setter
@NoArgsConstructor
public class EventTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** CAMPUS | CAREER_FAIR | HIRING_EVENT | WEBINAR */
    @Column(name = "event_type", nullable = false)
    private String eventType = "CAMPUS";

    /** IN_PERSON | VIRTUAL | HYBRID */
    @Column(nullable = false)
    private String format = "IN_PERSON";

    /** ISO country code; null for any country. */
    @Column(length = 2)
    private String country;

    @Column(nullable = false)
    private String timezone = "America/New_York";

    /** The form the recruiter fills in when creating the event; null for the default. */
    @Column(name = "intake_form_id")
    private UUID intakeFormId;

    /** The form students register with; null for the default. */
    @Column(name = "registration_form_id")
    private UUID registrationFormId;

    /** The most an event seats; null for no limit. */
    @Column
    private Integer capacity;

    @Column(name = "waitlist_enabled", nullable = false)
    private boolean waitlistEnabled;

    /** Registration closes this many days before the event starts; null keeps it open until then. */
    @Column(name = "close_days_before")
    private Integer closeDaysBefore;

    /** An approved event goes on the careers site this many days before it starts; null: as soon as approved. */
    @Column(name = "publish_days_before")
    private Integer publishDaysBefore;

    /** What a student agrees to; null for the standard wording. */
    @Column(name = "consent_text")
    private String consentText;

    /** Switched off, it is no longer offered for new events; events made from it are untouched. */
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
