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
    @DisplayName("a letter the basic set lacks keeps its letter and loses only its mark")
    void aNameLosesItsAccentNotItsLetter() {
        assertThat(SmsText.plain("Hi María, Sofía, Jesús, Zoë, François, Ramón"))
                .isEqualTo("Hi Maria, Sofia, Jesus, Zoe, Francois, Ramon");
        assertThat(SmsText.plain("Łukasz Škoda, Øystein, Renée")).isEqualTo("Lukasz Skoda, Øystein, Renée");
        assertThat(SmsText.plain("Đorđe Œuvre")).isEqualTo("Dorde OEuvre");
    }

    @Test
    void whatHasNoPlainFormIsStillLeftOut() {
        assertThat(SmsText.plain("Hi 李 and Дмитрий")).isEqualTo("Hi and");
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
    @DisplayName("a wording is counted as it would be sent, the way the candidate's record counts it")
    void aWordingIsMeasuredAsSent() {
        assertThat(SmsText.measure("a".repeat(160))).isEqualTo(new SmsText.Measure("a".repeat(160), 160, 1));
        assertThat(SmsText.measure("a".repeat(161)).parts()).isEqualTo(2);
        assertThat(SmsText.measure("a".repeat(306)).parts()).isEqualTo(2);
        assertThat(SmsText.measure("a".repeat(307)).parts()).isEqualTo(3);
        assertThat(SmsText.measure("a".repeat(320)).parts()).as("320 characters is three parts, not two").isEqualTo(3);
    }

    @Test
    void aEuroSignCountsTwice() {
        SmsText.Measure m = SmsText.measure("€" + "a".repeat(159));
        assertThat(m.characters()).isEqualTo(161);
        assertThat(m.parts()).isEqualTo(2);
        assertThat(SmsText.measure("€" + "a".repeat(158)).parts()).isEqualTo(1);
    }

    @Test
    @DisplayName("curly quotes are counted straight and an emoji is not counted, since neither is sent")
    void theCountIsOfThePlainText() {
        SmsText.Measure m = SmsText.measure("You’re “in” 📅");
        assertThat(m.plainText()).isEqualTo("You're \"in\"");
        assertThat(m.characters()).isEqualTo(11);
    }

    @Test
    void pastTwoPartsIsTooLong() {
        assertThat(SmsText.measure("a".repeat(306)).tooLong()).isFalse();
        assertThat(SmsText.measure("a".repeat(307)).tooLong()).isTrue();
    }

    @Test
    void noWordingIsNoParts() {
        assertThat(SmsText.measure("  📅 ")).isEqualTo(new SmsText.Measure("", 0, 0));
        assertThat(SmsText.measure(null).parts()).isZero();
    }

    @Test
    void nothingIsNothing() {
        assertThat(SmsText.plain(null)).isEmpty();
    }
}
