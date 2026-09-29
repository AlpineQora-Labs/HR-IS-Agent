package com.taportal.domain.messaging;

/**
 * What a gateway said about a message handed to it.
 *
 * @param accepted          the gateway took the message
 * @param providerMessageId the gateway's own id for it, when accepted
 * @param error             why it was not taken, when it was not
 */
public record Receipt(boolean accepted, String providerMessageId, String error) {

    public static Receipt accepted(String providerMessageId) {
        return new Receipt(true, providerMessageId, null);
    }

    public static Receipt refused(String error) {
        return new Receipt(false, null, error);
    }
}
