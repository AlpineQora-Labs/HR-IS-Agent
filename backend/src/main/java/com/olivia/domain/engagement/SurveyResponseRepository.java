package com.olivia.domain.engagement;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, UUID> {

    long countBySurveyId(UUID surveyId);
}
