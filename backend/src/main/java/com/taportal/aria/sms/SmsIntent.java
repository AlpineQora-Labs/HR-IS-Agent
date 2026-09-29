package com.taportal.aria.sms;

/**
 * What a candidate's text message asks for.
 *
 * @param choice the number picked, for {@link Kind#PICK}; 0 otherwise
 */
public record SmsIntent(Kind kind, int choice) {

    public enum Kind {
        /** STOP: no more texts. */
        OPT_OUT,
        /** START: texts again. */
        OPT_IN,
        HELP,
        /** Where does my application stand? */
        STATUS,
        /** "2": the second time offered. */
        PICK,
        /** None of the times offered work; offer others. */
        MORE,
        /** Show me the times again. */
        TIMES,
        RESCHEDULE,
        /** "Thanks", "ok": nothing is being asked. */
        THANKS,
        /** Not something Aria can act on by text. */
        UNCLEAR
    }

    static SmsIntent of(Kind kind) {
        return new SmsIntent(kind, 0);
    }

    static SmsIntent pick(int choice) {
        return new SmsIntent(Kind.PICK, choice);
    }
}
