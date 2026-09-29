package com.taportal.domain.journey;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The points of the candidate journey at which a message goes out.
 *
 * <p>The name is the stored key (it is written into the drawn workflow and
 * onto every message), so a point is never renamed — only its label is.
 */
public enum JourneyPoint {

    // ---- on the drawing: what the journey sends ----
    APPLICATION_RECEIVED("When the candidate applies", Kind.UNPROMPTED, true, Set.of()),
    STATUS_REPLY("When the candidate asks for their status", Kind.REPLY, false, Set.of("status_summary")),
    INTERVIEW_INVITE("When the candidate is selected for interview", Kind.UNPROMPTED, true, Set.of("slot_options")),
    INTERVIEW_CONFIRMED("When the interview is booked", Kind.UNPROMPTED, true, Set.of("interview_time")),
    RESCHEDULE_OPTIONS("When the candidate asks to reschedule", Kind.REPLY, false, Set.of("slot_options")),
    INTERVIEW_REMINDER_24H("24 hours before the interview", Kind.UNPROMPTED, true, Set.of("interview_time")),
    INTERVIEW_REMINDER_1H("1 hour before the interview", Kind.UNPROMPTED, false, Set.of("interview_time")),

    // ---- not drawn: what the hiring team's own actions make necessary ----
    TEAM_RESCHEDULED("When the hiring team moves an interview", Kind.NOTICE, true, Set.of("slot_options")),
    INTERVIEW_CANCELED("When the hiring team cancels an interview", Kind.NOTICE, true, Set.of("interview_time")),
    INVITE_BY_LINK("When a second invitation goes out while one is open", Kind.NOTICE, false, Set.of("link")),

    // ---- not drawn: what the system answers when a candidate texts ----
    MORE_TIMES("When the candidate asks for other times", Kind.SERVICE, false, Set.of("slot_options")),
    NO_OTHER_TIMES("When there are no other times to offer", Kind.SERVICE, false, Set.of("slot_options")),
    SLOT_TAKEN("When the time picked is no longer available", Kind.SERVICE, false, Set.of("slot_options")),
    NO_TIMES_AVAILABLE("When no times can be found", Kind.SERVICE, false, Set.of()),
    RESCHEDULE_DECLINED("When a reschedule is not allowed", Kind.SERVICE, false, Set.of("reason")),
    RESCHEDULE_NO_TIMES("When there is nothing to reschedule to", Kind.SERVICE, false, Set.of("interview_time")),
    NOTHING_TO_RESCHEDULE("When there is no interview to move", Kind.SERVICE, false, Set.of()),
    ALREADY_BOOKED("When a time is picked but the interview is booked", Kind.SERVICE, false, Set.of("interview_time")),
    PICK_A_NUMBER("When the reply is not one of the times offered", Kind.SERVICE, false, Set.of("slot_options")),
    HELP("When the candidate asks for help, or is not understood", Kind.SERVICE, false, Set.of()),
    OPT_OUT("When the candidate opts out of texts", Kind.SERVICE, false, Set.of()),
    OPT_IN("When the candidate opts back in", Kind.SERVICE, false, Set.of()),
    UNKNOWN_SENDER("When a text comes from a number we do not know", Kind.SERVICE, false, Set.of());

    public enum Kind {
        /** Sent because something happened. Goes out on a channel only when its step is on the drawing. */
        UNPROMPTED,
        /** An answer to the candidate's own text, drawn so that its wording can be chosen there. */
        REPLY,
        /** Sent because the hiring team did something the candidate must hear of. Not drawn. */
        NOTICE,
        /** An answer to the candidate's own text, worded in Communications only. */
        SERVICE
    }

    private final String label;
    private final Kind kind;
    private final boolean email;
    private final Set<String> needs;

    JourneyPoint(String label, Kind kind, boolean email, Set<String> needs) {
        this.label = label;
        this.kind = kind;
        this.email = email;
        this.needs = needs;
    }

    /** "Sent when…", as the screens show it. */
    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    /** True when the message answers a text the candidate sent. */
    public boolean answers() {
        return kind == Kind.REPLY || kind == Kind.SERVICE;
    }

    /** True when the point can be placed on the workflow drawing. */
    public boolean drawn() {
        return kind == Kind.UNPROMPTED || kind == Kind.REPLY;
    }

    /** Every point can be texted. */
    public boolean byText() {
        return true;
    }

    public boolean byEmail() {
        return email;
    }

    /** The fields a text for this point must carry to be of use: an invitation without times is not one. */
    public Set<String> needs() {
        return needs;
    }

    /** The key of the template that words this point unless the drawing names another. */
    public String templateKey() {
        return "journey." + name().toLowerCase();
    }

    public static Optional<JourneyPoint> of(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(p -> p.name().equalsIgnoreCase(key.trim())).findFirst();
    }

    public static Optional<JourneyPoint> ofTemplateKey(String templateKey) {
        if (templateKey == null || !templateKey.startsWith("journey.")) {
            return Optional.empty();
        }
        return of(templateKey.substring("journey.".length()));
    }

    public static List<JourneyPoint> onTheDrawing() {
        return Arrays.stream(values()).filter(JourneyPoint::drawn).toList();
    }
}
