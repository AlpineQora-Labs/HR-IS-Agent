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

/** A recruiting event / campus / career fair (Paradox). */
@Entity
@Table(name = "recruiting_event")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RecruitingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Column(nullable = false)
    private String name;

    /** HIRING_EVENT | CAMPUS | WEBINAR | CAREER_FAIR */
    @Setter
    @Column(nullable = false)
    private String type;

    @Setter
    @Column
    private String location;

    @Setter
    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    /** Optional end of the event (validated after startsAt on create). */
    @Setter
    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    /** IANA zone the event runs in — display context for candidates. */
    @Setter
    @Column(nullable = false)
    private String timezone = "America/New_York";

    @Setter
    @Column(nullable = false)
    private int registrations;

    @Setter
    @Column(nullable = false)
    private int attended;

    @Setter
    @Column(nullable = false)
    private int hires;

    /** The most students it seats; null for no limit. See {@link EventSeats}. */
    @Setter
    @Column
    private Integer capacity;

    /** When registration ends; null keeps it open until the event starts. */
    @Setter
    @Column(name = "registration_closes_at")
    private OffsetDateTime registrationClosesAt;

    /** Whether a full event takes names for a waitlist. */
    @Setter
    @Column(name = "waitlist_enabled", nullable = false)
    private boolean waitlistEnabled;

    /** The template it was created from; null for an event started blank. */
    @Setter
    @Column(name = "template_id")
    private UUID templateId;

    /** ISO country code it is held in (from its template); null when not said. */
    @Setter
    @Column(length = 2)
    private String country;

    /** IN_PERSON | VIRTUAL | HYBRID; null on events older than the setting. */
    @Setter
    @Column
    private String format;

    /** What a student agrees to when registering; null for the standard wording. */
    @Setter
    @Column(name = "consent_text")
    private String consentText;

    /**
     * Set when it was moved (date, time, place or format) while students were registered, and
     * they have not yet been told. Cleared when they are. See CampusService.tellStudentsOfMove.
     */
    @Setter
    @Column(name = "moved_at")
    private OffsetDateTime movedAt;

    /** It must be approved before students can see it. True for every event created since the rule. */
    @Setter
    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;

    /**
     * When it goes on the careers site. It must be approved as well: approved, it shows from this
     * moment; not approved, never. Null: as soon as it is approved.
     */
    @Setter
    @Column(name = "publish_at")
    private OffsetDateTime publishAt;

    /** The form students fill in to register (null = the default Student registration form). */
    @Setter
    @Column(name = "registration_form_id")
    private UUID registrationFormId;

    /** The intake form this event was created with (null = kind default). */
    @Setter
    @Column(name = "intake_form_id")
    private UUID intakeFormId;
}
