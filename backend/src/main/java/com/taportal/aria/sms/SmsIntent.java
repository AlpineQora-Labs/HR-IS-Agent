package com.taportal.aria.sms;

/**
 * What a candidate's text message asks for.
 *
 * @param choice the number picked, for {@link Kind#PICK}; 0 otherwise
 */
public record SmsIntent(Kind kind, int choice) {

    public enum Kind {
        /** STOP: no more texts. */
        OPT_OUT("Stop texting me"),
        /** START: texts again. */
        OPT_IN("Text me again"),
        HELP("Help"),
        /** Where does my application stand? */
        STATUS("Where does my application stand"),
        /** "2": the second time offered. */
        PICK("A time, picked by its number"),
        /** None of the times offered work; offer others. */
        MORE("Other times"),
        /** Show me the times again. */
        TIMES("Show me the times"),
        RESCHEDULE("Move my interview"),
        /** "Thanks", "ok": nothing is being asked. */
        THANKS("Thanks; nothing is asked"),
        /** Not something Aria can act on by text. */
        UNCLEAR("Not understood");

        private final String words;

        Kind(String words) {
            this.words = words;
        }

        /** What the text was taken to mean, as the screens say it. */
        public String words() {
            return words;
        }
    }

    static SmsIntent of(Kind kind) {
        return new SmsIntent(kind, 0);
    }

    static SmsIntent pick(int choice) {
        return new SmsIntent(Kind.PICK, choice);
    }
}
