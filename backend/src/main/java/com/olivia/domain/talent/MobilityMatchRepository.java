package com.olivia.domain.talent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobilityMatchRepository extends JpaRepository<MobilityMatch, UUID> {

    List<MobilityMatch> findByOrderByFitScoreDesc();
}
