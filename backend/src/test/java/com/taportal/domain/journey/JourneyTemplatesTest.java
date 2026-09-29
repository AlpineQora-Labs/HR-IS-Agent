package com.taportal.domain.journey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Which template can word a point of the journey")
class JourneyTemplatesTest {

    private static final JourneyPoint INVITE = JourneyPoint.INTERVIEW_INVITE;

    @Test
    void anActiveTemplateWithWhatThePointNeedsIsFit() {
        assertThat(JourneyTemplates.unfit(INVITE, "ACTIVE", null,
                "{{company_name}}: pick a time, {{first_name}}: {{slot_options}}")).isNull();
        assertThat(JourneyTemplates.unfit(INVITE, "ACTIVE", "journey.interview_invite", "{{ slot_options }}")).isNull();
    }

    @Test
    @DisplayName("a Draft is not sent, whatever the drawing shows")
    void aDraftIsNotFit() {
        assertThat(JourneyTemplates.unfit(INVITE, "DRAFT", null, "{{slot_options}}")).isEqualTo("is not Active");
        assertThat(JourneyTemplates.unfit(INVITE, "ARCHIVED", null, "{{slot_options}}")).isEqualTo("is not Active");
    }

    @Test
    @DisplayName("the wording of one point is not the wording of another, even when it could be filled in")
    void anotherPointsWordingIsNotFit() {
        assertThat(JourneyTemplates.unfit(JourneyPoint.INTERVIEW_REMINDER_24H, "ACTIVE", "journey.interview_confirmed",
                "You are confirmed for {{interview_time}}")).isEqualTo("is the wording of another point of the journey");
    }

    @Test
    void anInvitationWithoutTimesIsNotFit() {
        assertThat(JourneyTemplates.unfit(INVITE, "ACTIVE", null, "We would like to interview you, {{first_name}}."))
                .isEqualTo("lacks {{slot_options}}");
    }

    @Test
    @DisplayName("a field the point has no value for makes a template unfit: it could never be sent")
    void aFieldWithNoValueAtThePointIsNotFit() {
        assertThat(JourneyTemplates.unfit(INVITE, "ACTIVE", null, "{{slot_options}} {{interview_time}}"))
                .isEqualTo("uses {{interview_time}}, which has no value at this point");
        assertThat(JourneyTemplates.unfit(JourneyPoint.INTERVIEW_CONFIRMED, "ACTIVE", null,
                "{{interview_time}} {{event_name}} {{cohort}}"))
                .isEqualTo("uses {{event_name}} and {{cohort}}, which have no value at this point");
    }

    @Test
    void olderFieldNamesAreReadAsTheNamesTheyHaveNow() {
        assertThat(JourneyTemplates.unfit(JourneyPoint.STATUS_REPLY, "ACTIVE", null,
                "Hi {{participant_name}}: {{status}}")).isNull();
    }

    @Test
    @DisplayName("every point has the fields it needs, and none has a time before an interview is booked")
    void whatAPointHasFollowsFromWhatItIsAbout() {
        for (JourneyPoint p : JourneyPoint.values()) {
            assertThat(p.has()).as(p.name()).containsAll(p.needs()).contains("company_name", "first_name");
        }
        assertThat(JourneyPoint.INTERVIEW_INVITE.has()).contains("slot_options", "link").doesNotContain("interview_time");
        assertThat(JourneyPoint.INTERVIEW_CONFIRMED.has()).contains("interview_time", "meeting_link")
                .doesNotContain("slot_options");
        assertThat(JourneyPoint.OPT_OUT.has()).doesNotContain("job_title", "candidate_name");
        assertThat(JourneyPoint.UNKNOWN_SENDER.has()).doesNotContain("job_title", "candidate_name");
    }
}
