package com.taportal.domain.forms;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormDefinitionRepository extends JpaRepository<FormDefinition, UUID> {

    Optional<FormDefinition> findByPurpose(String purpose);
}
