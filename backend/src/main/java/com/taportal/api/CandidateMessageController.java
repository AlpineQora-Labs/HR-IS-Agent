package com.taportal.api;

import com.taportal.api.MessageDtos.InboundResult;
import com.taportal.api.MessageDtos.InboundSms;
import com.taportal.api.MessageDtos.ReplyRequest;
import com.taportal.api.MessageDtos.Timeline;
import com.taportal.aria.sms.SmsConversation;
import com.taportal.aria.sms.SmsConversation.Heard;
import com.taportal.domain.journey.JourneyQueries;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Texts and emails, to and from candidates.
 *
 * <p>{@code POST /v1/sms/inbound} is the shape a carrier's webhook has: a
 * number, a text, the carrier's id for it. Today it is called by the portal
 * itself, playing the candidate's phone. It has no authentication, like every
 * endpoint of this proof of concept; a real carrier's webhook has to be
 * verified by the carrier's signature before it is switched on.
 */
@RestController
public class CandidateMessageController {

    private static final String SIMULATOR = "SIMULATOR";

    private final SmsConversation aria;
    private final JourneyQueries queries;

    public CandidateMessageController(SmsConversation aria, JourneyQueries queries) {
        this.aria = aria;
        this.queries = queries;
    }

    @GetMapping("/v1/candidates/{id}/messages")
    public Timeline timeline(@PathVariable UUID id) {
        return queries.timeline(id);
    }

    /** Play the candidate's phone: a text from their number, handled exactly as one from a carrier is. */
    @PostMapping("/v1/candidates/{id}/messages/reply")
    public InboundResult reply(@PathVariable UUID id, @RequestBody ReplyRequest request) {
        String phone = queries.phoneOf(id);
        return hear(phone, request == null ? null : request.body(), SIMULATOR, null);
    }

    @PostMapping("/v1/sms/inbound")
    public InboundResult inbound(@RequestBody InboundSms sms) {
        if (sms == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A text needs a sender and a body.");
        }
        return hear(sms.from(), sms.body(), SIMULATOR, sms.messageId());
    }

    private InboundResult hear(String from, String body, String provider, String messageId) {
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The text is empty.");
        }
        if (body.length() > 1600) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The text is too long to be a text message.");
        }
        Heard heard = aria.receive(from, body, provider, messageId);
        if (heard == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The sender is not a phone number a text can come from.");
        }
        return new InboundResult(heard.inboundId(), heard.understood(), heard.inWords(), heard.again(),
                heard.again() ? java.util.List.of() : queries.answersTo(heard.inboundId()));
    }
}
