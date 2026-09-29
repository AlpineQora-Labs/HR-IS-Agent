package com.taportal.domain.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the record of a message. It joins the unit of work it is called
 * from, so that a message and what it speaks of (times put on offer, say) are
 * written together or not at all.
 */
@Service
public class MessageStore {

    private final CandidateMessageRepository messages;

    public MessageStore(CandidateMessageRepository messages) {
        this.messages = messages;
    }

    /** A message to be written, before it is decided what becomes of it. */
    public record Draft(
            UUID candidateId, UUID applicationId, UUID interviewId,
            String channel, String address, String subject, String body,
            String point, UUID templateId, String templateName,
            String dedupeKey, String offer) {

        /** The same draft, carrying the times it offers. */
        public Draft withOffer(String offered) {
            return new Draft(candidateId, applicationId, interviewId, channel, address, subject, body,
                    point, templateId, templateName, dedupeKey, offered);
        }
    }

    /** True when a message with this key is already on record: it is not to be written again. */
    public boolean known(String dedupeKey) {
        return dedupeKey != null && messages.existsByDedupeKey(dedupeKey);
    }

    /** Record a message to be sent. */
    @Transactional
    public CandidateMessage queue(Draft d) {
        CandidateMessage m = outbound(d);
        m.setStatus(CandidateMessage.QUEUED);
        m.setDedupeKey(d.dedupeKey());
        m.setOffer(d.offer());
        return messages.save(m);
    }

    /**
     * Record a message that will not be sent, and why. It carries a key only
     * when the draft gives one — which a scheduled job does, so that it records
     * the matter once and not once a minute. What an event holds back carries
     * none: what was held back today may rightly go tomorrow.
     */
    @Transactional
    public CandidateMessage notSent(Draft d, String status, String reason) {
        CandidateMessage m = outbound(d);
        m.setStatus(status);
        m.setReason(reason);
        m.setDedupeKey(d.dedupeKey());
        return messages.save(m);
    }

    /** Record a text that arrived. */
    @Transactional
    public CandidateMessage received(
            UUID candidateId, String from, String body, String provider, String providerMessageId) {
        CandidateMessage m = new CandidateMessage();
        m.setCandidateId(candidateId);
        m.setChannel(CandidateMessage.SMS);
        m.setDirection(CandidateMessage.INBOUND);
        m.setAddress(from);
        m.setBody(body == null ? "" : body);
        m.setStatus(CandidateMessage.RECEIVED);
        m.setProvider(provider);
        m.setProviderMessageId(providerMessageId);
        return messages.save(m);
    }

    @Transactional
    public void handled(UUID inboundId) {
        messages.findById(inboundId).ifPresent(m -> {
            m.setProcessedAt(OffsetDateTime.now());
            messages.save(m);
        });
    }

    /** Close the offers open for an interview: its times were picked, replaced or withdrawn. */
    @Transactional
    public void closeOffers(UUID interviewId) {
        OffsetDateTime now = OffsetDateTime.now();
        for (CandidateMessage m : messages.openOffersFor(interviewId)) {
            m.setOfferClosedAt(now);
            messages.save(m);
        }
    }

    private static CandidateMessage outbound(Draft d) {
        CandidateMessage m = new CandidateMessage();
        m.setCandidateId(d.candidateId());
        m.setApplicationId(d.applicationId());
        m.setInterviewId(d.interviewId());
        m.setChannel(d.channel());
        m.setDirection(CandidateMessage.OUTBOUND);
        m.setAddress(d.address());
        m.setSubject(d.subject());
        m.setBody(d.body() == null ? "" : d.body());
        m.setPoint(d.point());
        m.setTemplateId(d.templateId());
        m.setTemplateName(d.templateName());
        return m;
    }
}
