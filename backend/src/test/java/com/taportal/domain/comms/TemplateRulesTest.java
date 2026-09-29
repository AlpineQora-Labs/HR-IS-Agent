package com.taportal.domain.comms;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TemplateRulesTest {

    private final TemplateRules rules = new TemplateRules();

    @Test
    void anOrdinaryTemplateCanBeDeletedAndTakenOutOfUse() {
        assertThatCode(() -> rules.mayDelete(null, "Offer letter")).doesNotThrowAnyException();
        assertThatCode(() -> rules.mayChange(null, "Offer letter", "ARCHIVED", "anything", true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a template that words a journey point cannot be deleted")
    void aJourneyTemplateCannotBeDeleted() {
        assertThatThrownBy(() -> rules.mayDelete("journey.interview_invite", "Interview invitation"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("can't be deleted")
                .hasMessageContaining("Interview invitation");
    }

    @Test
    void aJourneyTemplateHasToStayActive() {
        assertThatThrownBy(() -> rules.mayChange("journey.help", "Help", "DRAFT", "Reply STATUS", true))
                .hasMessageContaining("has to stay Active");
    }

    @Test
    @DisplayName("an invitation has to keep its times: it can be reworded, not emptied")
    void aJourneyTextKeepsWhatItsPointNeeds() {
        assertThatCode(() -> rules.mayChange("journey.interview_invite", "Interview invitation", "ACTIVE",
                "Pick one, {{first_name}}: {{slot_options}}", true)).doesNotThrowAnyException();

        assertThatThrownBy(() -> rules.mayChange("journey.interview_invite", "Interview invitation", "ACTIVE",
                "We would like to interview you, {{first_name}}.", true))
                .hasMessageContaining("{{slot_options}}")
                .hasMessageContaining("when the candidate is selected for interview");
    }

    @Test
    void olderFieldNamesCount() {
        assertThatCode(() -> rules.mayChange("journey.status_reply", "Status update", "ACTIVE",
                "Update: {{status}}", true)).doesNotThrowAnyException();
    }

    @Test
    void anEmailIsNotHeldToTheFieldsOfAText() {
        assertThatCode(() -> rules.mayChange("journey.interview_invite", "Interview invitation", "ACTIVE",
                "<p>Choose a time: {{link}}</p>", false)).doesNotThrowAnyException();
    }
}
