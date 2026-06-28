package com.olivia.domain.engagement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitingEventRepository extends JpaRepository<RecruitingEvent, UUID> {

    List<RecruitingEvent> findByOrderByStartsAtDesc();
}
