package com.taportal.domain.interview;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, UUID> {

    List<InterviewSlot> findByJobIdAndBookedFalse(UUID jobId);

    List<InterviewSlot> findByJobId(UUID jobId);

    List<InterviewSlot> findByInterviewIdAndStatusOrderByStartsAt(UUID interviewId, String status);

    List<InterviewSlot> findByInterviewId(UUID interviewId);
}
