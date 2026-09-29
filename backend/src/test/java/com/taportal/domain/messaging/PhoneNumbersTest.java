package com.taportal.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({
        "+1-704-555-0101, +17045550101",
        "(704) 555-0101, +17045550101",
        "704.555.0101, +17045550101",
        "7045550101, +17045550101",
        "1 704 555 0101, +17045550101",
        "+44 20 7946 0958, +442079460958",
        "'  +17045550101  ', +17045550101",
        "704-555-0101 x12, +17045550101",
        "704-555-0101 ext. 12, +17045550101",
    })
    void oneNumberHasOneForm(String typed, String expected) {
        assertThat(PhoneNumbers.normalise(typed)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is not a number a text can reach")
    @NullSource
    @ValueSource(strings = {
        "", "   ", "call me later", "555-0101", "0704 555 0101", "+0 704 555 0101",
        "12345", "704-555-0101 or 704-555-0102", "my number is 7045550101",
        "+1234567890123456", "1045550101", "11045550101"})
    void whatIsNotANumberIsRefused(String typed) {
        assertThat(PhoneNumbers.normalise(typed)).isNull();
    }

    @Test
    @DisplayName("a number typed two ways is the same number")
    void twoWaysOfTypingMatch() {
        assertThat(PhoneNumbers.normalise("+1-212-555-0109")).isEqualTo(PhoneNumbers.normalise("(212) 555 0109"));
    }

    @Test
    void aNumberIsNeverShownWhole() {
        assertThat(PhoneNumbers.masked("+17045550101")).isEqualTo("the number ending 0101");
        assertThat(PhoneNumbers.masked(null)).isEqualTo("a number");
    }
}
