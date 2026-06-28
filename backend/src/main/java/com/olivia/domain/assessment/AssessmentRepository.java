package com.olivia.domain.assessment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findByApplicationId(UUID applicationId);
}
