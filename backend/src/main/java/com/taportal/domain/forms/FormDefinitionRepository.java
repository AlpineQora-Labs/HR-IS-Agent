package com.taportal.domain.forms;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for FormDefinition rows (Spring Data JPA; derived queries only). */
public interface FormDefinitionRepository extends JpaRepository<FormDefinition, UUID> {

    Optional<FormDefinition> findByPurpose(String purpose);
}
