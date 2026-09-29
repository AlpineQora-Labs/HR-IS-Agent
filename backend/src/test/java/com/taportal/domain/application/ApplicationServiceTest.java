package com.taportal.domain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taportal.api.ApplicationDtos.UpdateApplicationRequest;
import com.taportal.domain.application.ApplicationService.NewApplication;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.events.ApplicationReceived;
import com.taportal.domain.events.ApplicationStageChanged;
import com.taportal.domain.interview.InterviewService;
import com.taportal.domain.job.JobRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ApplicationServiceTest {

    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final InterviewService interviews = mock(InterviewService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ApplicationService service =
            new ApplicationService(applications, candidates, mock(JobRepository.class), interviews, events);

    private final UUID id = UUID.randomUUID();
    private Application application;

    @BeforeEach
    void anApplicationUnderReview() {
        application = new Application();
        ReflectionTestUtils.setField(application, "id", id);
        application.setCandidateId(UUID.randomUUID());
        application.setJobId(UUID.randomUUID());
        application.setStage("SCREENED");
        when(applications.findById(id)).thenReturn(Optional.of(application));
        when(applications.save(any(Application.class))).thenAnswer(call -> {
            Application a = call.getArgument(0);
            if (a.getId() == null) {
                ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
            }
            return a;
        });
        when(candidates.save(any(Candidate.class))).thenAnswer(call -> {
            Candidate c = call.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            return c;
        });
    }

    private static UpdateApplicationRequest moveTo(String stage) {
        return new UpdateApplicationRequest(stage, null, null);
    }

    @Test
    void aMoveIsAnnouncedWithWhereFromAndWhereTo() {
        service.update(id, moveTo("INTERVIEW"));

        verify(events).publishEvent(new ApplicationStageChanged(id, "SCREENED", "INTERVIEW"));
    }

    @Test
    @DisplayName("saving the stage it already has is not a move: nobody is invited twice")
    void theSameStageAgainIsNotAMove() {
        application.setStage("INTERVIEW");

        service.update(id, moveTo("INTERVIEW"));
        service.update(id, new UpdateApplicationRequest(null, 80, null));

        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("an application that is closed takes its interview with it")
    void closingAnApplicationCancelsItsInterviews() {
        service.update(id, moveTo("REJECTED"));
        verify(interviews).cancelForApplication(id);

        application.setStage("INTERVIEW");
        service.update(id, moveTo("WITHDRAWN"));
        verify(interviews, org.mockito.Mockito.times(2)).cancelForApplication(id);
    }

    @Test
    void otherMovesLeaveInterviewsAlone() {
        service.update(id, moveTo("ASSESSMENT"));
        service.update(id, moveTo("OFFER"));

        verify(interviews, never()).cancelForApplication(any());
    }

    @Test
    void aStageThatDoesNotExistIsRefusedAndNothingIsAnnounced() {
        assertThatThrownBy(() -> service.update(id, moveTo("SHORTLISTED"))).isInstanceOf(ResponseStatusException.class);

        assertThat(application.getStage()).isEqualTo("SCREENED");
        verify(events, never()).publishEvent(any());
    }

    private NewApplication applying(String phone, boolean agreed, boolean screeningFollows) {
        return new NewApplication(UUID.randomUUID(), "Raj Patel", "raj.patel@example.com", phone, "en",
                "WEB_CHAT", agreed, screeningFollows);
    }

    private Candidate saved() {
        ArgumentCaptor<Candidate> c = ArgumentCaptor.forClass(Candidate.class);
        verify(candidates).save(c.capture());
        return c.getValue();
    }

    @Test
    void aNumberGivenIsKeptAsTypedAndInStandardForm() {
        service.receive(applying("(212) 555-0109", true, true));

        assertThat(saved().getPhone()).isEqualTo("(212) 555-0109");
        assertThat(saved().getPhoneE164()).isEqualTo("+12125550109");
        assertThat(saved().getSmsConsentAt()).isNotNull();
    }

    @Test
    @DisplayName("what is typed instead of a number is kept, and is not a number: the application is not lost")
    void whatIsNotANumberDoesNotStopTheApplication() {
        Application made = service.receive(applying("call me later", true, true));

        assertThat(made.getStage()).isEqualTo("APPLIED");
        assertThat(saved().getPhone()).isEqualTo("call me later");
        assertThat(saved().getPhoneE164()).isNull();
        assertThat(saved().getSmsConsentAt()).as("agreement needs a number it can apply to").isNull();
    }

    @Test
    void withoutAgreementNoAgreementIsRecorded() {
        service.receive(applying("212-555-0109", false, true));

        assertThat(saved().getSmsConsentAt()).isNull();
        assertThat(saved().getPhoneE164()).isEqualTo("+12125550109");
    }

    @Test
    void aMissingNameOrEmailDoesNotStopTheApplication() {
        service.receive(new NewApplication(UUID.randomUUID(), " ", null, null, null, "WEB_CHAT", true, true));

        assertThat(saved().getName()).isEqualTo("Unknown");
        assertThat(saved().getEmail()).isEmpty();
        assertThat(saved().getPreferredLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("with screening to come, the application is announced once it is passed, not before")
    void anApplicationIsAnnouncedWhenItIsIn() {
        Application made = service.receive(applying("212-555-0109", true, true));
        verify(events, never()).publishEvent(any());

        when(applications.findById(made.getId())).thenReturn(Optional.of(made));
        service.passedScreening(made.getId());

        assertThat(made.getStage()).isEqualTo("SCREENED");
        assertThat(made.getKnockoutPassed()).isTrue();
        verify(events).publishEvent(new ApplicationReceived(made.getId()));
    }

    @Test
    void withNoScreeningToComeItIsAnnouncedAtOnce() {
        Application made = service.receive(applying("212-555-0109", true, false));

        verify(events).publishEvent(new ApplicationReceived(made.getId()));
    }

    @Test
    @DisplayName("a candidate who did not pass was told in the conversation: nothing else is sent")
    void failingScreeningAnnouncesNothing() {
        service.failedScreening(id, "Did not meet knockout screening criteria");

        assertThat(application.getStage()).isEqualTo("REJECTED");
        assertThat(application.getKnockoutPassed()).isFalse();
        assertThat(application.getRejectionReason()).isEqualTo("Did not meet knockout screening criteria");
        verify(events, never()).publishEvent(any());
    }
}
