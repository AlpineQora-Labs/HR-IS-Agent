package com.taportal.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taportal.api.InterviewDtos.SlotResponse;
import com.taportal.domain.application.Application;
import com.taportal.domain.application.ApplicationRepository;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.events.BookingOrigin;
import com.taportal.domain.events.InterviewBooked;
import com.taportal.domain.events.InterviewCanceled;
import com.taportal.domain.events.InterviewTimesOffered;
import com.taportal.domain.interview.InterviewService.RescheduleAttempt;
import com.taportal.domain.interview.InterviewService.RescheduleRefusalReason;
import com.taportal.domain.interview.InterviewService.RescheduleResult;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.JobRepository;
import com.taportal.domain.notification.NotificationService;
import com.taportal.domain.recruiter.RecruiterUser;
import com.taportal.domain.recruiter.RecruiterUserRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Booking, moving and cancelling: what may happen, and what is announced when it does. */
class InterviewServiceTest {

    private final InterviewRepository interviews = mock(InterviewRepository.class);
    private final InterviewSlotRepository slots = mock(InterviewSlotRepository.class);
    private final CalendarEventRepository calendar = mock(CalendarEventRepository.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final RecruiterUserRepository people = mock(RecruiterUserRepository.class);
    private final InterviewPanelistRepository panelists = mock(InterviewPanelistRepository.class);
    private final SchedulingPolicyRepository policies = mock(SchedulingPolicyRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final InterviewService service = new InterviewService(
            interviews, slots, calendar, availability, applications, jobs, mock(CandidateRepository.class), people,
            panelists, mock(InterviewRoundRepository.class), mock(InterviewRoundMemberRepository.class),
            policies, notifications, events, mock(EntityManager.class));

    private final UUID managerId = UUID.randomUUID();
    private Interview interview;
    private Application application;
    private SchedulingPolicy policy;

    private static OffsetDateTime inDays(int days, int hour) {
        return OffsetDateTime.now().plusDays(days).withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }

    @BeforeEach
    void anInterviewWithAHiringManager() {
        application = new Application();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.setCandidateId(UUID.randomUUID());
        application.setJobId(UUID.randomUUID());
        application.setStage("SCREENED");

        interview = new Interview();
        ReflectionTestUtils.setField(interview, "id", UUID.randomUUID());
        interview.setApplicationId(application.getId());
        interview.setStatus("SLOTS_PROPOSED");
        interview.setType("RECRUITER_SCREEN");
        interview.setDurationMin(45);

        Job job = new Job();
        job.setHiringManagerId(managerId);
        RecruiterUser manager = mock(RecruiterUser.class);
        when(manager.getId()).thenReturn(managerId);
        when(manager.getName()).thenReturn("David Okafor");

        policy = new SchedulingPolicy();
        policy.setRescheduleLimit(2);
        policy.setRescheduleCutoffHours(12);
        policy.setProposalCount(3);
        policy.setMinNoticeHours(20);

        when(interviews.findById(interview.getId())).thenReturn(Optional.of(interview));
        when(interviews.lockById(interview.getId())).thenReturn(Optional.of(interview));
        when(interviews.findByApplicationId(application.getId())).thenReturn(List.of(interview));
        when(interviews.save(any(Interview.class))).thenAnswer(call -> call.getArgument(0));
        when(applications.findById(application.getId())).thenReturn(Optional.of(application));
        when(jobs.findById(application.getJobId())).thenReturn(Optional.of(job));
        when(panelists.findByInterviewId(interview.getId())).thenReturn(List.of());
        when(people.findAllById(any())).thenReturn(List.of(manager));
        when(people.lockAllByIdIn(any())).thenReturn(List.of(manager));
        when(policies.current()).thenReturn(policy);
        when(slots.findByInterviewId(interview.getId())).thenReturn(List.of());
        when(slots.findByInterviewIdAndStatusOrderByStartsAt(eq(interview.getId()), any())).thenReturn(List.of());
    }

    private InterviewSlot proposed(OffsetDateTime start) {
        InterviewSlot slot = new InterviewSlot();
        ReflectionTestUtils.setField(slot, "id", UUID.randomUUID());
        slot.setInterviewId(interview.getId());
        slot.setStartsAt(start);
        slot.setEndsAt(start.plusMinutes(45));
        slot.setStatus("PROPOSED");
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));
        return slot;
    }

