package com.taportal.domain.messaging;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether a message may go to an address. The rules:
 *
 * <ol>
 *   <li>A text needs a number a text can reach.
 *   <li>A number that said STOP is texted nothing, except the confirmation of
 *       that and the answers to HELP and START.
 *   <li>A text nobody asked for needs the candidate's agreement to be texted.
 *       An answer to a text they sent does not.
 *   <li>An email needs an address that can be one.
 * </ol>
 */
@Service
public class MessagePolicy {

    /** What a number that opted out may still be told. */
    private static final Set<String> AFTER_STOP = Set.of("OPT_OUT", "OPT_IN", "HELP");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private final SmsOptOutRepository optOuts;

    public MessagePolicy(SmsOptOutRepository optOuts) {
        this.optOuts = optOuts;
    }

    /**
     * @param send   true when the message may go
     * @param reason why not, in words a recruiter reads on the timeline
     */
    public record Decision(boolean send, String reason) {

        static final Decision YES = new Decision(true, null);

        static Decision no(String reason) {
            return new Decision(false, reason);
        }
    }

    /**
     * @param consentAt when the candidate agreed to be texted; null when they have not
     * @param answering true when this answers a text the candidate sent
     * @param point     the journey point, by key
     */
    public Decision text(String phoneE164, OffsetDateTime consentAt, boolean answering, String point) {
        if (phoneE164 == null || phoneE164.isBlank()) {
            return Decision.no("No mobile number a text can reach");
        }
        if (optedOut(phoneE164) && !AFTER_STOP.contains(point)) {
            return Decision.no("This number opted out of texts (STOP)");
        }
        if (!answering && consentAt == null) {
            return Decision.no("No agreement to be texted on record");
        }
        return Decision.YES;
    }

    public Decision email(String address) {
        if (address == null || address.isBlank()) {
            return Decision.no("No email address");
        }
        if (!EMAIL.matcher(address.trim()).matches()) {
            return Decision.no("The email address on record is not a valid address");
        }
        return Decision.YES;
    }

    public boolean optedOut(String phoneE164) {
        return phoneE164 != null && optOuts.existsById(phoneE164);
    }

    @Transactional
    public void optOut(String phoneE164) {
        if (!optOuts.existsById(phoneE164)) {
            optOuts.save(new SmsOptOut(phoneE164));
        }
    }

    @Transactional
    public void optIn(String phoneE164) {
        if (optOuts.existsById(phoneE164)) {
            optOuts.deleteById(phoneE164);
        }
    }
}
