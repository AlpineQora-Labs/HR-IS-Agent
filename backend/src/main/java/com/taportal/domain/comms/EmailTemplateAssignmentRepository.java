package com.taportal.domain.comms;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for EmailTemplateAssignment rows (Spring Data JPA; derived queries only). */
public interface EmailTemplateAssignmentRepository extends JpaRepository<EmailTemplateAssignment, UUID> {

    List<EmailTemplateAssignment> findByTemplateIdOrderByCreatedAt(UUID templateId);
}
