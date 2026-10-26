package com.taportal.domain.comms;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("a template can be offered for a step its wording suits, and is kept by the step's name")
    void aTemplateCanBeOfferedForAStep() {
        assertThat(rules.mayOffer("HIRED", null, null, "{{company_name}}: Welcome, {{first_name}}.", true))
                .isEqualTo("HIRED");
        assertThat(rules.mayOffer("hired", null, "Welcome, {{first_name}}", "<p>{{job_title}}</p>", false))
                .isEqualTo("HIRED");
        assertThat(rules.mayOffer(null, null, null, "anything", true)).isNull();
        assertThat(rules.mayOffer(" ", null, null, "anything", true)).isNull();
    }

    @Test
    @DisplayName("an invitation offered without its times is refused, and the reason names what it can use")
    void anOfferedTemplateCarriesWhatItsStepNeeds() {
        assertThatThrownBy(() -> rules.mayOffer("INTERVIEW_INVITE", null, null, "We would like to interview you.", true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("{{slot_options}}")
                .hasMessageContaining("when the candidate is selected for interview");
        assertThatThrownBy(() -> rules.mayOffer("APPLICATION_RECEIVED", null, null, "Your interview is {{interview_time}}", true))
                .hasMessageContaining("{{interview_time}}")
                .hasMessageContaining("It can use");
    }

    @Test
    @DisplayName("only a step on the drawing, on a channel it uses, and never with a point's own wording")
    void onlyADrawnStepOnItsChannel() {
        assertThatThrownBy(() -> rules.mayOffer("OPT_OUT", null, null, "You are unsubscribed.", true))
                .hasMessageContaining("is not one the workflow draws")
                .hasMessageNotContaining("OPT_OUT");
        assertThatThrownBy(() -> rules.mayOffer("NOPE", null, null, "Hi", true))
                .hasMessageContaining("That step is not on the workflow.");
        assertThatThrownBy(() -> rules.mayOffer("INTERVIEW_REMINDER_1H", null, "Soon", "<p>{{interview_time}}</p>", false))
                .hasMessageContaining("No email is sent 1 hour before the interview");
        assertThatThrownBy(() -> rules.mayOffer("HIRED", "journey.application_received", null, "Hi {{first_name}}", true))
                .hasMessageContaining("own wording");
    }

    @Test
    @DisplayName("a copy keeps the original's step only when the workflow draws it with an email")
    void aCopyIsOfferedForAStepItCanBeSavedFor() {
        assertThat(rules.offerForCopy("HIRED", null)).isEqualTo("HIRED");
        assertThat(rules.offerForCopy(null, "journey.interview_confirmed")).isEqualTo("INTERVIEW_CONFIRMED");
        assertThat(rules.offerForCopy(null, "journey.interview_canceled"))
                .as("a notice is never drawn, so its copy is offered for any step its fields suit").isNull();
        assertThat(rules.offerForCopy("INTERVIEW_REMINDER_1H", null)).as("no email is sent at that step").isNull();
        assertThat(rules.offerForCopy("NOPE", null)).isNull();
        assertThat(rules.offerForCopy(null, null)).isNull();
    }
}
