package com.taportal.domain.forms;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormResponseRepository extends JpaRepository<FormResponse, UUID> {

    List<FormResponse> findBySubjectTypeAndSubjectIdOrderByCreatedAt(String subjectType, UUID subjectId);
}
