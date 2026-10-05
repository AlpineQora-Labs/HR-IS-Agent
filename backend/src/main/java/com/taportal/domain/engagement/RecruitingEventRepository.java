package com.taportal.domain.engagement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for RecruitingEvent rows (Spring Data JPA; derived queries only). */
public interface RecruitingEventRepository extends JpaRepository<RecruitingEvent, UUID> {

    List<RecruitingEvent> findByOrderByStartsAtDesc();

    long countByTemplateId(UUID templateId);

    /** Events that use a form: as the form students register with, or the one filled in at creation. */
    long countByRegistrationFormIdOrIntakeFormId(UUID registrationFormId, UUID intakeFormId);

    /** Events starting in a stretch of time: the ones a reminder or a note afterwards could be due for. */
    java.util.List<RecruitingEvent> findByStartsAtBetween(java.time.OffsetDateTime from, java.time.OffsetDateTime to);
}
