package com.taportal.domain.interview;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findByApplicationId(UUID applicationId);

    List<Interview> findByStatus(String status);

    List<Interview> findByStatusAndScheduledAtBetween(String status, java.time.OffsetDateTime from, java.time.OffsetDateTime to);
}
