package com.taportal.domain.engagement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One student at one event: register -> check in / no-show. Each registration is
 * (or becomes) a real candidate, so the event funnel feeds the pipeline instead
 * of a vanity counter.
 */
@Entity
@Table(name = "event_registration")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Setter
    @Column(name = "candidate_id")
    private UUID candidateId;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    @Column(nullable = false)
    private String email;

    @Setter
    @Column(name = "school_id")
    private UUID schoolId;

    @Setter
    @Column
    private String major;

    @Setter
    @Column(name = "grad_year")
    private Integer gradYear;

    /** REGISTERED | WALK_IN */
    @Setter
    @Column(nullable = false)
    private String source = "REGISTERED";

    /** REGISTERED | CHECKED_IN | NO_SHOW | WAITLISTED (no seat yet) | CANCELLED (gave the seat up) */
    @Setter
    @Column(nullable = false)
    private String status = "REGISTERED";

    @Setter
    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    /** A second address the student gave (their own, beside the school one). */
    @Setter
    @Column(name = "personal_email")
    private String personalEmail;

    /** The phone number as the student gave it. */
    @Setter
    @Column
    private String phone;

    /** When the student agreed to their details being kept. Null for rows older than the rule. */
    @Setter
    @Column(name = "consent_at")
    private OffsetDateTime consentAt;

    /** What the recruiter made of them at the booth: 1 (weak) to 5 (strong). */
    @Setter
    @Column
    private Integer rating;

    @Setter
    @Column
    private String notes;

    /** Picked out to be followed up after the event. */
    @Setter
    @Column(nullable = false)
    private boolean shortlisted;

    @Setter
    @Column(name = "evaluated_at")
    private OffsetDateTime evaluatedAt;

    /** The application made for them when they were put forward for a job. */
    @Setter
    @Column(name = "application_id")
    private UUID applicationId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
