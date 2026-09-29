package com.taportal.domain.messaging;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stands in for a carrier: takes every text and delivers none. What would
 * have been sent is on record in {@code candidate_message}, where the message
 * timeline reads it. Used until a provider is configured.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.sms-provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSmsGateway.class);

    @Override
    public String provider() {
        return "SIMULATED";
    }

    @Override
    public Receipt send(String toE164, String body) {
        log.info("Text (simulated) to {}: {} characters", PhoneNumbers.masked(toE164), body.length());
        return Receipt.accepted("sim-" + UUID.randomUUID());
    }
}
