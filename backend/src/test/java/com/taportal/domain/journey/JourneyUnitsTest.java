package com.taportal.domain.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.api.InterviewDtos.SlotResponse;
import com.taportal.domain.application.Application;
import com.taportal.domain.application.ApplicationRepository;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.events.BookingOrigin;
import com.taportal.domain.interview.AvailabilityService;
import com.taportal.domain.interview.Interview;
import com.taportal.domain.interview.InterviewRepository;
import com.taportal.domain.interview.InterviewRoundRepository;
import com.taportal.domain.interview.InterviewService;
import com.taportal.domain.interview.InterviewService.RescheduleAttempt;
import com.taportal.domain.interview.InterviewService.RescheduleRefusal;
import com.taportal.domain.interview.InterviewService.RescheduleRefusalReason;
import com.taportal.domain.interview.InterviewService.RescheduleResult;
import com.taportal.domain.interview.InterviewSlot;
import com.taportal.domain.interview.InterviewSlotRepository;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.JobRepository;
import com.taportal.domain.journey.JourneyPlan.Plan;
import com.taportal.domain.journey.JourneyTemplates.Wording;
import com.taportal.domain.journey.JourneyUnits.Pick;
import com.taportal.domain.journey.JourneyUnits.PickNext;
import com.taportal.domain.journey.JourneyUnits.Texter;
import com.taportal.domain.messaging.CandidateMessage;
import com.taportal.domain.messaging.CandidateMessageRepository;
import com.taportal.domain.messaging.MessagePolicy;
import com.taportal.domain.messaging.MessageStore;
import com.taportal.domain.messaging.SmsOptOutRepository;
import com.taportal.domain.notification.NotificationService;
import com.taportal.domain.recruiter.RecruiterUserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The journey's rules, with everything around them stood in for. */
class JourneyUnitsTest {

