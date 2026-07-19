package com.taportal.domain.talent;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TalentPoolMemberRepository extends JpaRepository<TalentPoolMember, TalentPoolMemberId> {

    long countByPoolId(UUID poolId);
}
