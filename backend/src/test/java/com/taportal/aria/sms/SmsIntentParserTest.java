package com.taportal.aria.sms;

import static org.assertj.core.api.Assertions.assertThat;

import com.taportal.aria.sms.SmsIntent.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SmsIntentParserTest {

    private static Kind kind(String text) {
        return SmsIntentParser.parse(text).kind();
    }

    @ParameterizedTest(name = "\"{0}\" stops texts")
    @ValueSource(strings = {
        "STOP", "stop", " Stop. ", "STOPALL", "stop all", "Unsubscribe", "END", "quit", "STOP!", "CANCEL", "cancel",
        "Revoke", "opt out", "Stop please", "stop texting me", "STOP sending these"})
    void stopWords(String text) {
        assertThat(kind(text)).isEqualTo(Kind.OPT_OUT);
    }

    @ParameterizedTest(name = "\"{0}\" starts texts again")
    @ValueSource(strings = {"START", "start", "Unstop", "subscribe"})
    void startWords(String text) {
        assertThat(kind(text)).isEqualTo(Kind.OPT_IN);
    }

    @ParameterizedTest(name = "\"{0}\" picks time {1}")
    @CsvSource({"1, 1", "2, 2", "' 3 ', 3", "2., 2", "#2, 2", "option 2, 2", "Option 3!, 3", "number 1, 1"})
    void aNumberOnItsOwnPicksATime(String text, int choice) {
        SmsIntent intent = SmsIntentParser.parse(text);

        assertThat(intent.kind()).isEqualTo(Kind.PICK);
        assertThat(intent.choice()).isEqualTo(choice);
    }

    @ParameterizedTest(name = "\"{0}\" books nothing")
    @ValueSource(strings = {
        "can we do 2pm instead", "maybe in 3 weeks", "2pm", "10", "0", "1 or 2", "I think 2 works",
        "option", "2 3", "the 2nd", "thanks!", "yes", "ok", "who is this?", "call me at 704 555 0101", "2 works", "12"})
    @DisplayName("a number inside a sentence is not a choice")
    void aNumberInsideASentenceBooksNothing(String text) {
        assertThat(kind(text)).isNotEqualTo(Kind.PICK);
    }

    @ParameterizedTest(name = "\"{0}\" asks for status")
    @ValueSource(strings = {"STATUS", "status?", "What's my status", "application status please", "update", "any update?", "Any news"})
    void status(String text) {
        assertThat(kind(text)).isEqualTo(Kind.STATUS);
    }

    @ParameterizedTest(name = "\"{0}\" asks to move the interview")
    @ValueSource(strings = {
        "RESCHEDULE", "reschedule please", "I need to reschedule", "can't make it", "Can’t make it",
        "cannot make that time", "can we change the time", "need a different time", "move my interview"})
    void reschedule(String text) {
        assertThat(kind(text)).isEqualTo(Kind.RESCHEDULE);
    }

    @ParameterizedTest(name = "\"{0}\" asks for other times")
    @ValueSource(strings = {"MORE", "none", "none of these work", "None of them", "other times?", "those don't work", "any other days"})
    void moreTimes(String text) {
        assertThat(kind(text)).isEqualTo(Kind.MORE);
    }

    @ParameterizedTest(name = "\"{0}\" asks to see the times")
    @ValueSource(strings = {"TIMES", "slots", "options"})
    void times(String text) {
        assertThat(kind(text)).isEqualTo(Kind.TIMES);
    }

    @ParameterizedTest(name = "\"{0}\" asks for help")
    @ValueSource(strings = {"HELP", "help!", "info", "?"})
    void help(String text) {
        assertThat(kind(text)).isEqualTo(Kind.HELP);
    }

    @ParameterizedTest(name = "\"{0}\" is not understood")
    @NullSource
    @ValueSource(strings = {"", "   ", "who is this?", "hello", "...", "end of the week works", "cancel my interview", "quit asking"})
    void everythingElseIsUnclear(String text) {
        assertThat(kind(text)).isEqualTo(Kind.UNCLEAR);
    }

    @ParameterizedTest(name = "\"{0}\" asks for nothing")
    @ValueSource(strings = {"thanks!", "Thank you", "thanks so much", "ok", "OK.", "yes", "got it", "sounds good", "C"})
    void thanksAsksForNothing(String text) {
        assertThat(kind(text)).isEqualTo(Kind.THANKS);
    }

    @Test
    @DisplayName("the words a carrier acts on are acted on here too, CANCEL included")
    void carrierOptOutWordsOptOut() {
        for (String word : new String[] {"STOP", "STOPALL", "UNSUBSCRIBE", "CANCEL", "END", "QUIT"}) {
            assertThat(kind(word)).as(word).isEqualTo(Kind.OPT_OUT);
        }
    }

    @Test
    @DisplayName("a sentence that only contains an opt-out word is not an opt-out")
    void anOptOutWordInsideASentenceIsNot() {
        assertThat(kind("please stop sending options, reschedule instead")).isEqualTo(Kind.RESCHEDULE);
        assertThat(kind("stop, I need to reschedule")).isEqualTo(Kind.RESCHEDULE);
        assertThat(kind("cancel my interview")).isNotEqualTo(Kind.OPT_OUT);
        assertThat(kind("the end")).isNotEqualTo(Kind.OPT_OUT);
    }
}