    private static final String PHONE = "+12125550109";
    private static final UUID TEXT_IN = UUID.randomUUID();

    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final InterviewRepository interviews = mock(InterviewRepository.class);
    private final InterviewSlotRepository slots = mock(InterviewSlotRepository.class);
    private final InterviewRoundRepository rounds = mock(InterviewRoundRepository.class);
    private final InterviewService interviewService = mock(InterviewService.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final JourneyPlan plans = mock(JourneyPlan.class);
    private final JourneyTemplates templates = mock(JourneyTemplates.class);
    private final CandidateMessageRepository messages = mock(CandidateMessageRepository.class);
    private final SmsOptOutRepository optOuts = mock(SmsOptOutRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);

    private final List<CandidateMessage> written = new ArrayList<>();

    private final JourneyUnits units = new JourneyUnits(
            candidates, applications, jobs, interviews, slots, rounds, interviewService, availability,
            plans, templates, new JourneyValues(mock(RecruiterUserRepository.class), "https://careers.example", "Example Careers"),
            new MessageStore(messages), new MessagePolicy(optOuts), messages, notifications, new ObjectMapper());

    private Candidate raj;
    private Application application;
    private Job job;
    private Interview interview;
    private Texter texter;

    @BeforeEach
    void aCandidateWithOneApplication() {
        raj = new Candidate();
        ReflectionTestUtils.setField(raj, "id", UUID.randomUUID());
        raj.setName("Raj Patel");
        raj.setEmail("raj.patel@example.com");
        raj.setPhone("+1-212-555-0109");
        raj.setSmsConsentAt(OffsetDateTime.now().minusDays(3));

        job = new Job();
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        job.setTitle("Summer Analyst Intern");

        application = new Application();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.setCandidateId(raj.getId());
        application.setJobId(job.getId());
        application.setStage("INTERVIEW");
        ReflectionTestUtils.setField(application, "updatedAt", OffsetDateTime.now());

        interview = new Interview();
        ReflectionTestUtils.setField(interview, "id", UUID.randomUUID());
        interview.setApplicationId(application.getId());
        interview.setStatus("SLOTS_PROPOSED");
        interview.setType("RECRUITER_SCREEN");
        interview.setDurationMin(45);
        interview.setInterviewers("David Okafor, Marcus Bell");

        texter = new Texter(PHONE, TEXT_IN, List.of(raj.getId()), raj.getId());

        when(candidates.findById(raj.getId())).thenReturn(Optional.of(raj));
        when(candidates.findByPhoneE164(PHONE)).thenReturn(List.of(raj));
        when(applications.findById(application.getId())).thenReturn(Optional.of(application));
        when(applications.findByCandidateId(raj.getId())).thenReturn(List.of(application));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(interviews.findById(interview.getId())).thenReturn(Optional.of(interview));
        when(interviews.findByApplicationId(application.getId())).thenReturn(List.of(interview));
        when(interviews.findByApplicationIdIn(any())).thenReturn(List.of(interview));
        when(messages.save(any(CandidateMessage.class))).thenAnswer(call -> {
            CandidateMessage m = call.getArgument(0);
            if (m.getId() == null) {
                m.setId(UUID.randomUUID());
                written.add(m);
            }
            return m;
        });
        when(messages.hasBeenTexted(anyString())).thenReturn(true);
        journeyDraws("APPLICATION_RECEIVED", "INTERVIEW_INVITE", "INTERVIEW_CONFIRMED",
                "INTERVIEW_REMINDER_24H", "INTERVIEW_REMINDER_1H");
        when(templates.forText(any(), any())).thenAnswer(call -> {
            JourneyPoint p = call.getArgument(0);
            return List.of(new Wording(UUID.randomUUID(), p.name(), null, wording(p), null));
        });
        when(templates.forEmail(any(), any())).thenAnswer(call -> {
            JourneyPoint p = call.getArgument(0);
            return List.of(new Wording(UUID.randomUUID(), p.name(), "About {{job_title}}", "<p>Hi {{first_name}}</p>", null));
        });
    }

    /** Wording that shows what it was given, so a test can read what reached the candidate. */
    private static String wording(JourneyPoint p) {
        StringBuilder sb = new StringBuilder(p.name()).append(" for {{first_name}}");
        for (String field : p.needs().stream().sorted().toList()) {
            sb.append(" | {{").append(field).append("}}");
        }
        return sb.toString();
    }

    /** The drawing has a text step and an email step for each of these points. */
    private void journeyDraws(String... points) {
        Map<String, Optional<UUID>> steps = new HashMap<>();
        for (String p : points) {
            steps.put(p + "|SMS", Optional.empty());
            steps.put(p + "|EMAIL", Optional.empty());
        }
        when(plans.current()).thenReturn(new Plan(true, steps));
    }

    private void journeyIsOff() {
        when(plans.current()).thenReturn(new Plan(false, Map.of()));
    }

    private InterviewSlot proposed(OffsetDateTime start) {
        InterviewSlot slot = new InterviewSlot();
        ReflectionTestUtils.setField(slot, "id", UUID.randomUUID());
        slot.setInterviewId(interview.getId());
        slot.setStartsAt(start);
        slot.setEndsAt(start.plusMinutes(45));
        slot.setStatus("PROPOSED");
        return slot;
    }

    private static SlotResponse response(InterviewSlot s) {
        return new SlotResponse(s.getId(), null, null, null, s.getStartsAt(), s.getEndsAt(), false, s.getInterviewId(), s.getStatus());
    }

    /** Times are on offer for the interview, and were texted. */
    private List<InterviewSlot> anOfferIsOpen(OffsetDateTime... starts) {
        List<InterviewSlot> open = new ArrayList<>();
        StringBuilder offer = new StringBuilder("[");
        int n = 1;
        for (OffsetDateTime start : starts) {
            open.add(proposed(start));
            offer.append(n > 1 ? "," : "").append("{\"n\":").append(n++)
                    .append(",\"startsAt\":\"").append(start).append("\",\"endsAt\":\"").append(start.plusMinutes(45)).append("\"}");
        }
        offer.append("]");
        CandidateMessage sent = new CandidateMessage();
        sent.setId(UUID.randomUUID());
        sent.setInterviewId(interview.getId());
        sent.setAddress(PHONE);
        sent.setOffer(offer.toString());
        sent.setStatus(CandidateMessage.SENT);
        when(messages.openOffersTo(PHONE)).thenReturn(List.of(sent));
        when(messages.openOffersFor(interview.getId())).thenReturn(List.of(sent));
        when(slots.findByInterviewIdAndStatusOrderByStartsAt(interview.getId(), "PROPOSED")).thenReturn(open);
        when(interviewService.proposedSlots(interview.getId()))
                .thenReturn(open.stream().map(JourneyUnitsTest::response).toList());
        return open;
    }

    private void theInterviewIsBooked(OffsetDateTime at) {
        interview.setStatus("SCHEDULED");
        interview.setScheduledAt(at);
        interview.setMeetingLink("https://meet.example/abc");
        when(interviewService.bookedAhead(application.getId())).thenReturn(Optional.of(interview));
    }

    private List<String> texts() {
        return written.stream().filter(m -> CandidateMessage.SMS.equals(m.getChannel())).map(CandidateMessage::getBody).toList();
    }

    private static OffsetDateTime inDays(int days, int hour) {
        return OffsetDateTime.now().plusDays(days).withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }

    @Nested
    @DisplayName("what is sent unprompted")
    class Unprompted {

        @Test
        void anApplicationReceivedGoesByTextAndByEmail() {
            application.setStage("SCREENED");

            List<CandidateMessage> sent = units.applicationReceived(application.getId());

            assertThat(sent).extracting(CandidateMessage::getChannel, CandidateMessage::getStatus, CandidateMessage::getAddress)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("SMS", "QUEUED", PHONE),
                            org.assertj.core.groups.Tuple.tuple("EMAIL", "QUEUED", "raj.patel@example.com"));
            assertThat(sent.get(0).getDedupeKey()).isEqualTo("APPLICATION_RECEIVED:" + application.getId() + ":SMS");
            assertThat(sent.get(1).getSubject()).isEqualTo("About Summer Analyst Intern");
        }

        @Test
        @DisplayName("text only: with no email step on the drawing, no email goes")
        void onlyTheChannelsDrawnAreUsed() {
            when(plans.current()).thenReturn(new Plan(true, Map.of("APPLICATION_RECEIVED|SMS", Optional.empty())));

            assertThat(units.applicationReceived(application.getId()))
                    .extracting(CandidateMessage::getChannel).containsExactly("SMS");
        }

        @Test
        void withTheJourneySwitchedOffNothingIsSent() {
            journeyIsOff();

            assertThat(units.applicationReceived(application.getId())).isEmpty();
            assertThat(written).isEmpty();
        }

        @Test
        void aClosedApplicationIsToldNothing() {
            application.setStage("REJECTED");

            assertThat(units.applicationReceived(application.getId())).isEmpty();
            assertThat(units.selectedForInterview(application.getId())).isEmpty();
        }

        @Test
        @DisplayName("the same thing is never sent twice")
        void whatIsOnRecordIsNotWrittenAgain() {
            when(messages.existsByDedupeKey("APPLICATION_RECEIVED:" + application.getId() + ":SMS")).thenReturn(true);

            assertThat(units.applicationReceived(application.getId()))
                    .extracting(CandidateMessage::getChannel).containsExactly("EMAIL");
        }

        @Test
        @DisplayName("without agreement the text is held, the email goes, and the record says why")
        void withoutAgreementTheTextIsHeld() {
            raj.setSmsConsentAt(null);

            List<CandidateMessage> sent = units.applicationReceived(application.getId());

            assertThat(sent).extracting(CandidateMessage::getChannel, CandidateMessage::getStatus)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("SMS", "SUPPRESSED"),
                            org.assertj.core.groups.Tuple.tuple("EMAIL", "QUEUED"));
            assertThat(sent.get(0).getReason()).isEqualTo("No agreement to be texted on record");
            assertThat(sent.get(0).getBody()).isEmpty();
            verify(notifications, never()).notifyRole(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("when it reaches the candidate on no channel, the recruiters are told")
        void reachingNobodyIsReported() {
            raj.setSmsConsentAt(null);
            raj.setEmail("I don't have one");

            units.applicationReceived(application.getId());

            verify(notifications).notifyRole(eq("RECRUITER"), eq("MESSAGE"),
                    eq("Raj Patel was not told: when the candidate applies"),
                    eq("Text: No agreement to be texted on record. Email: The email address on record is not a valid address"),
                    eq("/candidates/" + raj.getId()));
        }

        @Test
        void theFirstTextToANumberSaysHowToStop() {
            when(messages.hasBeenTexted(PHONE)).thenReturn(false);

            units.applicationReceived(application.getId());

            assertThat(texts().get(0)).endsWith(" Reply STOP to opt out, HELP for help. Msg and data rates may apply.");
        }

        @Test
        void aTextIsPlainCharacters() {
            raj.setName("Renée “Ray” O’Neil");

            units.applicationReceived(application.getId());

            assertThat(texts().get(0)).isEqualTo("APPLICATION_RECEIVED for Renée");
        }
    }

    @Nested
    @DisplayName("wording")
    class WordingRules {

        @Test
        @DisplayName("a template that asks for what there is no value for is passed over for the journey's own")
        void aTemplateThatCannotBeFilledIsPassedOver() {
            org.mockito.Mockito.doReturn(List.of(
                    new Wording(UUID.randomUUID(), "Chosen on the drawing", null, "See you at {{event_name}}", null),
                    new Wording(UUID.randomUUID(), "The journey's own", null, "Hi {{first_name}}", null)))
                    .when(templates).forText(eq(JourneyPoint.APPLICATION_RECEIVED), any());

            units.applicationReceived(application.getId());

            assertThat(texts()).containsExactly("Hi Raj");
            assertThat(written.get(0).getTemplateName()).isEqualTo("The journey's own");
        }

        @Test
        @DisplayName("a message is never sent half filled: it fails, and says which field")
        void aMessageIsNeverSentHalfFilled() {
            org.mockito.Mockito.doReturn(List.of(
                    new Wording(UUID.randomUUID(), "Broken", null, "See you at {{event_name}}, {{first_name}}", null)))
                    .when(templates).forText(eq(JourneyPoint.APPLICATION_RECEIVED), any());

            List<CandidateMessage> sent = units.applicationReceived(application.getId());

            assertThat(sent.get(0).getStatus()).isEqualTo("FAILED");
            assertThat(sent.get(0).getReason()).isEqualTo("The template needs {{event_name}}, which has no value here");
            assertThat(sent.get(0).getBody()).isEmpty();
        }

        @Test
        void aPointWithNoWordingAtAllFails() {
            org.mockito.Mockito.doReturn(List.of())
                    .when(templates).forText(eq(JourneyPoint.APPLICATION_RECEIVED), any());

            assertThat(units.applicationReceived(application.getId()).get(0).getReason())
                    .isEqualTo("The journey has no active template for “When the candidate applies”");
        }
    }

    @Nested
    @DisplayName("selected for interview")
    class Invite {

        @Test
        void timesOnOfferAreTextedNumberedAndKeptWithTheMessage() {
            OffsetDateTime first = inDays(3, 14);
            anOfferIsOpen(first, first.plusDays(1));
            when(messages.openOffersTo(PHONE)).thenReturn(List.of());

            List<CandidateMessage> sent = units.selectedForInterview(application.getId());

            CandidateMessage text = sent.get(0);
            assertThat(text.getPoint()).isEqualTo("INTERVIEW_INVITE");
            assertThat(text.getBody()).isEqualTo("INTERVIEW_INVITE for Raj | "
                    + JourneyTimes.numbered(List.of(first, first.plusDays(1))));
            assertThat(units.readOffer(text.getOffer()))
                    .extracting(JourneyUnits.Offered::n, o -> o.startsAt().toInstant())
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(1, first.toInstant()),
                            org.assertj.core.groups.Tuple.tuple(2, first.plusDays(1).toInstant()));
            assertThat(sent.get(1).getChannel()).isEqualTo("EMAIL");
            assertThat(sent.get(1).getOffer()).as("only the text is answered with a number").isNull();
        }

        @Test
        @DisplayName("already booked: no second interview, no invitation, and the record says so")
        void aCandidateAlreadyBookedIsNotInvitedAgain() {
            theInterviewIsBooked(inDays(2, 15));

            List<CandidateMessage> sent = units.selectedForInterview(application.getId());

            assertThat(sent).singleElement().satisfies(m -> {
                assertThat(m.getStatus()).isEqualTo("SUPPRESSED");
                assertThat(m.getReason()).startsWith("Already booked for ").endsWith(": no invitation was sent");
            });
            verify(interviewService, never()).beginSelfSchedule(any(), any(), anyInt());
            verify(interviewService, never()).autoPropose(any());
        }

        @Test
        void withNoInterviewYetOneIsBegunForTheFirstRoundOrAsARecruiterScreen() {
            when(interviews.findByApplicationId(application.getId())).thenReturn(List.of());
            when(rounds.findByJobIdOrderByRoundNo(job.getId())).thenReturn(List.of());
            when(interviewService.beginSelfSchedule(application.getId(), "RECRUITER_SCREEN", 45)).thenReturn(interview);
            when(interviewService.proposedSlots(interview.getId())).thenReturn(List.of());

            units.selectedForInterview(application.getId());

            verify(interviewService).beginSelfSchedule(application.getId(), "RECRUITER_SCREEN", 45);
        }

        @Test
        @DisplayName("calendars full: the candidate is told times are being arranged, once a day at most")
        void withNoTimesTheCandidateIsToldSo() {
            when(interviewService.proposedSlots(interview.getId())).thenReturn(List.of());

            List<CandidateMessage> sent = units.selectedForInterview(application.getId());

            assertThat(sent).singleElement().satisfies(m -> {
                assertThat(m.getPoint()).isEqualTo("NO_TIMES_AVAILABLE");
                assertThat(m.getDedupeKey()).startsWith("NO_TIMES_AVAILABLE:" + interview.getId() + ":");
                assertThat(m.getOffer()).isNull();
            });
        }

        @Test
        @DisplayName("one open offer for a number at a time: a second invitation goes by link")
        void aSecondInvitationGoesByLink() {
            OffsetDateTime first = inDays(3, 14);
            anOfferIsOpen(first);
            Interview other = new Interview();
            ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
            other.setApplicationId(application.getId());
            other.setStatus("SLOTS_PROPOSED");
            when(interviews.findById(other.getId())).thenReturn(Optional.of(other));
            CandidateMessage earlier = new CandidateMessage();
            earlier.setId(UUID.randomUUID());
            earlier.setInterviewId(other.getId());
            earlier.setOffer("[]");
            when(messages.openOffersTo(PHONE)).thenReturn(List.of(earlier));

            List<CandidateMessage> sent = units.selectedForInterview(application.getId());

            assertThat(sent).extracting(CandidateMessage::getChannel, CandidateMessage::getPoint)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("SMS", "INVITE_BY_LINK"),
                            org.assertj.core.groups.Tuple.tuple("EMAIL", "INTERVIEW_INVITE"));
            assertThat(sent.get(0).getOffer()).isNull();
            assertThat(sent.get(0).getBody()).contains("https://careers.example/schedule/" + interview.getId());
        }

