package com.taportal.domain.platform;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricDailyRepository extends JpaRepository<MetricDaily, UUID> {

    List<MetricDaily> findByMetricKeyOrderByMetricDate(String metricKey);
}
