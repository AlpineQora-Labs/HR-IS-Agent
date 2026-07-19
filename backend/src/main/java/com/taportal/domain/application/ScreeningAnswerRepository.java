package com.taportal.domain.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningAnswerRepository extends JpaRepository<ScreeningAnswer, UUID> {

    List<ScreeningAnswer> findByApplicationId(UUID applicationId);
}
