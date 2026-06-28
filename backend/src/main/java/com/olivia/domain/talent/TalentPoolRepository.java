package com.olivia.domain.talent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TalentPoolRepository extends JpaRepository<TalentPool, UUID> {

    List<TalentPool> findByOrderByNameAsc();
}
