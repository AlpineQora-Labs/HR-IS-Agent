package com.taportal.domain.platform;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiasReportRepository extends JpaRepository<BiasReport, UUID> {}
