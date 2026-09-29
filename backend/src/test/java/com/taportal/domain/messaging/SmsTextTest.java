package com.taportal.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SmsTextTest {

    @Test
    @DisplayName("look-alike characters are made plain, so one curly quote does not turn a text into 70-character parts")
    void lookAlikesAreMadePlain() {
        assertThat(SmsText.plain("You’re “confirmed” — Tue…"))
                .isEqualTo("You're \"confirmed\" - Tue...");
    }

    @Test
    void whatAPhoneMayNotShowIsLeftOut() {
        assertThat(SmsText.plain("Friendly reminder! 📅 See you ☃ soon")).isEqualTo("Friendly reminder! See you soon");
    }

    @Test
    void linesAreKeptAndTidied() {
        assertThat(SmsText.plain("Pick a time:  \n1) Tue\n2) Wed \n")).isEqualTo("Pick a time:\n1) Tue\n2) Wed");
    }

    @Test
    void accentedLettersEveryPhoneHasAreKept() {
        assertThat(SmsText.plain("José Ñ ü")).isEqualTo("José Ñ ü");
    }

    @Test
    void partsAreCountedTheWayACarrierBills() {
        assertThat(SmsText.parts("a".repeat(160))).isEqualTo(1);
        assertThat(SmsText.parts("a".repeat(161))).isEqualTo(2);
        assertThat(SmsText.parts("a".repeat(306))).isEqualTo(2);
        assertThat(SmsText.parts("a".repeat(307))).isEqualTo(3);
        assertThat(SmsText.parts("[" + "a".repeat(158))).as("a bracket counts twice").isEqualTo(1);
        assertThat(SmsText.parts("[" + "a".repeat(159))).isEqualTo(2);
    }

    @Test
    void nothingIsNothing() {
        assertThat(SmsText.plain(null)).isEmpty();
    }
}
