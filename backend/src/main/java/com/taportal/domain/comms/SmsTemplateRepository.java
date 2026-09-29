package com.taportal.domain.comms;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, UUID> {

    List<SmsTemplate> findByOrderByNameAsc();

    java.util.Optional<SmsTemplate> findByTemplateKey(String templateKey);
}
