package com.taportal.domain.engagement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for EventTemplate rows (Spring Data JPA; derived queries only). */
public interface EventTemplateRepository extends JpaRepository<EventTemplate, UUID> {

    List<EventTemplate> findByOrderByName();

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByNameIgnoreCase(String name);

    /** Event templates that use a form, either way round. */
    long countByRegistrationFormIdOrIntakeFormId(UUID registrationFormId, UUID intakeFormId);
}
