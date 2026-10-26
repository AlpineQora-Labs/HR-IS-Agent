package com.taportal.domain.briefing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.taportal.api.MessageDtos.MessageRow;
import com.taportal.api.MessageDtos.Timeline;
import com.taportal.api.SchedulingDtos.AttentionItem;
import com.taportal.api.SchedulingDtos.InterviewerLoad;
import com.taportal.api.SchedulingDtos.Overview;
import com.taportal.domain.application.Application;
import com.taportal.domain.application.ApplicationRepository;
import com.taportal.domain.application.ScreeningAnswer;
import com.taportal.domain.application.ScreeningAnswerRepository;
import com.taportal.domain.briefing.Briefing.Why;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.interview.Interview;
import com.taportal.domain.interview.InterviewRepository;
import com.taportal.domain.interview.SchedulingOverviewService;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.JobRepository;
import com.taportal.domain.job.KnockoutQuestion;
import com.taportal.domain.job.KnockoutQuestionRepository;
import com.taportal.domain.journey.JourneyQueries;
import com.taportal.domain.recruiter.RecruiterUser;
import com.taportal.domain.recruiter.RecruiterUserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The briefing the recruiter assistant answers from: what it holds, the reasons it gives, and what it keeps out. */
class BriefingServiceTest {

    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-24T14:00:00-04:00");

    SchedulingOverviewService overview = mock(SchedulingOverviewService.class);
    CandidateRepository candidates = mock(CandidateRepository.class);
    ApplicationRepository applications = mock(ApplicationRepository.class);
    JobRepository jobs = mock(JobRepository.class);
    InterviewRepository interviews = mock(InterviewRepository.class);
    ScreeningAnswerRepository answers = mock(ScreeningAnswerRepository.class);
    KnockoutQuestionRepository questions = mock(KnockoutQuestionRepository.class);
    RecruiterUserRepository users = mock(RecruiterUserRepository.class);
    JourneyQueries journey = mock(JourneyQueries.class);
    BriefingService service;

    Candidate maya;
    Job teller;
    Application app;

