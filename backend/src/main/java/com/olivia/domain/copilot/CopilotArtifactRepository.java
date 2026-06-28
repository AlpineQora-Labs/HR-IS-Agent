package com.olivia.domain.copilot;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopilotArtifactRepository extends JpaRepository<CopilotArtifact, UUID> {

    List<CopilotArtifact> findByOrderByCreatedAtDesc();
}
