package com.taportal.domain.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.interview.Interview;
import com.taportal.domain.job.Job;
import com.taportal.domain.recruiter.RecruiterUser;
import com.taportal.domain.recruiter.RecruiterUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JourneyValuesTest {

    private final RecruiterUserRepository people = mock(RecruiterUserRepository.class);
    private final JourneyValues values = new JourneyValues(people, "https://careers.example/", "Example Careers");

    @Test
    void aFirstNameIsTheFirstWordOfTheName() {
        assertThat(JourneyValues.firstName("Raj Patel")).isEqualTo("Raj");
        assertThat(JourneyValues.firstName("  Fatima   Al-Sayed ")).isEqualTo("Fatima");
    }

    @Test
    @DisplayName("nobody is greeted as Unknown")
    void aNameThatIsNotKnownIsNotUsed() {
        assertThat(JourneyValues.firstName("Unknown")).isEqualTo("there");
        assertThat(JourneyValues.firstName("")).isEqualTo("there");
        assertThat(JourneyValues.firstName(null)).isEqualTo("there");
    }

    @Test
    void interviewersAreNamedAsAPersonWould() {
        assertThat(JourneyValues.names("David Okafor")).isEqualTo("David Okafor");
        assertThat(JourneyValues.names("David Okafor, Marcus Bell")).isEqualTo("David Okafor and Marcus Bell");
        assertThat(JourneyValues.names("A, B, C")).isEqualTo("A, B and C");
        assertThat(JourneyValues.names(" ")).isEqualTo("the hiring team");
        assertThat(JourneyValues.names(null)).isEqualTo("the hiring team");
    }

    @Test
    void theKindOfInterviewIsInWords() {
        assertThat(JourneyValues.typeInWords("AI_PHONE_SCREEN")).isEqualTo("phone screen");
        assertThat(JourneyValues.typeInWords("RECRUITER_SCREEN")).isEqualTo("recruiter screen");
        assertThat(JourneyValues.typeInWords("SOMETHING_ELSE")).isEqualTo("something else");
        assertThat(JourneyValues.typeInWords("Hiring manager conversation")).isEqualTo("Hiring manager conversation");
        assertThat(JourneyValues.typeInWords(null)).isEqualTo("interview");
    }

    @Test
    void whatIsKnownIsFilledAndWhatIsNotIsLeftOut() {
        Candidate c = new Candidate();
        c.setName("Raj Patel");
        Job job = new Job();
        job.setTitle("Summer Analyst Intern");

        Map<String, String> v = values.of(c, null, job, null);

        assertThat(v).containsEntry("first_name", "Raj").containsEntry("candidate_name", "Raj Patel")
                .containsEntry("job_title", "Summer Analyst Intern")
                .containsEntry("company_name", "Example Careers")
                .containsEntry("sender_name", "Example Careers")
                // A role with no recruiter set is still recruited for by somebody: the field never fails a message.
                .containsEntry("recruiter_name", "the recruiting team");
        assertThat(v).doesNotContainKeys("interview_time", "meeting_link", "link", "slot_options");
    }

    @Test
    void anInterviewBringsItsTimeItsPeopleAndItsLinks() {
        UUID recruiterId = UUID.randomUUID();
        RecruiterUser recruiter = mock(RecruiterUser.class);
        when(recruiter.getName()).thenReturn("Marcus Bell");
        when(people.findById(recruiterId)).thenReturn(Optional.of(recruiter));
        Job job = new Job();
        job.setTitle("Teller");
        job.setRecruiterId(recruiterId);
        Interview interview = mock(Interview.class);
        UUID id = UUID.fromString("81000000-0000-0000-0000-000000000001");
        when(interview.getId()).thenReturn(id);
        when(interview.getScheduledAt()).thenReturn(OffsetDateTime.parse("2026-10-06T14:00:00Z"));
        when(interview.getInterviewers()).thenReturn("David Okafor, Marcus Bell");
        when(interview.getType()).thenReturn("RECRUITER_SCREEN");
        when(interview.getMeetingLink()).thenReturn("https://meet.example/abc");

        Map<String, String> v = values.of(new Candidate(), null, job, interview);

        assertThat(v).containsEntry("interview_time", "Tue, Oct 6, 10:00 AM ET")
                .containsEntry("interviewers", "David Okafor and Marcus Bell")
                .containsEntry("interview_type", "recruiter screen")
                .containsEntry("meeting_link", "https://meet.example/abc")
                .containsEntry("link", "https://careers.example/schedule/" + id)
                .containsEntry("recruiter_name", "Marcus Bell")
                .containsEntry("sender_name", "Marcus Bell, Example Careers");
    }

    @Test
    void untilAMeetingExistsThePageThatShowsTheInterviewIsWhereToGo() {
        Interview interview = mock(Interview.class);
        UUID id = UUID.randomUUID();
        when(interview.getId()).thenReturn(id);

        assertThat(values.of(null, null, null, interview))
                .containsEntry("meeting_link", "https://careers.example/schedule/" + id);
    }

    @Test
    void timesOfferedComeWithTheRepliesThatPickOne() {
        Map<String, String> v = new java.util.HashMap<>();
        JourneyValues.offer(v, List.of(
                OffsetDateTime.parse("2026-10-06T14:00:00Z"), OffsetDateTime.parse("2026-10-07T18:30:00Z")));

        assertThat(v).containsEntry("slot_options", "1) Tue, Oct 6, 10:00 AM ET\n2) Wed, Oct 7, 2:30 PM ET")
                .containsEntry("slot_choices", "1 or 2");

        Map<String, String> none = new java.util.HashMap<>();
        JourneyValues.offer(none, List.of());
        assertThat(none).isEmpty();
    }

    @Test
    void everyFieldATemplateMayUseIsListedOnce() {
        assertThat(JourneyValues.FIELDS).extracting(JourneyValues.Field::name).doesNotHaveDuplicates()
                .contains("first_name", "job_title", "slot_options", "slot_choices", "interview_time", "link", "reason");
    }
}
