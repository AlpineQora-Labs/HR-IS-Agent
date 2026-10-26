package com.taportal.domain.briefing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Names out before a model sees the text, and back in before a recruiter reads it. */
class PeopleNamesTest {

    final Map<String, String> people = new LinkedHashMap<>(Map.of(
            "[C1]", "Maya Thompson",
            "[S1]", "Mark Ellis",
            "[S2]", "Maya Chen"));

    @Test
    void fullNamesAreHiddenInAnyCase() {
        assertThat(PeopleNames.hide(people, "Why is maya thompson waiting, and what did MARK ELLIS say?"))
                .isEqualTo("Why is [C1] waiting, and what did [S1] say?");
    }

    @Test
    void aFirstNameAloneIsHiddenOnlyWhenOnePersonHasIt() {
        // Two people are called Maya, so "Maya" alone could be either and is left; Mark is one person.
        assertThat(PeopleNames.hide(people, "Did Maya reply to Mark's text?")).isEqualTo("Did Maya reply to [S1]'s text?");
    }

    @Test
    void aFirstNameThatIsAlsoAWordIsLeftAloneInLowerCase() {
        assertThat(PeopleNames.hide(people, "Should I mark it completed?")).isEqualTo("Should I mark it completed?");
    }

    @Test
    void placeholdersAreReplacedByTheNames() {
        assertThat(PeopleNames.show(people, "[C1]'s interview with [S1] and [S2] is booked."))
                .isEqualTo("Maya Thompson's interview with Mark Ellis and Maya Chen is booked.");
    }

    @Test
    void nothingToHideLeavesTheTextAsItIs() {
        assertThat(PeopleNames.hide(Map.of(), "Why is Maya waiting?")).isEqualTo("Why is Maya waiting?");
        assertThat(PeopleNames.hide(people, null)).isNull();
    }
}
