package com.taportal.domain.messaging;

/**
 * Hands a text message to whatever carries it. A provider is one class
 * implementing this, chosen by {@code app.messaging.sms-provider}; its
 * credentials belong in configuration set by the owner of the account.
 */
public interface SmsGateway {

    /** The name recorded against each message, for example {@code SIMULATED}. */
    String provider();

    Receipt send(String toE164, String body);
}
