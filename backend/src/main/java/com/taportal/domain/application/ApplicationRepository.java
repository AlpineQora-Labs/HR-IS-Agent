package com.taportal.domain.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByJobId(UUID jobId);

    List<Application> findByCandidateId(UUID candidateId);

    List<Application> findByJobIdAndStage(UUID jobId, String stage);

    long countByJobIdAndStage(UUID jobId, String stage);
}
