package com.taportal.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Whether a number was given with agreement to be texted")
class TextAgreementTest {

    @ParameterizedTest(name = "\"{0}\" agrees")
    @NullSource
    @ValueSource(strings = {
        "704-555-0101", "(704) 555 0101", "my cell is 704 555 0101", "Sure, 704-555-0101. Text me any time",
        "+1 704 555 0101 thanks", "", "it's 7045550101, text is best"})
    void aNumberGivenIsAgreement(String answer) {
        assertThat(TextAgreement.refused(answer)).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" does not")
    @ValueSource(strings = {
        "You can call me on 704-555-0101 but please don't text me", "704 555 0101, no texts please",
        "704-555-0101 (landline)", "Calls only: 704 555 0101", "704 555 0101 - do not text",
        "704-555-0101, I can’t receive texts", "Please do not message me. 704 555 0101",
        "704 555 0101 but I'd rather not be texted", "only call, 7045550101"})
    void aNumberGivenWithARefusalIsNot(String answer) {
        assertThat(TextAgreement.refused(answer)).isTrue();
    }
}
