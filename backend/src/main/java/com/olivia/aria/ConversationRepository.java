package com.olivia.aria;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByCandidateId(UUID candidateId);

    List<Conversation> findByApplicationId(UUID applicationId);
}
