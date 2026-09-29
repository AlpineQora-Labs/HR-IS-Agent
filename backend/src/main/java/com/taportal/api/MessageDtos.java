package com.taportal.api;

import com.taportal.domain.journey.JourneyPoint;
import com.taportal.domain.messaging.CandidateMessage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** What the screens are shown of texts and emails. Wording and status come from here; screens only lay them out. */
public final class MessageDtos {

    private MessageDtos() {
    }

    /**
     * One message on the timeline.
     *
     * @param channel    SMS | EMAIL
     * @param direction  OUTBOUND | INBOUND
     * @param status     QUEUED | SENDING | SENT | FAILED | SUPPRESSED | RECEIVED
     * @param statusText the status in words, for example "Not sent"
     * @param reason     why it was not sent, when it was not
     * @param sentWhen   the journey point in words, for example "When the interview is booked"
     * @param text       the message as plain text; for an email, its body without markup
     * @param parts      for a text message, how many messages a carrier would bill it as
     */
    public record MessageRow(
            UUID id, String channel, String direction, String status, String statusText, String reason,
            String point, String sentWhen, String templateName, String address, String subject,
            String text, Integer parts, boolean offersTimes, UUID applicationId, UUID interviewId,
            OffsetDateTime createdAt, OffsetDateTime sentAt) {

        public static MessageRow of(CandidateMessage m) {
            boolean sms = CandidateMessage.SMS.equals(m.getChannel());
            String text = sms ? m.getBody() : plain(m.getBody());
            return new MessageRow(
                    m.getId(), m.getChannel(), m.getDirection(), m.getStatus(), words(m.getStatus()), m.getReason(),
                    m.getPoint(), JourneyPoint.of(m.getPoint()).map(JourneyPoint::label).orElse(null),
                    m.getTemplateName(), m.getAddress(), m.getSubject(), text,
                    sms && CandidateMessage.OUTBOUND.equals(m.getDirection()) && !text.isEmpty()
                            ? com.taportal.domain.messaging.SmsText.parts(text) : null,
                    m.getOffer() != null && m.getOfferClosedAt() == null,
                    m.getApplicationId(), m.getInterviewId(), m.getCreatedAt(), m.getSentAt());
        }

        private static String words(String status) {
            return switch (status == null ? "" : status) {
                case CandidateMessage.QUEUED, CandidateMessage.SENDING -> "Sending";
                case CandidateMessage.SENT -> "Sent";
                case CandidateMessage.FAILED -> "Failed";
                case CandidateMessage.SUPPRESSED -> "Not sent";
                case CandidateMessage.RECEIVED -> "Received";
                default -> status;
            };
        }

        /** An email's body as the words a reader sees. */
        static String plain(String html) {
            if (html == null) {
                return "";
            }
            return html
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)</(p|h[1-6]|li|div|tr)>", "\n")
                    .replaceAll("(?i)<li[^>]*>", "- ")
                    .replaceAll("<[^>]+>", "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                    .replaceAll("[ \\t]+\\n", "\n")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();
        }
    }

    /**
     * A candidate's messages, and what the screen needs to know to show them.
     *
     * @param phone       the number texts go to, or null when there is none a text can reach
     * @param canBeTexted false when texts are held back; {@code whyNot} then says why
     * @param journeyOn   false when the candidate journey is switched off: nothing is sent unprompted
     */
    public record Timeline(
            UUID candidateId, String candidateName, String phone, String email,
            boolean canBeTexted, String whyNot, boolean journeyOn, List<MessageRow> messages) {
    }

    /** A text played as the candidate's phone. */
    public record ReplyRequest(String body) {
    }

    /** A text as a carrier would deliver it. */
    public record InboundSms(String from, String body, String messageId) {
    }

    /**
     * @param understood   what the text was taken to ask for
     * @param understoodAs the same, in words
     * @param duplicate    true when this text had been handled already
     * @param replies      what was written in answer
     */
    public record InboundResult(
            UUID inboundId, String understood, String understoodAs, boolean duplicate, List<MessageRow> replies) {
    }

    /** A point of the journey, for the screens that let one be chosen. */
    public record PointDto(String key, String sentWhen, String kind, boolean byText, boolean byEmail,
            boolean drawn, List<String> needs, String templateKey) {
    }

    public record FieldDto(String name, String holds) {
    }

    /** The rules a journey workflow can be drawn with. The value is the stored key. */
    public record RuleDto(String value, String sentence) {
    }

    public record JourneyInfo(boolean on, List<PointDto> points, List<FieldDto> fields, List<RuleDto> rules) {
    }

    /** @param sent what was written; {@code note} says why nothing was, when nothing was */
    public record ReminderResult(List<MessageRow> sent, String note) {
    }
}
