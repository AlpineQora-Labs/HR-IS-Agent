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
        assertThatCode(() -> rules.mayChange(null, "Offer letter", "ARCHIVED", null, "anything {{whatever}}", true))
                .doesNotThrowAnyException();
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
        assertThatThrownBy(() -> rules.mayChange("journey.help", "Help", "DRAFT", null, "Reply STATUS", true))
                .hasMessageContaining("has to stay Active");
    }

    @Test
    @DisplayName("an invitation has to keep its times: it can be reworded, not emptied")
    void aJourneyTextKeepsWhatItsPointNeeds() {
        assertThatCode(() -> rules.mayChange("journey.interview_invite", "Interview invitation", "ACTIVE", null,
                "Pick one, {{first_name}}: {{slot_options}}", true)).doesNotThrowAnyException();

        assertThatThrownBy(() -> rules.mayChange("journey.interview_invite", "Interview invitation", "ACTIVE", null,
                "We would like to interview you, {{first_name}}.", true))
                .hasMessageContaining("{{slot_options}}")
                .hasMessageContaining("when the candidate is selected for interview");
    }

    @Test
    void olderFieldNamesCount() {
        assertThatCode(() -> rules.mayChange("journey.status_reply", "Status update", "ACTIVE", null,
                "Update: {{status}}", true)).doesNotThrowAnyException();
    }

    @Test
    void anEmailIsNotHeldToTheFieldsOfAText() {
        assertThatCode(() -> rules.mayChange("journey.interview_invite", "Interview invitation", "ACTIVE",
                "Choose a time, {{first_name}}", "<p>Choose a time: {{link}}</p>", false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a field with no value when the message is sent is refused: the message could never go")
    void aJourneyTemplateUsesOnlyWhatItsPointHas() {
        // Nobody in particular is being answered when a number opts out: there is no role to name.
        assertThatThrownBy(() -> rules.mayChange("journey.opt_out", "Opted out of texts", "ACTIVE", null,
                "{{company_name}}: You are unsubscribed from texts about {{job_title}}.", true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("{{job_title}}")
                .hasMessageContaining("could never be sent");

        // An invitation is sent before there is a time to speak of.
        assertThatThrownBy(() -> rules.mayChange("journey.interview_invite", "Interview invitation", "ACTIVE", null,
                "Your interview is {{interview_time}}. {{slot_options}}", true))
                .hasMessageContaining("{{interview_time}}");

        // A field the journey does not have at all.
        assertThatThrownBy(() -> rules.mayChange("journey.interview_confirmed", "Interview confirmed", "ACTIVE",
                "Confirmed", "<p>See you at {{event_name}}, {{interview_time}}</p>", false))
                .hasMessageContaining("{{event_name}}");
    }

    @Test
    @DisplayName("an email's subject is held to the same rule as its body")
    void theSubjectCounts() {
        assertThatThrownBy(() -> rules.mayChange("journey.application_received", "Application received", "ACTIVE",
                "Your interview, {{interview_time}}", "<p>Thank you, {{first_name}}.</p>", false))
                .hasMessageContaining("{{interview_time}}");
    }

    @Test
    void whatThePointHasCanBeUsed() {
        assertThatCode(() -> rules.mayChange("journey.interview_confirmed", "Interview confirmed", "ACTIVE", null,
                "{{company_name}}: {{first_name}}, {{job_title}} {{interview_type}} {{interview_time}} with "
                        + "{{interviewers}}. {{meeting_link}} {{link}} {{recruiter_name}} {{sender_name}}", true))
                .doesNotThrowAnyException();
        assertThatCode(() -> rules.mayChange("journey.reschedule_declined", "Reschedule not possible", "ACTIVE", null,
                "{{reason}} It stays at {{interview_time}}.", true)).doesNotThrowAnyException();
    }
}
