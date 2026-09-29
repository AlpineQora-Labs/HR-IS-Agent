package com.taportal.domain.interview;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for Interview rows (Spring Data JPA; derived queries only). */
public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findByApplicationId(UUID applicationId);

    List<Interview> findByApplicationIdIn(java.util.Collection<UUID> applicationIds);

    /** The interview, held so that only one booking or release of it runs at a time. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from Interview i where i.id = :id")
    java.util.Optional<Interview> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    List<Interview> findByStatus(String status);

    List<Interview> findByStatusAndScheduledAtBetween(String status, java.time.OffsetDateTime from, java.time.OffsetDateTime to);

    List<Interview> findByStatusAndScheduledAtBefore(String status, java.time.OffsetDateTime before);
}