    @BeforeEach
    void setUp() {
        service = new BriefingService(overview, candidates, applications, jobs, interviews, answers, questions, users,
                journey, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        maya = new Candidate();
        set(maya, "id", UUID.randomUUID());
        set(maya, "name", "Maya Thompson");
        set(maya, "email", "maya.thompson@example.com");
        set(maya, "phoneE164", "+17045550123");
        set(maya, "headline", "Teller at a credit union. Maya Thompson, call 704-555-0123");
        set(maya, "location", "Charlotte, NC");
        teller = job("Teller", "R-1042");
        app = application(maya, teller, "INTERVIEW", NOW.minusDays(5));

        when(candidates.findById(maya.getId())).thenReturn(Optional.of(maya));
        when(applications.findByCandidateId(maya.getId())).thenReturn(List.of(app));
        when(applications.findById(app.getId())).thenReturn(Optional.of(app));
        when(jobs.findAllById(any())).thenReturn(List.of(teller));
        when(jobs.findById(teller.getId())).thenReturn(Optional.of(teller));
        when(interviews.findByApplicationIdIn(any())).thenReturn(List.of());
        when(answers.findByApplicationId(any())).thenReturn(List.of());
        when(questions.findByJobIdOrderByOrdinal(any())).thenReturn(List.of());
        timeline(true, null, List.of());
        when(overview.overview()).thenReturn(new Overview(List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    @Nested
    class Privacy {

        @Test
        void aCandidateBriefingNamesNobodyAndCarriesNoContactDetails() {
            Interview iv = interview("PANEL", "SCHEDULED", NOW.plusDays(2));
            set(iv, "interviewers", "Priya Raman, Marcus Bell");
            set(iv, "bookedVia", "TEXT");
            set(iv, "summary", "Maya Thompson was clear and calm.");
            when(interviews.findByApplicationIdIn(any())).thenReturn(List.of(iv));
            timeline(true, null, List.of(
                    inbound("Yes this is Maya, reach me at 704-555-0123 or maya.thompson@example.com"),
                    outbound("SENT", "When the interview is booked", null)));

            Briefing b = service.forApplication(app.getId());

            String all = text(b);
            assertThat(all).doesNotContain("Maya", "Thompson", "Priya", "Raman", "Marcus", "Bell",
                    "704", "555", "0123", "example.com");
            assertThat(b.people()).containsEntry("[C1]", "Maya Thompson")
                    .containsEntry("[S1]", "Priya Raman").containsEntry("[S2]", "Marcus Bell");
            assertThat(all).contains("[C1]", "[S1] and [S2]", "[a number]", "[an email address]");
            assertThat(b.gaps()).anyMatch(g -> g.contains("Phone numbers and email addresses are left out"));
        }

        @Test
        void theDayNamesNobodyEither() {
            when(overview.overview()).thenReturn(new Overview(
                    List.of(item("Jordan Lee", 30, NOW.minusDays(2))),
                    List.of(item("Ana Ruiz", 4, NOW.minusHours(4))),
                    List.of(),
                    List.of(new AttentionItem(UUID.randomUUID(), UUID.randomUUID(), "Sam Ford", "Teller", "SCHEDULED",
                            NOW.plusHours(2), 0, null, List.of("Priya Raman"))),
                    List.of(new InterviewerLoad(UUID.randomUUID(), "Priya Raman", "PR", "RECRUITER", 3))));

            Briefing b = service.forDay();

            assertThat(text(b)).doesNotContain("Jordan", "Ana", "Sam", "Priya");
            assertThat(b.people()).containsValues("Jordan Lee", "Ana Ruiz", "Sam Ford", "Priya Raman");
            // The same person is one placeholder wherever they appear.
            assertThat(b.people().values().stream().filter("Priya Raman"::equals)).hasSize(1);
        }
    }

    @Nested
    class Reasons {

        @Test
        void timesOfferedAndNotPickedAreExplainedWithWhatCanBeDone() {
            Interview iv = interview("PHONE_SCREEN", "SLOTS_PROPOSED", null);
            set(iv, "createdAt", NOW.minusHours(50));
            when(interviews.findByApplicationIdIn(any())).thenReturn(List.of(iv));

            Why why = only(service.forApplication(app.getId()).whys());

            assertThat(why.what()).isEqualTo("No time is set for the phone screen for Teller (R-1042)");
            assertThat(why.because()).isEqualTo("times were offered 2 days ago and [C1] has not picked one");
            assertThat(why.next()).contains("scheduling drawer");
        }

        @Test
        void aTimeThatHasPassedWithNothingRecordedNeedsAnOutcome() {
            when(interviews.findByApplicationIdIn(any())).thenReturn(List.of(interview("LIVE_VIDEO", "SCHEDULED", NOW.minusHours(3))));

            Why why = only(service.forApplication(app.getId()).whys());

            assertThat(why.what()).contains("has no outcome recorded");
            assertThat(why.next()).contains("mark it completed or a no-show");
        }

        @Test
        void aBookedTimeSaysWhoSetIt() {
            Interview iv = interview("PANEL", "SCHEDULED", NOW.plusDays(1));
            set(iv, "bookedVia", "TEXT");
            when(interviews.findByApplicationIdIn(any())).thenReturn(List.of(iv));

            Why why = only(service.forApplication(app.getId()).whys());

            assertThat(why.because()).isEqualTo("the candidate picked the time by text");
            assertThat(why.next()).isNull();
        }

        @Test
        void failedScreeningIsExplainedByTheAnswerGiven() {
            set(app, "knockoutPassed", false);
            KnockoutQuestion q = new KnockoutQuestion();
            set(q, "id", UUID.randomUUID());
            set(q, "prompt", "Are you authorized to work in the US?");
            ScreeningAnswer a = new ScreeningAnswer();
            set(a, "questionId", q.getId());
            set(a, "answer", "No");
            set(a, "passed", false);
            when(questions.findByJobIdOrderByOrdinal(teller.getId())).thenReturn(List.of(q));
            when(answers.findByApplicationId(app.getId())).thenReturn(List.of(a));

            Why why = only(service.forApplication(app.getId()).whys());

            assertThat(why.what()).isEqualTo("[C1] did not pass screening for Teller (R-1042)");
            assertThat(why.because()).isEqualTo("the answer to \"Are you authorized to work in the US?\" was \"No\"");
        }

        @Test
        void aMessageThatDidNotGoOutIsExplainedByItsReason() {
            timeline(true, null, List.of(outbound("SUPPRESSED", "When the interview is booked",
                    "The candidate opted out of texts on Oct 3.")));

            Why why = only(service.forCandidate(maya.getId(), null).whys());

            assertThat(why.what()).isEqualTo("The text sent when the interview is booked on Oct 23 did not go out");
            assertThat(why.because()).isEqualTo("the candidate opted out of texts on Oct 3.");
        }

        @Test
        void theSameReasonIsGivenOnce() {
            timeline(true, null, List.of(
                    outbound("FAILED", "When the interview is booked", "No mobile number a text can reach."),
                    outbound("FAILED", "When the interview is booked", "No mobile number a text can reach.")));

            assertThat(service.forCandidate(maya.getId(), null).whys()).hasSize(1);
        }

        @Test
        void textsHeldBackAreExplained() {
            timeline(false, "The number replied STOP.", List.of());

            assertThat(only(service.forCandidate(maya.getId(), null).whys()).what()).isEqualTo("Texts to [C1] are held back");
        }

        @Test
        void reasonsBeyondTheLimitAreCountedNotDroppedSilently() {
            List<AttentionItem> waiting = IntStream.range(0, 10)
                    .mapToObj(i -> item("Candidate " + i, 5, NOW.minusHours(5))).toList();
            when(overview.overview()).thenReturn(new Overview(List.of(), waiting, List.of(), List.of(), List.of()));

            Briefing b = service.forDay();

            assertThat(b.whys()).hasSize(BriefingService.MAX_WHYS);
            assertThat(b.gaps().get(0)).startsWith("2 more items like these are left out");
        }
    }

    @Nested
    class WhatIsOnScreen {

        @Test
        void theMostSpecificThingOnScreenIsBriefed() {
            assertThat(service.forContext(null, null, null).scope()).isEqualTo(Briefing.DAY);
            assertThat(service.forContext(null, null, teller.getId()).scope()).isEqualTo(Briefing.JOB);
            assertThat(service.forContext(maya.getId(), null, teller.getId()).about()).isEqualTo("[C1]");
            assertThat(service.forContext(maya.getId(), app.getId(), teller.getId()).about())
                    .isEqualTo("[C1], applying for Teller (R-1042)");
        }

        @Test
        void theApplicationOnScreenIsDescribedFirst() {
            Job branch = job("Branch Manager", null);
            Application older = application(maya, branch, "APPLIED", NOW.minusDays(1));
            when(applications.findByCandidateId(maya.getId())).thenReturn(List.of(older, app));
            when(applications.findById(app.getId())).thenReturn(Optional.of(app));
            when(jobs.findAllById(any())).thenReturn(List.of(teller, branch));

            Briefing b = service.forApplication(app.getId());

            assertThat(b.sections().stream().map(Briefing.Section::title).filter(t -> t.startsWith("Application")))
                    .containsExactly("Application for Teller (R-1042)", "Application for Branch Manager");
        }

        @Test
        void anIdNotOnFileIsNotFound() {
            UUID nobody = UUID.randomUUID();
            when(candidates.findById(nobody)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.forCandidate(nobody, null)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void aJobIsBriefedByStageAndOpeningsFilled() {
            RecruiterUser elena = new RecruiterUser();
            set(elena, "id", UUID.randomUUID());
            set(elena, "name", "Elena Vasquez");
            set(teller, "recruiterId", elena.getId());
            when(users.findById(elena.getId())).thenReturn(Optional.of(elena));
            Candidate b1 = new Candidate();
            set(b1, "id", UUID.randomUUID());
            when(applications.findByJobId(teller.getId())).thenReturn(List.of(app,
                    application(b1, teller, "HIRED", NOW.minusDays(9)), application(b1, teller, "HIRED", NOW.minusDays(8))));

            Briefing b = service.forJob(teller.getId());

            assertThat(lines(b, "Applicants by stage"))
                    .contains("3 applicants in all.", "Interview: 1.", "Hired: 2.", "Openings filled: 2 of 2.");
            assertThat(lines(b, "The requisition")).contains("Recruiter: [S1].");
            assertThat(b.people()).containsEntry("[S1]", "Elena Vasquez");
        }
    }

    // ------------------------------------------------------------------ fixtures

    private Job job(String title, String jobNo) {
        Job j = new Job();
        set(j, "id", UUID.randomUUID());
        set(j, "title", title);
        set(j, "jobNo", jobNo);
        set(j, "status", "OPEN");
        set(j, "openings", 2);
        return j;
    }

    private Application application(Candidate c, Job j, String stage, OffsetDateTime appliedAt) {
        Application a = new Application();
        set(a, "id", UUID.randomUUID());
        set(a, "candidateId", c.getId());
        set(a, "jobId", j.getId());
        set(a, "stage", stage);
        set(a, "appliedAt", appliedAt);
        set(a, "knockoutPassed", true);
        return a;
    }

    private Interview interview(String type, String status, OffsetDateTime at) {
        Interview iv = new Interview();
        set(iv, "id", UUID.randomUUID());
        set(iv, "applicationId", app.getId());
        set(iv, "type", type);
        set(iv, "status", status);
        set(iv, "scheduledAt", at);
        set(iv, "createdAt", NOW.minusDays(3));
        return iv;
    }

    private AttentionItem item(String name, long hours, OffsetDateTime at) {
        return new AttentionItem(UUID.randomUUID(), UUID.randomUUID(), name, "Teller", "SLOTS_PROPOSED", at, hours, null, List.of());
    }

    private void timeline(boolean canBeTexted, String whyNot, List<MessageRow> rows) {
        when(journey.timeline(maya.getId())).thenReturn(new Timeline(maya.getId(), maya.getName(), maya.getPhoneE164(),
                maya.getEmail(), canBeTexted, whyNot, true, null, rows));
    }

    private MessageRow outbound(String status, String sentWhen, String reason) {
        return new MessageRow(UUID.randomUUID(), "SMS", "OUTBOUND", status, "SENT".equals(status) ? "Sent" : "Not sent",
                reason, "INTERVIEW_BOOKED", sentWhen, "Interview booked", "+17045550123", null, "text", 1, false,
                app.getId(), null, NOW.minusDays(1), null);
    }

    private MessageRow inbound(String text) {
        return new MessageRow(UUID.randomUUID(), "SMS", "INBOUND", "RECEIVED", "Received", null, null, null, null,
                "+17045550123", null, text, null, false, null, null, NOW.minusHours(2), null);
    }

    private static Why only(List<Why> whys) {
        assertThat(whys).hasSize(1);
        return whys.get(0);
    }

    private static List<String> lines(Briefing b, String title) {
        return b.sections().stream().filter(s -> s.title().equals(title)).findFirst().orElseThrow().lines();
    }

    /** Every word of the briefing that leaves the server: all of it except the placeholder map. */
    private static String text(Briefing b) {
        List<String> parts = new ArrayList<>();
        parts.add(b.about());
        b.sections().forEach(s -> {
            parts.add(s.title());
            parts.addAll(s.lines());
        });
        b.whys().forEach(w -> {
            parts.add(w.what());
            parts.add(w.because());
            parts.add(String.valueOf(w.next()));
        });
        parts.addAll(b.gaps());
        b.links().forEach(l -> parts.add(l.label()));
        return String.join("\n", parts);
    }

    private static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}
