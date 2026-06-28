package com.olivia.domain.engagement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NurtureCampaignRepository extends JpaRepository<NurtureCampaign, UUID> {

    List<NurtureCampaign> findByOrderByCreatedAtDesc();
}
