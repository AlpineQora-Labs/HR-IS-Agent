package com.taportal.domain.interview;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewPanelistRepository extends JpaRepository<InterviewPanelist, InterviewPanelist.Key> {

    List<InterviewPanelist> findByInterviewId(UUID interviewId);

    void deleteByInterviewId(UUID interviewId);
}
