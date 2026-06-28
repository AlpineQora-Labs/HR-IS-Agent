package com.olivia.domain.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnockoutQuestionRepository extends JpaRepository<KnockoutQuestion, UUID> {

    List<KnockoutQuestion> findByJobIdOrderByOrdinal(UUID jobId);
}