    private void booked(OffsetDateTime at) {
        interview.setStatus("SCHEDULED");
        interview.setScheduledAt(at);
        interview.setMeetingLink("https://meet.example/abc");
        interview.setBookedAt(OffsetDateTime.now().minusDays(1));
    }

    private static void refused(Runnable call, HttpStatus status, String saying) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).contains(saying);
        });
    }

    @Nested
    @DisplayName("booking a time")
    class Booking {

        @Test
        void aTimeOnOfferIsBookedAndAnnouncedWithHowItWasBooked() {
            OffsetDateTime at = inDays(3, 14);
            InterviewSlot slot = proposed(at);

            Interview done = service.selectProposedSlot(slot.getId(), BookingOrigin.TEXT);

            assertThat(done.getStatus()).isEqualTo("SCHEDULED");
            assertThat(done.getScheduledAt()).isEqualTo(at);
            assertThat(done.getBookedAt()).isNotNull();
            assertThat(slot.getStatus()).isEqualTo("SELECTED");
            assertThat(application.getStage()).isEqualTo("INTERVIEW");
            verify(events).publishEvent(new InterviewBooked(interview.getId(), at, BookingOrigin.TEXT));
        }

        @Test
        @DisplayName("the interview and its interviewers are held before the time is checked")
        void theInterviewAndThePanelAreHeldFirst() {
            InterviewSlot slot = proposed(inDays(3, 14));

            service.selectProposedSlot(slot.getId(), BookingOrigin.WEB_PAGE);

            var order = org.mockito.Mockito.inOrder(interviews, people, availability);
            order.verify(interviews).lockById(interview.getId());
            order.verify(people).lockAllByIdIn(List.of(managerId));
            order.verify(availability).hasConflict(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a time that has passed is never booked")
        void aTimeThatHasPassedIsRefused() {
            InterviewSlot slot = proposed(OffsetDateTime.now().minusHours(2));

            refused(() -> service.selectProposedSlot(slot.getId(), BookingOrigin.TEXT), HttpStatus.CONFLICT, "has passed");

            assertThat(slot.getStatus()).isEqualTo("EXPIRED");
            assertThat(interview.getStatus()).isEqualTo("SLOTS_PROPOSED");
            verify(events, never()).publishEvent(any());
        }

        @Test
        void aTimeTakenByAnotherInterviewIsRefused() {
            InterviewSlot slot = proposed(inDays(3, 14));
            when(availability.hasConflict(any(), any(), any(), any())).thenReturn(true);

            refused(() -> service.selectProposedSlot(slot.getId(), BookingOrigin.TEXT), HttpStatus.CONFLICT, "just taken");

            assertThat(slot.getStatus()).isEqualTo("EXPIRED");
            verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("an application has one interview booked at a time: a time picked for a second is refused")
        void aSecondInterviewForTheApplicationIsNotBooked() {
            InterviewSlot slot = proposed(inDays(3, 14));
            Interview byTheTeam = new Interview();
            ReflectionTestUtils.setField(byTheTeam, "id", UUID.randomUUID());
            byTheTeam.setApplicationId(application.getId());
            byTheTeam.setStatus("SCHEDULED");
            byTheTeam.setScheduledAt(inDays(2, 11));
            when(interviews.findByApplicationId(application.getId())).thenReturn(List.of(interview, byTheTeam));

            refused(() -> service.selectProposedSlot(slot.getId(), BookingOrigin.TEXT), HttpStatus.CONFLICT,
                    "already has an interview booked");

            assertThat(interview.getStatus()).isEqualTo("SLOTS_PROPOSED");
            verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("booked by the team directly: the interviewers' hour is blocked and what was on offer is withdrawn")
        void aTimeBookedByTheTeamIsHeldLikeAnyOther() {
            Interview direct = new Interview();
            ReflectionTestUtils.setField(direct, "id", UUID.randomUUID());
            direct.setApplicationId(application.getId());
            direct.setStatus("SCHEDULED");
            direct.setType("PHONE_SCREEN");
            direct.setDurationMin(30);
            when(interviews.lockById(direct.getId())).thenReturn(Optional.of(direct));
            when(interviews.findByApplicationId(application.getId())).thenReturn(List.of(interview, direct));
            when(panelists.findByInterviewId(direct.getId())).thenReturn(List.of());
            InterviewSlot onOffer = proposed(inDays(3, 14));
            when(slots.findByInterviewId(interview.getId())).thenReturn(List.of(onOffer));
            InterviewSlot pool = new InterviewSlot();
            ReflectionTestUtils.setField(pool, "id", UUID.randomUUID());
            pool.setStartsAt(inDays(2, 11));
            pool.setEndsAt(inDays(2, 11).plusMinutes(30));
            when(slots.findById(pool.getId())).thenReturn(Optional.of(pool));

            service.schedule(direct.getId(), new com.taportal.api.InterviewDtos.ScheduleInterviewRequest(pool.getId(), null));

            assertThat(direct.getScheduledAt()).isEqualTo(pool.getStartsAt());
            assertThat(direct.getBookedAt()).isNotNull();
            ArgumentCaptor<CalendarEvent> blocked = ArgumentCaptor.forClass(CalendarEvent.class);
            verify(calendar).save(blocked.capture());
            assertThat(blocked.getValue().getUserId()).isEqualTo(managerId);
            assertThat(blocked.getValue().getStartsAt()).isEqualTo(pool.getStartsAt());
            assertThat(onOffer.getStatus()).as("the time texted to the candidate can no longer be picked").isEqualTo("EXPIRED");
            assertThat(interview.getStatus()).isEqualTo("REQUESTED");
            verify(events).publishEvent(new InterviewBooked(direct.getId(), pool.getStartsAt(), BookingOrigin.TEAM));
        }

        @Test
        @DisplayName("the team cannot book over an interviewer who is busy, or a second interview for an application")
        void aTimeBookedByTheTeamIsCheckedLikeAnyOther() {
            InterviewSlot pool = new InterviewSlot();
            ReflectionTestUtils.setField(pool, "id", UUID.randomUUID());
            pool.setStartsAt(inDays(2, 11));
            pool.setEndsAt(inDays(2, 11).plusMinutes(45));
            when(slots.findById(pool.getId())).thenReturn(Optional.of(pool));
            var request = new com.taportal.api.InterviewDtos.ScheduleInterviewRequest(pool.getId(), null);

            when(availability.hasConflict(anyList(), any(), any(), any())).thenReturn(true);
            refused(() -> service.schedule(interview.getId(), request), HttpStatus.CONFLICT, "not free");
            verify(events, never()).publishEvent(any());

            when(availability.hasConflict(anyList(), any(), any(), any())).thenReturn(false);
            Interview other = new Interview();
            ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
            other.setApplicationId(application.getId());
            other.setStatus("SCHEDULED");
            other.setScheduledAt(inDays(5, 9));
            when(interviews.findByApplicationId(application.getId())).thenReturn(List.of(interview, other));
            refused(() -> service.schedule(interview.getId(), request), HttpStatus.CONFLICT,
                    "already has an interview booked");
        }

        @Test
        @DisplayName("an interview already booked is not booked again by a second reply")
        void aBookedInterviewIsNotBookedAgain() {
            InterviewSlot slot = proposed(inDays(4, 10));
            booked(inDays(3, 14));

            refused(() -> service.selectProposedSlot(slot.getId(), BookingOrigin.TEXT), HttpStatus.CONFLICT, "already booked");

            assertThat(interview.getScheduledAt()).isEqualTo(inDays(3, 14));
        }

        @Test
        void aTimeNoLongerOnOfferIsRefused() {
            InterviewSlot slot = proposed(inDays(3, 14));
            slot.setStatus("EXPIRED");

            refused(() -> service.selectProposedSlot(slot.getId(), BookingOrigin.TEXT), HttpStatus.CONFLICT, "no longer available");
        }
    }

    @Nested
    @DisplayName("offering times")
    class Offering {

        @Test
        void asManyTimesAreOfferedAsThePolicySays() {
            policy.setProposalCount(3);
            when(availability.openSlots(any(), anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of());

            service.autoPropose(interview.getId());

            verify(availability).openSlots(eq(List.of(managerId)), eq(45), eq(3), isNull(), eq(List.of()), isNull());
        }

        @Test
        @DisplayName("times are not offered over a booking: it has to be given up first")
        void aBookedInterviewIsNotOfferedOver() {
            booked(inDays(3, 14));

            refused(() -> service.autoPropose(interview.getId()), HttpStatus.CONFLICT, "is booked");

            assertThat(interview.getStatus()).isEqualTo("SCHEDULED");
            verify(availability, never()).openSlots(any(), anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        void timesThatHavePassedAreNotOnOffer() {
            InterviewSlot gone = proposed(OffsetDateTime.now().minusHours(1));
            InterviewSlot ahead = proposed(inDays(2, 10));
            when(slots.findByInterviewIdAndStatusOrderByStartsAt(interview.getId(), "PROPOSED"))
                    .thenReturn(List.of(gone, ahead));

            assertThat(service.proposedSlots(interview.getId())).extracting(SlotResponse::id).containsExactly(ahead.getId());
        }

        @Test
        @DisplayName("an application already booked gets no second interview")
        void beginningAgainReturnsTheBooking() {
            booked(inDays(3, 14));

            Interview same = service.beginSelfSchedule(application.getId(), "RECRUITER_SCREEN", 45);

            assertThat(same).isSameAs(interview);
            verify(interviews, never()).save(any());
            verify(availability, never()).openSlots(any(), anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        @DisplayName("an interview begun from a round of the plan is saved whole: its kind and length are set before it is")
        void anInterviewBegunFromARoundIsSavedWhole() {
            UUID roundId = UUID.randomUUID();
            InterviewRoundRepository rounds = (InterviewRoundRepository) ReflectionTestUtils.getField(service, "rounds");
            InterviewRoundMemberRepository members =
                    (InterviewRoundMemberRepository) ReflectionTestUtils.getField(service, "roundMembers");
            InterviewRound round = mock(InterviewRound.class);
            when(round.getName()).thenReturn("Hiring manager conversation");
            when(round.getDurationMin()).thenReturn(60);
            when(rounds.findById(roundId)).thenReturn(Optional.of(round));
            when(members.findByRoundId(roundId)).thenReturn(List.of());
            when(interviews.findByApplicationId(application.getId())).thenReturn(List.of());
            List<String> savedAs = new java.util.ArrayList<>();
            when(interviews.save(any(Interview.class))).thenAnswer(call -> {
                Interview i = call.getArgument(0);
                savedAs.add(i.getType() + "/" + i.getDurationMin());
                ReflectionTestUtils.setField(i, "id", interview.getId());
                return i;
            });
            when(availability.openSlots(any(), anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of());

            service.beginRound(application.getId(), roundId);

            assertThat(savedAs.get(0)).isEqualTo("Hiring manager conversation/60");
        }

        @Test
        void laterTimesComeAfterTheLastOneOnOfferAndRepeatNone() {
            InterviewSlot a = proposed(inDays(2, 10));
            InterviewSlot b = proposed(inDays(3, 14));
            when(slots.findByInterviewIdAndStatusOrderByStartsAt(interview.getId(), "PROPOSED")).thenReturn(List.of(a, b));
            OffsetDateTime later = inDays(5, 11);
            when(availability.openSlots(any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(List.<OffsetDateTime[]>of(new OffsetDateTime[] {later, later.plusMinutes(45)}));

            List<SlotResponse> offered = service.proposeLater(interview.getId());

            verify(availability).openSlots(any(), eq(45), eq(3), eq(b.getStartsAt()),
                    eq(List.of(a.getStartsAt(), b.getStartsAt())), isNull());
            assertThat(offered).extracting(SlotResponse::startsAt).containsExactly(later);
        }

        @Test
        @DisplayName("with nothing later, what is on offer stays on offer")
        void withNothingLaterNothingChanges() {
            InterviewSlot a = proposed(inDays(2, 10));
            when(slots.findByInterviewIdAndStatusOrderByStartsAt(interview.getId(), "PROPOSED")).thenReturn(List.of(a));
            when(slots.findByInterviewId(interview.getId())).thenReturn(List.of(a));
            when(availability.openSlots(any(), anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of());

            assertThat(service.proposeLater(interview.getId())).isEmpty();

            assertThat(a.getStatus()).isEqualTo("PROPOSED");
            verify(slots, never()).saveAll(anyList());
        }

        @Test
        void timesTheTeamOffersAreAnnounced() {
            when(availability.openSlots(any(), anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of());

            service.proposeByTeam(interview.getId(), null);

            verify(events).publishEvent(new InterviewTimesOffered(interview.getId(), InterviewTimesOffered.Why.PROPOSED));
        }
    }

    @Nested
    @DisplayName("a candidate asks to move a booked interview")
    class Moving {

        private final OffsetDateTime at = inDays(3, 14);

        @BeforeEach
        void itIsBooked() {
            booked(at);
        }

        private void thereAreOtherTimes(OffsetDateTime... starts) {
            List<OffsetDateTime[]> windows = new java.util.ArrayList<>();
            for (OffsetDateTime s : starts) {
                windows.add(new OffsetDateTime[] {s, s.plusMinutes(45)});
            }
            when(availability.openSlots(any(), anyInt(), anyInt(), any(), any(), any())).thenReturn(windows);
        }

        @Test
        void withSomewhereToMoveToTheTimeIsGivenUpAndTheChangeCounted() {
            thereAreOtherTimes(inDays(4, 10), inDays(5, 15));

            RescheduleAttempt attempt = service.tryCandidateReschedule(interview.getId());

            assertThat(attempt.result()).isEqualTo(RescheduleResult.MOVED);
            assertThat(attempt.was()).isEqualTo(at);
            assertThat(attempt.slots()).hasSize(2);
            assertThat(interview.getRescheduleCount()).isEqualTo(1);
            assertThat(interview.getScheduledAt()).isNull();
            assertThat(interview.getMeetingLink()).isNull();
            assertThat(interview.getStatus()).isEqualTo("SLOTS_PROPOSED");
            verify(calendar).deleteByInterviewId(interview.getId());
        }

        @Test
        @DisplayName("other times are looked for while the booking stands, never offering the time being given up")
        void theTimeBeingGivenUpIsNotOfferedBack() {
            thereAreOtherTimes(inDays(4, 10));

            service.tryCandidateReschedule(interview.getId());

            verify(availability).openSlots(eq(List.of(managerId)), eq(45), eq(3), isNull(), eq(List.of(at)),
                    eq(interview.getId()));
        }

        @Test
        @DisplayName("with nowhere to move to, the booking is kept and no change is counted")
        void withNowhereToMoveToNothingChanges() {
            thereAreOtherTimes();

            RescheduleAttempt attempt = service.tryCandidateReschedule(interview.getId());

            assertThat(attempt.result()).isEqualTo(RescheduleResult.NO_OTHER_TIMES);
            assertThat(interview.getScheduledAt()).isEqualTo(at);
            assertThat(interview.getStatus()).isEqualTo("SCHEDULED");
            assertThat(interview.getRescheduleCount()).isZero();
            verify(calendar, never()).deleteByInterviewId(any());
            verify(interviews, never()).save(any());
        }

        @Test
        void onceTheChangesAllowedAreUsedTheAnswerIsNoAndNothingChanges() {
            interview.setRescheduleCount(2);

            RescheduleAttempt attempt = service.tryCandidateReschedule(interview.getId());

            assertThat(attempt.result()).isEqualTo(RescheduleResult.REFUSED);
            assertThat(attempt.refusal().reason()).isEqualTo(RescheduleRefusalReason.LIMIT_REACHED);
            assertThat(interview.getScheduledAt()).isEqualTo(at);
            verify(availability, never()).openSlots(any(), anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        void tooCloseToTheInterviewTheAnswerIsNo() {
            booked(OffsetDateTime.now().plusHours(5));

            RescheduleAttempt attempt = service.tryCandidateReschedule(interview.getId());

            assertThat(attempt.result()).isEqualTo(RescheduleResult.REFUSED);
            assertThat(attempt.refusal().reason()).isEqualTo(RescheduleRefusalReason.TOO_CLOSE);
            assertThat(attempt.refusal().hours()).isEqualTo(12);
        }

        @Test
        @DisplayName("with nothing booked there is nothing to move, and no change is spent")
        void withNothingBookedNothingIsSpent() {
            interview.setStatus("SLOTS_PROPOSED");
            interview.setScheduledAt(null);

            assertThat(service.tryCandidateReschedule(interview.getId()).result()).isEqualTo(RescheduleResult.NOT_BOOKED);
            assertThat(interview.getRescheduleCount()).isZero();
        }

        @Test
        void theSelfSchedulePageIsToldWhyInWords() {
            thereAreOtherTimes();
            refused(() -> service.candidateReschedule(interview.getId()), HttpStatus.UNPROCESSABLE_ENTITY,
                    "your interview stays as booked");

            interview.setRescheduleCount(2);
            refused(() -> service.candidateReschedule(interview.getId()), HttpStatus.UNPROCESSABLE_ENTITY,
                    "Reschedule limit reached");
        }

        @Test
        void aMoveByTheTeamIsAnnouncedAsOne() {
            thereAreOtherTimes(inDays(4, 10));

            service.rescheduleByTeam(interview.getId());

            verify(events).publishEvent(new InterviewTimesOffered(interview.getId(), InterviewTimesOffered.Why.MOVED));
            assertThat(interview.getRescheduleCount()).as("the team's move is not the candidate's").isZero();
        }

        @Test
        @DisplayName("the team cannot move an interview to nowhere: with no other time open, the booking is kept")
        void aMoveByTheTeamWithNowhereToGoIsRefused() {
            OffsetDateTime at = interview.getScheduledAt();
            when(availability.openSlots(anyList(), anyInt(), anyInt(), any(), anyList(), any())).thenReturn(List.of());

            refused(() -> service.rescheduleByTeam(interview.getId()), HttpStatus.CONFLICT,
                    "No other time is open");

            assertThat(interview.getStatus()).isEqualTo("SCHEDULED");
            assertThat(interview.getScheduledAt()).isEqualTo(at);
            verify(calendar, never()).deleteByInterviewId(any());
            verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("moving in chat or after a no-show announces nothing: the candidate is in that conversation")
        void aMoveFromElsewhereIsNotAnnounced() {
            thereAreOtherTimes(inDays(4, 10));

            service.reschedule(interview.getId());

            verify(events, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("cancelling")
    class Cancelling {

        @Test
        void aBookedInterviewCancelledByTheTeamIsAnnouncedWithTheTimeItHad() {
            OffsetDateTime at = inDays(3, 14);
            booked(at);

            service.cancelByTeam(interview.getId());

            assertThat(interview.getStatus()).isEqualTo("CANCELED");
            verify(events).publishEvent(new InterviewCanceled(interview.getId(), application.getId(), at));
        }

        @Test
        void anInterviewNeverBookedIsCancelledQuietly() {
            service.cancelByTeam(interview.getId());

            assertThat(interview.getStatus()).isEqualTo("CANCELED");
            verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("closing an application cancels what it was waiting or booked for, and leaves the past alone")
        void closingCancelsWhatIsAhead() {
            Interview held = new Interview();
            ReflectionTestUtils.setField(held, "id", UUID.randomUUID());
            held.setApplicationId(application.getId());
            held.setStatus("SCHEDULED");
            held.setScheduledAt(OffsetDateTime.now().minusDays(10));
            Interview done = new Interview();
            ReflectionTestUtils.setField(done, "id", UUID.randomUUID());
            done.setApplicationId(application.getId());
            done.setStatus("COMPLETED");
            booked(inDays(3, 14));
            when(interviews.findByApplicationId(application.getId())).thenReturn(List.of(held, done, interview));

            service.cancelForApplication(application.getId());

            assertThat(interview.getStatus()).isEqualTo("CANCELED");
            assertThat(held.getStatus()).isEqualTo("SCHEDULED");
            assertThat(done.getStatus()).isEqualTo("COMPLETED");
            ArgumentCaptor<Object> announced = ArgumentCaptor.forClass(Object.class);
            verify(events).publishEvent(announced.capture());
            assertThat(announced.getValue()).isInstanceOf(InterviewCanceled.class);
        }
    }
}
