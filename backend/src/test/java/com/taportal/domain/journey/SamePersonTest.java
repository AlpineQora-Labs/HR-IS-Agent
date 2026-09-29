package com.taportal.domain.journey;

import static org.assertj.core.api.Assertions.assertThat;

import com.taportal.domain.candidate.Candidate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Whether records that share a phone number are one person")
class SamePersonTest {

    private static Candidate record(String name, String email) {
        Candidate c = new Candidate();
        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
        c.setName(name);
        c.setEmail(email);
        return c;
    }

    @Test
    void theSameEmailIsTheSamePersonWhateverItsCase() {
        assertThat(SamePerson.same(record("Raj Patel", "raj@example.com"), record("R. Patel", " RAJ@Example.com ")))
                .isTrue();
    }

    @Test
    void theSameNameIsTheSamePerson() {
        assertThat(SamePerson.same(record("Raj  Patel", "raj@example.com"), record("raj patel", "rp@work.example")))
                .isTrue();
    }

    @Test
    @DisplayName("a different name and a different email is somebody else: a shared phone, or a digit mistyped")
    void differentNameAndEmailIsSomebodyElse() {
        assertThat(SamePerson.same(record("Raj Patel", "raj@example.com"), record("Maria Gomez", "maria@example.com")))
                .isFalse();
    }

    @Test
    void nothingKnownOfEitherMatchesNothing() {
        assertThat(SamePerson.same(record("Unknown", ""), record("Unknown", ""))).isFalse();
        assertThat(SamePerson.same(record(null, null), record(null, null))).isFalse();
    }

    @Test
    void oneRecordOrNoneIsOnePerson() {
        assertThat(SamePerson.all(List.of())).isTrue();
        assertThat(SamePerson.all(List.of(record("Raj Patel", "raj@example.com")))).isTrue();
    }

    @Test
    void oneStrangerAmongThemMakesTheNumberShared() {
        assertThat(SamePerson.all(List.of(
                record("Raj Patel", "raj@example.com"),
                record("Raj Patel", "other@example.com"),
                record("Maria Gomez", "maria@example.com")))).isFalse();
    }
}
