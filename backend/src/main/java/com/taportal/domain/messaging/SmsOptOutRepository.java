package com.taportal.domain.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsOptOutRepository extends JpaRepository<SmsOptOut, String> {
}
