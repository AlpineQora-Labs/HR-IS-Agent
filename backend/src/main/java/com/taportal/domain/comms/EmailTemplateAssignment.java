package com.taportal.domain.comms;

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

/** Binds a template to a survey trigger (initial invite, reminders, thank-you…). */
@Entity
@Table(name = "email_template_assignment")
@Getter
@NoArgsConstructor
public class EmailTemplateAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Setter
    @Column(name = "survey_id")
    private UUID surveyId;

    @Setter
    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Setter
    @Column(name = "send_delay_days", nullable = false)
    private int sendDelayDays;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