        @Test
        void aTimeTooCloseToPickIsNotOffered() {
            anOfferIsOpen(OffsetDateTime.now().plusMinutes(40), inDays(3, 14));
            when(messages.openOffersTo(PHONE)).thenReturn(List.of());

            List<CandidateMessage> sent = units.selectedForInterview(application.getId());

            assertThat(units.readOffer(sent.get(0).getOffer())).hasSize(1);
        }
    }

    @Nested
    @DisplayName("a number in reply")
    class Picking {

        @Test
        void aNumberOfTheOfferNamesTheTimeToBook() {
            List<InterviewSlot> open = anOfferIsOpen(inDays(3, 14), inDays(4, 15), inDays(5, 16));

            Pick pick = units.pick(texter, 2);

            assertThat(pick.next()).isEqualTo(PickNext.BOOK);
            assertThat(pick.slotId()).isEqualTo(open.get(1).getId());
            assertThat(written).isEmpty();
        }

        @Test
        @DisplayName("the number means the time that was read, whatever was proposed again since")
        void theTimeIsFoundByWhenItIsNotByWhichRowItIs() {
            OffsetDateTime a = inDays(3, 14);
            OffsetDateTime b = inDays(4, 15);
            anOfferIsOpen(a, b);
            // Proposed again since: the same times are new rows, in another order.
            InterviewSlot newB = proposed(b);
            InterviewSlot newA = proposed(a);
            when(slots.findByInterviewIdAndStatusOrderByStartsAt(interview.getId(), "PROPOSED"))
                    .thenReturn(List.of(newB, newA));

            assertThat(units.pick(texter, 1).slotId()).isEqualTo(newA.getId());
            assertThat(units.pick(texter, 2).slotId()).isEqualTo(newB.getId());
        }

        @Test
        void aTimeNoLongerOnOfferIsGone() {
            anOfferIsOpen(inDays(3, 14), inDays(4, 15));
            when(slots.findByInterviewIdAndStatusOrderByStartsAt(interview.getId(), "PROPOSED"))
                    .thenReturn(List.of(proposed(inDays(6, 10))));

            Pick pick = units.pick(texter, 1);

            assertThat(pick.next()).isEqualTo(PickNext.GONE);
            assertThat(pick.interviewId()).isEqualTo(interview.getId());
        }

        @Test
        @DisplayName("a time that has come too close is gone, even though its row is still there")
        void aTimeTooCloseIsGone() {
            anOfferIsOpen(OffsetDateTime.now().plusMinutes(30));

            assertThat(units.pick(texter, 1).next()).isEqualTo(PickNext.GONE);
        }

        @Test
        void aNumberThatIsNotOneOfTheTimesGetsTheTimesAgain() {
            OffsetDateTime a = inDays(3, 14);
            anOfferIsOpen(a, a.plusDays(1));

            Pick pick = units.pick(texter, 5);

            assertThat(pick.next()).isEqualTo(PickNext.ANSWERED);
            assertThat(pick.written()).singleElement().satisfies(m -> {
                assertThat(m.getPoint()).isEqualTo("PICK_A_NUMBER");
                assertThat(m.getBody()).contains("1) " + JourneyTimes.when(a)).contains("2) ");
                assertThat(m.getOffer()).as("the offer that is open stays the offer").isNull();
            });
        }

        @Test
        @DisplayName("with the interview already booked, a number changes nothing and says when it is")
        void aNumberAfterBookingChangesNothing() {
            OffsetDateTime at = inDays(2, 15);
            anOfferIsOpen(at);
            theInterviewIsBooked(at);

            Pick pick = units.pick(texter, 1);

            assertThat(pick.next()).isEqualTo(PickNext.ANSWERED);
            assertThat(pick.written()).singleElement().satisfies(m -> {
                assertThat(m.getPoint()).isEqualTo("ALREADY_BOOKED");
                assertThat(m.getBody()).isEqualTo("ALREADY_BOOKED for Raj | " + JourneyTimes.when(at));
            });
            verify(interviewService, never()).autoPropose(any());
        }

        @Test
        void aNumberForAnApplicationSinceClosedBooksNothing() {
            anOfferIsOpen(inDays(3, 14));
            application.setStage("REJECTED");
            when(interviews.findByApplicationIdIn(any())).thenReturn(List.of());

            Pick pick = units.pick(texter, 1);

            assertThat(pick.next()).isEqualTo(PickNext.ANSWERED);
            assertThat(pick.written()).extracting(CandidateMessage::getPoint).containsExactly("HELP");
        }

        @Test
        void aNumberWithNothingOnOfferGetsHelp() {
            when(messages.openOffersTo(PHONE)).thenReturn(List.of());
            when(interviews.findByApplicationIdIn(any())).thenReturn(List.of());

            assertThat(units.pick(texter, 2).written()).extracting(CandidateMessage::getPoint).containsExactly("HELP");
        }

        @Test
        @DisplayName("a time that is gone is answered with the times there are, kept as the new offer")
        void goneIsAnsweredWithWhatThereIs() {
            OffsetDateTime a = inDays(3, 14);
            anOfferIsOpen(a);

            List<CandidateMessage> sent = units.gone(texter, interview.getId());

            assertThat(sent).singleElement().satisfies(m -> {
                assertThat(m.getPoint()).isEqualTo("SLOT_TAKEN");
                assertThat(units.readOffer(m.getOffer())).hasSize(1);
                assertThat(m.getDedupeKey()).isEqualTo("reply:" + TEXT_IN + ":SLOT_TAKEN");
            });
        }

        @Test
        void goneWhenTheInterviewIsBookedSaysWhenItIs() {
            theInterviewIsBooked(inDays(2, 15));

            assertThat(units.gone(texter, interview.getId()))
                    .extracting(CandidateMessage::getPoint).containsExactly("ALREADY_BOOKED");
        }
    }

    @Nested
    @DisplayName("booked")
    class Booked {

        @Test
        void aBookingIsConfirmedByTextAndEmail() {
            OffsetDateTime at = inDays(2, 15);
            theInterviewIsBooked(at);

            List<CandidateMessage> sent = units.booked(interview.getId(), at, BookingOrigin.WEB_PAGE);

            assertThat(sent).extracting(CandidateMessage::getChannel, CandidateMessage::getPoint)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("SMS", "INTERVIEW_CONFIRMED"),
                            org.assertj.core.groups.Tuple.tuple("EMAIL", "INTERVIEW_CONFIRMED"));
            assertThat(sent.get(0).getDedupeKey())
                    .isEqualTo("INTERVIEW_CONFIRMED:" + interview.getId() + ":" + at.toEpochSecond() + ":SMS");
        }

        @Test
        @DisplayName("booked by text: the confirmation is the answer to their text, and is given whatever the drawing says")
        void aBookingByTextIsAlwaysAnswered() {
            journeyIsOff();
            raj.setSmsConsentAt(null);
            OffsetDateTime at = inDays(2, 15);
            theInterviewIsBooked(at);

            List<CandidateMessage> sent = units.booked(interview.getId(), at, BookingOrigin.TEXT);

            assertThat(sent).extracting(CandidateMessage::getChannel, CandidateMessage::getStatus)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("SMS", "QUEUED"));
        }

        @Test
        void aBookingByAnotherRouteFollowsTheDrawing() {
            journeyIsOff();
            OffsetDateTime at = inDays(2, 15);
            theInterviewIsBooked(at);

            assertThat(units.booked(interview.getId(), at, BookingOrigin.CHAT)).isEmpty();
        }

        @Test
        @DisplayName("news of a booking that has changed since is not sent")
        void aBookingThatChangedSinceIsNotConfirmed() {
            OffsetDateTime at = inDays(2, 15);
            theInterviewIsBooked(at.plusDays(1));

            assertThat(units.booked(interview.getId(), at, BookingOrigin.WEB_PAGE)).isEmpty();
            interview.setStatus("REQUESTED");
            assertThat(units.booked(interview.getId(), at.plusDays(1), BookingOrigin.WEB_PAGE)).isEmpty();
        }
    }

    @Nested
    @DisplayName("reminders")
    class Reminders {

        @Test
        void aReminderIsKeyedByTheTimeItIsFor() {
            OffsetDateTime at = inDays(1, 10);
            theInterviewIsBooked(at);

            List<CandidateMessage> sent = units.remind(interview.getId(), JourneyPoint.INTERVIEW_REMINDER_24H);

            assertThat(sent.get(0).getDedupeKey())
                    .isEqualTo("INTERVIEW_REMINDER_24H:" + interview.getId() + ":" + at.toEpochSecond() + ":SMS");
        }

        @Test
        @DisplayName("a reminder held back is recorded once, not once a minute")
        void aHeldReminderIsRecordedOnce() {
            raj.setSmsConsentAt(null);
            OffsetDateTime at = inDays(1, 10);
            theInterviewIsBooked(at);
            String held = "INTERVIEW_REMINDER_1H:" + interview.getId() + ":" + at.toEpochSecond() + ":SMS:held";

            List<CandidateMessage> first = units.remind(interview.getId(), JourneyPoint.INTERVIEW_REMINDER_1H);
            assertThat(first).singleElement().satisfies(m -> {
                assertThat(m.getStatus()).isEqualTo("SUPPRESSED");
                assertThat(m.getDedupeKey()).isEqualTo(held);
            });

            when(messages.existsByDedupeKey(held)).thenReturn(true);
            assertThat(units.remind(interview.getId(), JourneyPoint.INTERVIEW_REMINDER_1H)).isEmpty();
        }

        @Test
        void nobodyIsRemindedOfAnInterviewNoLongerBookedOrAnApplicationNoLongerRunning() {
            assertThat(units.remind(interview.getId(), JourneyPoint.INTERVIEW_REMINDER_1H)).isEmpty();

            theInterviewIsBooked(inDays(1, 10));
            application.setStage("WITHDRAWN");
            assertThat(units.remind(interview.getId(), JourneyPoint.INTERVIEW_REMINDER_1H)).isEmpty();
        }
    }

    @Nested
    @DisplayName("what a candidate asks by text")
    class Asking {

        @Test
        void statusSaysWhereTheApplicationStands() {
            application.setStage("APPLIED");

            List<CandidateMessage> sent = units.status(texter);

            assertThat(sent).singleElement().satisfies(m -> assertThat(m.getBody()).isEqualTo(
                    "STATUS_REPLY for Raj | Your application for Summer Analyst Intern: we have it and it is under review."));
        }

        @Test
        @DisplayName("someone who applied twice is one person by text: status covers every application")
        void statusCoversEveryRecordWithTheNumber() {
            Candidate again = new Candidate();
            ReflectionTestUtils.setField(again, "id", UUID.randomUUID());
            again.setName("Raj Patel");
            again.setPhone("(212) 555-0109");
            Job teller = new Job();
            ReflectionTestUtils.setField(teller, "id", UUID.randomUUID());
            teller.setTitle("Teller");
            Application second = new Application();
            ReflectionTestUtils.setField(second, "id", UUID.randomUUID());
            second.setCandidateId(again.getId());
            second.setJobId(teller.getId());
            second.setStage("REJECTED");
            ReflectionTestUtils.setField(second, "updatedAt", OffsetDateTime.now().plusMinutes(5));
            when(candidates.findById(again.getId())).thenReturn(Optional.of(again));
            when(applications.findByCandidateId(again.getId())).thenReturn(List.of(second));
            when(jobs.findById(teller.getId())).thenReturn(Optional.of(teller));
            when(interviews.findByApplicationId(second.getId())).thenReturn(List.of());
            application.setStage("SCREENED");
            when(interviews.findByApplicationId(application.getId())).thenReturn(List.of());

            List<CandidateMessage> sent = units.status(new Texter(PHONE, TEXT_IN, List.of(raj.getId(), again.getId()), raj.getId()));

            assertThat(sent.get(0).getBody()).isEqualTo("STATUS_REPLY for Raj | "
                    + "Summer Analyst Intern: you passed the first screening and the hiring team is reviewing it.\n"
                    + "Teller: we are not moving forward with it. Thank you for your interest.");
        }

        @Test
        void anAnswerNeedsNoAgreementAndNoStepOnTheDrawing() {
            journeyIsOff();
            raj.setSmsConsentAt(null);

            assertThat(units.status(texter)).extracting(CandidateMessage::getStatus).containsExactly("QUEUED");
        }

        @Test
        @DisplayName("a text handled twice is answered once")
        void anAnswerIsKeyedByTheTextItAnswers() {
            assertThat(units.help(texter).get(0).getDedupeKey()).isEqualTo("reply:" + TEXT_IN + ":HELP");

            when(messages.existsByDedupeKey("reply:" + TEXT_IN + ":HELP")).thenReturn(true);
            assertThat(units.help(texter)).isEmpty();
        }

        @Test
        @DisplayName("two machines can talk all night: after three answers in ten minutes the system stops answering")
        void aLoopIsNotFed() {
            when(messages.countAnswers(eq(PHONE), any(), any())).thenReturn(2L);
            assertThat(units.unclear(texter)).extracting(CandidateMessage::getPoint).containsExactly("HELP");
            assertThat(units.unclear(texter).get(0).getStatus()).isEqualTo("QUEUED");

            when(messages.countAnswers(eq(PHONE), any(), any())).thenReturn(3L);
            assertThat(units.unclear(texter)).singleElement().satisfies(m -> {
                assertThat(m.getStatus()).isEqualTo("SUPPRESSED");
                assertThat(m.getReason()).startsWith("Answered three times in ten minutes already");
            });
        }

        @Test
        @DisplayName("HELP asked for by name is always answered, however much was said before")
        void helpByNameIsNeverMetWithSilence() {
            when(messages.countAnswers(eq(PHONE), any(), any())).thenReturn(9L);

            assertThat(units.help(texter)).singleElement().satisfies(m -> {
                assertThat(m.getPoint()).isEqualTo("HELP");
                assertThat(m.getStatus()).isEqualTo("QUEUED");
            });
        }

        @Test
        void notUnderstoodWithTimesOnOfferGetsTheTimesAgain() {
            anOfferIsOpen(inDays(3, 14));

            assertThat(units.unclear(texter)).extracting(CandidateMessage::getPoint).containsExactly("PICK_A_NUMBER");
        }

        @Test
        void aNumberNobodyGaveIsToldSoOnceADay() {
            Texter stranger = new Texter("+13035550000", TEXT_IN, List.of(), null);

            assertThat(units.status(stranger)).singleElement().satisfies(m -> {
                assertThat(m.getPoint()).isEqualTo("UNKNOWN_SENDER");
                assertThat(m.getCandidateId()).isNull();
                assertThat(m.getAddress()).isEqualTo("+13035550000");
            });

            when(messages.countAnswers(eq("+13035550000"), any(), any())).thenReturn(1L);
            assertThat(units.status(stranger)).isEmpty();
        }
    }

    @Nested
    @DisplayName("STOP and START")
    class OptingOut {

        @Test
        void stopIsRecordedForTheNumberConfirmedAndClosesWhatWasOnOffer() {
            anOfferIsOpen(inDays(3, 14));
            CandidateMessage offer = messages.openOffersTo(PHONE).get(0);
            when(optOuts.existsById(PHONE)).thenReturn(false, true);

            List<CandidateMessage> sent = units.optOut(texter);

            verify(optOuts).save(any());
            assertThat(offer.getOfferClosedAt()).isNotNull();
            assertThat(sent).extracting(CandidateMessage::getPoint, CandidateMessage::getStatus)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("OPT_OUT", "QUEUED"));
        }

        @Test
        void afterStopWhatIsAskedIsRecordedAndNotActedOn() {
            when(optOuts.existsById(PHONE)).thenReturn(true);

            assertThat(units.notActedOn(texter, "STATUS")).singleElement().satisfies(m -> {
                assertThat(m.getStatus()).isEqualTo("SUPPRESSED");
                assertThat(m.getReason()).contains("opted out").contains("“STATUS”");
            });
            assertThat(units.status(texter)).extracting(CandidateMessage::getStatus).containsExactly("SUPPRESSED");
        }

        @Test
        @DisplayName("START is the candidate saying they may be texted")
        void startRecordsAgreement() {
            raj.setSmsConsentAt(null);
            when(candidates.findAllById(any())).thenReturn(List.of(raj));
            when(optOuts.existsById(PHONE)).thenReturn(true, false);

            List<CandidateMessage> sent = units.optIn(texter);

            verify(optOuts).deleteById(PHONE);
            assertThat(raj.getSmsConsentAt()).isNotNull();
            assertThat(sent).extracting(CandidateMessage::getPoint).containsExactly("OPT_IN");
        }
    }

    @Nested
    @DisplayName("RESCHEDULE and MORE")
    class Moving {

        @Test
        void rescheduleFindsTheSoonestInterviewBooked() {
            theInterviewIsBooked(inDays(2, 15));

            JourneyUnits.Reschedule plan = units.reschedule(texter);

            assertThat(plan.interviewId()).isEqualTo(interview.getId());
            assertThat(plan.written()).isEmpty();
        }

        @Test
        @DisplayName("nothing booked but times on offer: they want other times, and no change is counted")
        void rescheduleWithAnOfferOpenIsMore() {
            anOfferIsOpen(inDays(3, 14));

            JourneyUnits.Reschedule plan = units.reschedule(texter);

            assertThat(plan.interviewId()).isNull();
            assertThat(plan.asMore()).isTrue();
        }

        @Test
        void rescheduleWithNothingBookedOrOfferedSaysSo() {
            when(messages.openOffersTo(PHONE)).thenReturn(List.of());
            interview.setStatus("COMPLETED");

            assertThat(units.reschedule(texter).written())
                    .extracting(CandidateMessage::getPoint).containsExactly("NOTHING_TO_RESCHEDULE");
        }

        @Test
        void aMoveIsAnsweredWithTheNewTimes() {
            OffsetDateTime a = inDays(4, 11);
            anOfferIsOpen(a);

            List<CandidateMessage> sent = units.rescheduled(texter, interview.getId(),
                    new RescheduleAttempt(RescheduleResult.MOVED, null, inDays(2, 15), List.of()));

            assertThat(sent).singleElement().satisfies(m -> {
                assertThat(m.getPoint()).isEqualTo("RESCHEDULE_OPTIONS");
                assertThat(units.readOffer(m.getOffer())).hasSize(1);
            });
        }

        @Test
        @DisplayName("a refusal says why, says the interview stands, and puts it in front of a recruiter")
        void aRefusalSaysWhy() {
            OffsetDateTime at = inDays(2, 15);
            theInterviewIsBooked(at);

            List<CandidateMessage> limit = units.rescheduled(texter, interview.getId(), new RescheduleAttempt(
                    RescheduleResult.REFUSED,
                    new RescheduleRefusal(RescheduleRefusalReason.LIMIT_REACHED, "…", 0), at, List.of()));
            assertThat(limit.get(0).getBody())
                    .isEqualTo("RESCHEDULE_DECLINED for Raj | You have used the changes allowed for this interview.");

            written.clear();
            List<CandidateMessage> close = units.rescheduled(new Texter(PHONE, UUID.randomUUID(), List.of(raj.getId()), raj.getId()),
                    interview.getId(), new RescheduleAttempt(RescheduleResult.REFUSED,
                            new RescheduleRefusal(RescheduleRefusalReason.TOO_CLOSE, "…", 12), at, List.of()));
            assertThat(close.get(0).getBody()).endsWith("It is less than 12 hours away.");
            verify(notifications, org.mockito.Mockito.times(2))
                    .notifyRole(eq("RECRUITER"), eq("MESSAGE"), any(), any(), any());
        }

        @Test
        void withNothingToMoveToTheBookingStandsAndTheCandidateIsToldSo() {
            OffsetDateTime at = inDays(2, 15);
            theInterviewIsBooked(at);

            List<CandidateMessage> sent = units.rescheduled(texter, interview.getId(),
                    new RescheduleAttempt(RescheduleResult.NO_OTHER_TIMES, null, at, List.of()));

            assertThat(sent.get(0).getBody()).isEqualTo("RESCHEDULE_NO_TIMES for Raj | " + JourneyTimes.when(at));
        }

        @Test
        void moreOffersLaterTimes() {
            anOfferIsOpen(inDays(3, 14));
            InterviewSlot later = proposed(inDays(6, 10));
            when(interviewService.proposeLater(interview.getId())).thenReturn(List.of(response(later)));
            when(interviewService.proposedSlots(interview.getId())).thenReturn(List.of(response(later)));

            assertThat(units.more(texter)).singleElement().satisfies(m -> {
                assertThat(m.getPoint()).isEqualTo("MORE_TIMES");
                assertThat(m.getBody()).contains("1) " + JourneyTimes.when(later.getStartsAt()));
            });
        }

        @Test
        @DisplayName("nothing later: what is on offer stays on offer, and the candidate is told these are the times")
        void moreWithNothingLaterKeepsWhatIsOnOffer() {
            OffsetDateTime a = inDays(3, 14);
            anOfferIsOpen(a);
            when(interviewService.proposeLater(interview.getId())).thenReturn(List.of());

            assertThat(units.more(texter)).singleElement().satisfies(m -> {
                assertThat(m.getPoint()).isEqualTo("NO_OTHER_TIMES");
                assertThat(m.getBody()).contains("1) " + JourneyTimes.when(a));
            });
        }
    }
}
