package com.taportal.domain.messaging;

/** Hands an email to whatever carries it; see {@link SmsGateway}. */
public interface EmailGateway {

    String provider();

    Receipt send(String to, String fromName, String subject, String html);
}
