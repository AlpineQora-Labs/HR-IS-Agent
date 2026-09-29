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
        "Revoke", "opt out", "Stop please", "stop texting me", "STOP sending these",
        // said in a sentence, and meant
        "Please stop texting me", "Opt-out", "unsubscribe me", "quit asking", "do not text me", "Don't text me again",
        "\"STOP\"", "\u201cStop\u201d", "please remove me from this list", "no more texts please",
        "take me off your list", "I want to opt out"})
    void stopWords(String text) {
        assertThat(kind(text)).isEqualTo(Kind.OPT_OUT);
    }

    @ParameterizedTest(name = "\"{0}\" starts texts again")
    @ValueSource(strings = {"START", "start", "Unstop", "subscribe"})
    void startWords(String text) {
        assertThat(kind(text)).isEqualTo(Kind.OPT_IN);
    }

    @ParameterizedTest(name = "\"{0}\" picks time {1}")
    @CsvSource({"1, 1", "2, 2", "' 3 ', 3", "2., 2", "#2, 2", "option 2, 2", "Option 3!, 3", "number 1, 1",
        "2 please, 2", "Option 2 please, 2"})
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
        "RESCHEDULE", "reschedule", "reschedule please", "Please reschedule", "I need to reschedule",
        "I want to reschedule my interview", "Can we reschedule?", "could I reschedule", "re-schedule",
        "I'd like to reschedule it"})
    @DisplayName("RESCHEDULE asked for plainly moves the interview")
    void reschedule(String text) {
        assertThat(kind(text)).isEqualTo(Kind.RESCHEDULE);
    }

    @ParameterizedTest(name = "\"{0}\" may be asking; nothing is moved on the strength of it")
    @ValueSource(strings = {
        "can't make it", "Can’t make it", "cannot make that time", "can we change the time",
        "need a different time", "move my interview", "I won't be able to make it",
        "thanks, but I need to reschedule", "stop, I need to reschedule", "Do I need to reschedule?",
        "please stop sending options, reschedule instead", "could we find another time"})
    void aSentenceThatMayAskToMoveTheInterviewIsOnlyAHint(String text) {
        assertThat(kind(text)).isEqualTo(Kind.RESCHEDULE_HINTED);
    }

    @ParameterizedTest(name = "\"{0}\" moves nothing")
    @ValueSource(strings = {
        "Thanks for rescheduling!", "The new time works, see you then", "No need to reschedule, I'll be there",
        "What's the status of my reschedule?", "I rescheduled my flight so I can make it", "never mind rescheduling",
        "I don't need to reschedule", "the different time zone confused me", "new time is great"})
    @DisplayName("a word about a move that was made, or is not wanted, is not a request to move")
    void speakingOfAMoveIsNotAskingForOne(String text) {
        assertThat(kind(text)).isNotIn(Kind.RESCHEDULE, Kind.RESCHEDULE_HINTED);
    }

    @Test
    @DisplayName("where a text can be read two ways, the reading that changes nothing wins")
    void theHarmlessReadingWins() {
        assertThat(kind("What's the status of my reschedule?")).isEqualTo(Kind.STATUS);
        assertThat(kind("Thanks for rescheduling!")).isEqualTo(Kind.THANKS);
        assertThat(kind("No need to reschedule, I'll be there")).isEqualTo(Kind.THANKS);
        assertThat(kind("The new time works, see you then")).isEqualTo(Kind.THANKS);
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
    @ValueSource(strings = {"", "   ", "who is this?", "hello", "...", "end of the week works", "cancel my interview",
        "please cancel my interview", "I can't stop thinking about this role", "2 pm", "1 or 2", "ok 2"})
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
        assertThat(kind("please stop sending options, reschedule instead")).isNotEqualTo(Kind.OPT_OUT);
        assertThat(kind("stop, I need to reschedule")).isNotEqualTo(Kind.OPT_OUT);
        assertThat(kind("cancel my interview")).isNotEqualTo(Kind.OPT_OUT);
        assertThat(kind("the end")).isNotEqualTo(Kind.OPT_OUT);
        assertThat(kind("I can't stop thinking about this role")).isNotEqualTo(Kind.OPT_OUT);
        assertThat(kind("end of day works")).isNotEqualTo(Kind.OPT_OUT);
    }
}
