package com.taportal.aria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Reading which time a candidate chose in the chat")
class ScriptedInterpreterTest {

    private static final List<String> TIMES = List.of(
            "Thu, Oct 1 at 10:00 AM ET", "Thu, Oct 1 at 12:30 PM ET", "Fri, Oct 2 at 10:00 AM ET");

    private final ScriptedInterpreter interpreter = new ScriptedInterpreter();

    @Test
    @DisplayName("a time pressed is the time booked, whatever digits its date holds")
    void theTimeItselfIsTheTime() {
        assertThat(interpreter.chooseSlot(TIMES, "Thu, Oct 1 at 12:30 PM ET")).isEqualTo(2);
        assertThat(interpreter.chooseSlot(TIMES, "fri, oct 2 at 10:00 am et")).isEqualTo(3);
        assertThat(interpreter.chooseSlot(TIMES, "  Thu, Oct 1 at 10:00 AM ET ")).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,2", "3,3", "option 2,2", "#3,3", "Number 1,1"})
    void aNumberAlonePicksByPosition(String said, int expected) {
        assertThat(interpreter.chooseSlot(TIMES, said)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"4", "0", "9", "12", "can we do 2pm instead", "Oct 1", "the 2nd works", "10:00 AM", "''"})
    @DisplayName("a number in a sentence, a number nobody offered, or a time two offers share picks nothing")
    void nothingIsGuessed(String said) {
        assertThat(interpreter.chooseSlot(TIMES, said)).isZero();
    }

    @Test
    void partOfATimePicksItWhenItCanMeanOnlyOne() {
        assertThat(interpreter.chooseSlot(TIMES, "12:30 PM")).isEqualTo(2);
        assertThat(interpreter.chooseSlot(TIMES, "Fri, Oct 2")).isEqualTo(3);
    }

    @Test
    void nothingOfferedNothingChosen() {
        assertThat(interpreter.chooseSlot(List.of(), "1")).isZero();
        assertThat(interpreter.chooseSlot(null, "1")).isZero();
        assertThat(interpreter.chooseSlot(TIMES, null)).isZero();
    }
}
