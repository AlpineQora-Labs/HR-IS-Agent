package com.taportal.aria.sms;

import com.taportal.domain.journey.JourneyService;
import com.taportal.domain.journey.JourneyUnits.Inbound;
import com.taportal.domain.journey.JourneyUnits.Texter;
import com.taportal.domain.messaging.PhoneNumbers;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Aria by text message. One text comes in; Aria reads what it asks for and
 * hands that to the candidate journey, which knows what may be done and what
 * is to be said.
 *
 * <p>Aria understands; the journey decides. Nothing about consent, policy,
 * wording or scheduling is settled here — see JourneyService.
 *
 * <p>There is no transaction here. The journey's steps each commit on their
 * own, and a scheduling refusal in one must not undo the record of the text
 * that asked.
 */
@Service
public class SmsConversation {

    private final JourneyService journey;

    public SmsConversation(JourneyService journey) {
        this.journey = journey;
    }

    /**
     * @param inboundId the record of the text, null when the sender's number could not be read
     * @param address   the sender's number in standard form
     * @param understood what the text was taken to ask for
     * @param again     true when this text had been handled already and nothing was done
     */
    public record Heard(UUID inboundId, String address, String understood, boolean again) {
    }

    /**
     * @param from              the sender's number, as the carrier gives it
     * @param provider          who delivered it
     * @param providerMessageId the carrier's id for the text; a text delivered twice has the same one
     * @return what was heard, or null when {@code from} is not a phone number
     */
    public Heard receive(String from, String text, String provider, String providerMessageId) {
        String address = PhoneNumbers.normalise(from);
        if (address == null) {
            return null;
        }
        Inbound inbound = journey.receive(address, text, provider, providerMessageId);
        Texter texter = inbound.texter();
        SmsIntent intent = SmsIntentParser.parse(text);
        if (inbound.again()) {
            return new Heard(texter.inboundId(), address, intent.kind().name(), true);
        }
        act(texter, intent, text);
        journey.handled(texter);
        return new Heard(texter.inboundId(), address, intent.kind().name(), false);
    }

    private void act(Texter texter, SmsIntent intent, String text) {
        // A number that said STOP is answered only when it asks for help or to come back.
        if (journey.optedOut(texter)) {
            switch (intent.kind()) {
                case OPT_IN -> journey.optIn(texter);
                case HELP -> journey.help(texter);
                case OPT_OUT -> journey.optOut(texter);
                default -> journey.notActedOn(texter, shortened(text));
            }
            return;
        }
        switch (intent.kind()) {
            case OPT_OUT -> journey.optOut(texter);
            case OPT_IN -> journey.optIn(texter);
            case HELP -> journey.help(texter);
            case STATUS -> journey.status(texter);
            case PICK -> journey.pick(texter, intent.choice());
            case MORE -> journey.more(texter);
            case TIMES -> journey.times(texter);
            case RESCHEDULE -> journey.reschedule(texter);
            case THANKS -> { /* nothing was asked: nothing is answered */ }
            case UNCLEAR -> journey.unclear(texter);
        }
    }

    private static String shortened(String text) {
        String t = text == null ? "" : text.trim();
        return t.length() <= 40 ? t : t.substring(0, 39) + "…";
    }
}
