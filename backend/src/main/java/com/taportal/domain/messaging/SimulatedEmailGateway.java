package com.taportal.domain.messaging;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Stands in for a mail service; see {@link SimulatedSmsGateway}. */
@Component
@ConditionalOnProperty(name = "app.messaging.email-provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailGateway.class);

    @Override
    public String provider() {
        return "SIMULATED";
    }

    @Override
    public Receipt send(String to, String fromName, String subject, String html) {
        log.info("Email (simulated), subject \"{}\"", subject);
        return Receipt.accepted("sim-" + UUID.randomUUID());
    }
}
