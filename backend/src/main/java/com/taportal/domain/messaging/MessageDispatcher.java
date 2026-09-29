package com.taportal.domain.messaging;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hands recorded messages to a gateway. A message is taken before it is sent
 * (QUEUED to SENDING, by one dispatcher only), sent outside any transaction,
 * and then marked SENT or FAILED.
 *
 * <p>A message found still SENDING long after it was taken was interrupted
 * between the gateway and the record. It is marked FAILED and never sent
 * again by itself: one text too few can be put right, two cannot.
 */
@Service
public class MessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    private final CandidateMessageRepository messages;
    private final MessagePolicy policy;
    private final SmsGateway sms;
    private final EmailGateway email;
    private final TransactionTemplate alone;

    public MessageDispatcher(
            CandidateMessageRepository messages, MessagePolicy policy,
            SmsGateway sms, EmailGateway email, PlatformTransactionManager transactions) {
        this.messages = messages;
        this.policy = policy;
        this.sms = sms;
        this.email = email;
        this.alone = new TransactionTemplate(transactions);
        this.alone.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void dispatch(Collection<CandidateMessage> written) {
        for (CandidateMessage m : written) {
            if (m != null && CandidateMessage.QUEUED.equals(m.getStatus())) {
                dispatch(m.getId());
            }
        }
    }

    public void dispatch(UUID id) {
        Boolean mine = alone.execute(tx -> messages.claimForSending(id) == 1);
        if (!Boolean.TRUE.equals(mine)) {
            return; // taken by another dispatcher, or no longer waiting
        }
        CandidateMessage m = alone.execute(tx -> messages.findById(id).orElse(null));
        if (m == null) {
            return;
        }
        // STOP may have arrived between the writing and the sending.
        if (CandidateMessage.SMS.equals(m.getChannel())
                && policy.optedOut(m.getAddress())
                && !policy.text(m.getAddress(), OffsetDateTime.now(), true, m.getPoint()).send()) {
            finish(id, CandidateMessage.SUPPRESSED, "This number opted out of texts (STOP)", null, null);
            return;
        }
        Receipt receipt;
        String provider;
        try {
            if (CandidateMessage.SMS.equals(m.getChannel())) {
                provider = sms.provider();
                receipt = sms.send(m.getAddress(), m.getBody());
            } else {
                provider = email.provider();
                receipt = email.send(m.getAddress(), null, m.getSubject(), m.getBody());
            }
        } catch (RuntimeException e) {
            log.warn("Gateway failed for message {}: {}", id, e.toString());
            finish(id, CandidateMessage.FAILED, "The gateway did not take the message", null, null);
            return;
        }
        if (receipt.accepted()) {
            finish(id, CandidateMessage.SENT, null, provider, receipt.providerMessageId());
        } else {
            finish(id, CandidateMessage.FAILED,
                    receipt.error() == null ? "The gateway refused the message" : receipt.error(), provider, null);
        }
    }

    /** Send what was written and never sent; give up on what was taken and never finished. */
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        for (CandidateMessage m : messages.findByStatusAndCreatedAtBefore(CandidateMessage.QUEUED, now.minusMinutes(1))) {
            dispatch(m.getId());
        }
        for (CandidateMessage m : messages.findByStatusAndCreatedAtBefore(CandidateMessage.SENDING, now.minusMinutes(5))) {
            finish(m.getId(), CandidateMessage.FAILED,
                    "Sending was interrupted and could not be confirmed. It was not sent again", null, null);
        }
    }

    private void finish(UUID id, String status, String reason, String provider, String providerMessageId) {
        alone.executeWithoutResult(tx -> messages.findById(id).ifPresent(m -> {
            m.setStatus(status);
            m.setReason(reason);
            if (provider != null) {
                m.setProvider(provider);
            }
            if (providerMessageId != null) {
                m.setProviderMessageId(providerMessageId);
            }
            if (CandidateMessage.SENT.equals(status)) {
                m.setSentAt(OffsetDateTime.now());
            }
            messages.save(m);
        }));
    }
}
