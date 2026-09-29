package com.taportal.domain.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taportal.api.InterviewDtos.SlotResponse;
import com.taportal.domain.application.Application;
import com.taportal.domain.application.ApplicationRepository;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.events.BookingOrigin;
import com.taportal.domain.events.InterviewTimesOffered;
import com.taportal.domain.interview.AvailabilityService;
import com.taportal.domain.interview.Interview;
import com.taportal.domain.interview.InterviewRound;
import com.taportal.domain.interview.InterviewRoundRepository;
import com.taportal.domain.interview.InterviewRepository;
import com.taportal.domain.interview.InterviewService;
import com.taportal.domain.interview.InterviewService.RescheduleAttempt;
import com.taportal.domain.interview.InterviewService.RescheduleRefusalReason;
import com.taportal.domain.interview.InterviewSlot;
import com.taportal.domain.interview.InterviewSlotRepository;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.JobRepository;
import com.taportal.domain.journey.JourneyPlan.Plan;
import com.taportal.domain.journey.JourneyTemplates.Wording;
import com.taportal.domain.messaging.CandidateMessage;
import com.taportal.domain.messaging.CandidateMessageRepository;
import com.taportal.domain.messaging.MessagePolicy;
import com.taportal.domain.messaging.MessageStore;
import com.taportal.domain.messaging.MessageStore.Draft;
import com.taportal.domain.messaging.SmsText;
import com.taportal.domain.messaging.TemplateRenderer;
import com.taportal.domain.messaging.TemplateRenderer.Rendered;
import com.taportal.domain.notification.NotificationService;
import com.taportal.domain.recruiter.RecruiterUser;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The journey's units of work. Each public method is one unit: it runs in a
 * transaction of its own, so it commits what it did whether it was called from
 * a listener after another transaction committed, from a scheduled job, or
 * while answering a text. A unit records messages; it never sends them —
 * {@link JourneyService} does, once the unit has committed.
 *
 * <p>Every rule of the journey is here or in the classes this one calls:
 * nothing is decided on a screen.
 */
@Service
public class JourneyUnits {

    /** A time picked by text must still be this far off, or it is treated as gone. */
    static final Duration PICK_NOTICE = Duration.ofHours(1);

    /** Answers to texts that were not understood, in ten minutes, before the system goes quiet. */
    private static final int ANSWERS_BEFORE_SILENCE = 3;
    private static final Set<String> ANSWERS_THAT_CAN_LOOP = Set.of("HELP", "PICK_A_NUMBER", "UNKNOWN_SENDER");

    private static final String FIRST_TEXT_FOOTER = " Reply STOP to opt out, HELP for help. Msg and data rates may apply.";

    private final CandidateRepository candidates;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final InterviewRepository interviews;
    private final InterviewSlotRepository slots;
    private final InterviewRoundRepository rounds;
    private final InterviewService interviewService;
    private final AvailabilityService availability;
    private final JourneyPlan plans;
    private final JourneyTemplates templates;
    private final JourneyValues values;
    private final MessageStore store;
    private final MessagePolicy policy;
    private final CandidateMessageRepository messages;
    private final NotificationService notifications;
    private final ObjectMapper json;

    public JourneyUnits(
            CandidateRepository candidates, ApplicationRepository applications, JobRepository jobs,
            InterviewRepository interviews, InterviewSlotRepository slots, InterviewRoundRepository rounds,
            InterviewService interviewService, AvailabilityService availability,
            JourneyPlan plans, JourneyTemplates templates, JourneyValues values,
            MessageStore store, MessagePolicy policy, CandidateMessageRepository messages,
            NotificationService notifications, ObjectMapper json) {
        this.candidates = candidates;
        this.applications = applications;
        this.jobs = jobs;
        this.interviews = interviews;
        this.slots = slots;
        this.rounds = rounds;
        this.interviewService = interviewService;
        this.availability = availability;
        this.plans = plans;
        this.templates = templates;
        this.values = values;
        this.store = store;
        this.policy = policy;
        this.messages = messages;
        this.notifications = notifications;
        this.json = json;
    }

    /**
     * Who a text is from: a number, and everyone who gave it. One person who
     * applied twice is two candidate records; by text they are one person.
     *
     * @param inboundId the record of the text being answered
     * @param primary   the record used for a name; null when nobody gave this number
     * @param shared    true when the records that gave this number are not one person: nothing
     *                  about any of them is said or changed by text
     */
    public record Texter(String address, UUID inboundId, List<UUID> candidateIds, UUID primary, boolean shared) {

        public Texter(String address, UUID inboundId, List<UUID> candidateIds, UUID primary) {
            this(address, inboundId, candidateIds, primary, false);
        }

        public boolean known() {
            return !candidateIds.isEmpty();
        }
    }

    /** @param again true when this text was handled before: it is not to be handled twice */
    public record Inbound(Texter texter, boolean again) {
    }

    /** What a number picked by text leads to. */
    public enum PickNext { BOOK, GONE, ANSWERED }

    public record Pick(PickNext next, UUID offerId, UUID slotId, UUID interviewId, List<CandidateMessage> written) {
    }

    public record Reschedule(UUID interviewId, boolean asMore, List<CandidateMessage> written) {
    }

    /** One time of an offer, as it was sent. */
    public record Offered(int n, OffsetDateTime startsAt, OffsetDateTime endsAt) {
    }

    // =====================================================================
    // What happens: the journey's unprompted points
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> applicationReceived(UUID applicationId) {
        Subject s = subject(applicationId, null);
        if (s == null || StatusWording.closed(s.application.getStage())) {
            return List.of();
        }
        return tell(JourneyPoint.APPLICATION_RECEIVED, s, values.of(s.candidate, s.application, s.job, null),
                "APPLICATION_RECEIVED:" + applicationId, null, false, false);
    }

    /**
     * The hiring team moved an application to INTERVIEW. Times are taken from
     * the interviewers' calendars and offered — unless the candidate is
     * already booked, in which case inviting them again would be a second
     * interview.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> selectedForInterview(UUID applicationId) {
        Subject s = subject(applicationId, null);
        if (s == null || StatusWording.closed(s.application.getStage())) {
            return List.of();
        }
        Interview booked = interviewService.bookedAhead(applicationId).orElse(null);
        if (booked != null) {
            return List.of(store.notSent(
                    draft(JourneyPoint.INTERVIEW_INVITE, CandidateMessage.SMS, s.with(booked),
                            s.candidate.getPhoneE164(), null, "", null, null, null),
                    CandidateMessage.SUPPRESSED,
                    "Already booked for " + JourneyTimes.when(booked.getScheduledAt()) + ": no invitation was sent"));
        }
        Interview waiting = interviews.findByApplicationId(applicationId).stream()
                .filter(i -> "REQUESTED".equals(i.getStatus()) || "SLOTS_PROPOSED".equals(i.getStatus()))
                .findFirst().orElse(null);
        Interview interview;
        if (waiting != null) {
            interview = waiting;
            if (open(waiting.getId()).isEmpty()) {
                interviewService.autoPropose(waiting.getId());
            }
        } else {
            InterviewRound first = rounds.findByJobIdOrderByRoundNo(s.application.getJobId()).stream()
                    .findFirst().orElse(null);
            interview = first != null
                    ? interviewService.beginRound(applicationId, first.getId())
                    : interviewService.beginSelfSchedule(applicationId, "RECRUITER_SCREEN", 45);
        }
        return offer(interview.getId(), JourneyPoint.INTERVIEW_INVITE, null);
    }

    /** The hiring team put times on offer, or moved a booked interview. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> timesOffered(UUID interviewId, InterviewTimesOffered.Why why) {
        Interview interview = interviews.findById(interviewId).orElse(null);
        if (interview == null || InterviewService.isBooked(interview)) {
            return List.of();
        }
        Subject s = subject(interview.getApplicationId(), interview);
        if (s == null || StatusWording.closed(s.application.getStage())) {
            return List.of();
        }
        return offer(interviewId,
                why == InterviewTimesOffered.Why.MOVED ? JourneyPoint.TEAM_RESCHEDULED : JourneyPoint.INTERVIEW_INVITE,
                null);
    }

    /**
     * An interview was booked. When the candidate booked it by text, the
     * confirmation is the answer to their text and is always given; otherwise
     * it goes out as the drawing says.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> booked(UUID interviewId, OffsetDateTime at, BookingOrigin origin) {
        Interview interview = interviews.findById(interviewId).orElse(null);
        if (interview == null || !"SCHEDULED".equals(interview.getStatus()) || at == null
                || interview.getScheduledAt() == null
                || !interview.getScheduledAt().toInstant().equals(at.toInstant())) {
            return List.of(); // it has changed since: this booking is no longer the news
        }
        // Booked: nothing else on offer for this application can be picked any more.
        for (Interview other : interviews.findByApplicationId(interview.getApplicationId())) {
            store.closeOffers(other.getId());
        }
        Subject s = subject(interview.getApplicationId(), interview);
        if (s == null) {
            return List.of();
        }
        return tell(JourneyPoint.INTERVIEW_CONFIRMED, s, values.of(s.candidate, s.application, s.job, interview),
                "INTERVIEW_CONFIRMED:" + booking(interview), null,
                origin == BookingOrigin.TEXT, false);
    }

    /**
     * What tells one booking from another: the interview, the time, and when
     * it was booked. A time given up and booked again is a booking of its own,
     * with a confirmation and reminders of its own.
     */
    private static String booking(Interview interview) {
        OffsetDateTime bookedAt = interview.getBookedAt();
        return interview.getId() + ":" + interview.getScheduledAt().toEpochSecond()
                + ":" + (bookedAt == null ? 0 : bookedAt.toInstant().toEpochMilli());
    }

    /** The hiring team's side cancelled an interview the candidate was booked for. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> canceled(UUID interviewId, UUID applicationId, OffsetDateTime was) {
        store.closeOffers(interviewId);
        Interview interview = interviews.findById(interviewId).orElse(null);
        Subject s = subject(applicationId, interview);
        if (s == null || was == null) {
            return List.of();
        }
        Map<String, String> v = values.of(s.candidate, s.application, s.job, interview);
        v.put("interview_time", JourneyTimes.when(was));
        return tell(JourneyPoint.INTERVIEW_CANCELED, s, v,
                "INTERVIEW_CANCELED:" + interviewId + ":" + was.toEpochSecond(), null, false, false);
    }

    /**
     * Remind a candidate of a booked interview. The interview is read again
     * here: a reminder goes out only for an interview still booked, for an
     * application still running, and once for each time it is booked for.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> remind(UUID interviewId, JourneyPoint which) {
        return remind(interviewId, which, true);
    }

    /** @param once true from the scheduled job, which would otherwise record a held reminder every minute */
    private List<CandidateMessage> remind(UUID interviewId, JourneyPoint which, boolean once) {
        Interview interview = interviews.findById(interviewId).orElse(null);
        if (interview == null || !InterviewService.isBooked(interview)) {
            return List.of();
        }
        Subject s = subject(interview.getApplicationId(), interview);
        if (s == null || StatusWording.closed(s.application.getStage())) {
            return List.of();
        }
        return tell(which, s, values.of(s.candidate, s.application, s.job, interview),
                reminderKey(which, interview), null, false, once);
    }

    private static String reminderKey(JourneyPoint which, Interview interview) {
        return which.name() + ":" + booking(interview);
    }

    /** What is on record for a reminder of this booking: sent, held back, or nothing. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<CandidateMessage> reminded(UUID interviewId, JourneyPoint which) {
        Interview interview = interviews.findById(interviewId).orElse(null);
        if (interview == null || interview.getScheduledAt() == null) {
            return List.of();
        }
        String key = reminderKey(which, interview);
        return messages.findByDedupeKeyIn(List.of(
                key + ":SMS", key + ":EMAIL", key + ":SMS:held", key + ":EMAIL:held"));
    }

    /**
     * A candidate was told times are being arranged. When the calendars have
     * opened up since, the promise is kept: times are offered.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> offerWhenTimesOpen(UUID interviewId) {
        Interview interview = interviews.findById(interviewId).orElse(null);
        if (interview == null || InterviewService.isBooked(interview)
                || !List.of("REQUESTED", "SLOTS_PROPOSED").contains(interview.getStatus())) {
            return List.of();
        }
        Subject s = subject(interview.getApplicationId(), interview);
        if (s == null || StatusWording.closed(s.application.getStage())
                || interviewService.bookedAhead(interview.getApplicationId()).isPresent()) {
            return List.of();
        }
        if (open(interviewId).isEmpty()) {
            // Look before proposing: a proposal that finds nothing alerts the hiring team every time.
            List<UUID> team = interviewService.participantsFor(interview, s.application).stream()
                    .map(RecruiterUser::getId).toList();
            if (availability.openSlots(team, interview.getDurationMin(), 1).isEmpty()) {
                return List.of();
            }
            interviewService.autoPropose(interviewId);
        }
        if (open(interviewId).isEmpty()) {
            return List.of();
        }
        return offer(interviewId, JourneyPoint.INTERVIEW_INVITE, null, true);
    }

    // =====================================================================
    // What a candidate texts
    // =====================================================================

    /** Record a text that arrived, and work out who it is from. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Inbound inbound(String address, String text, String provider, String providerMessageId) {
        List<Candidate> people = candidates.findByPhoneE164(address);
        List<UUID> ids = people.stream().map(Candidate::getId).toList();
        boolean shared = !SamePerson.all(people);
        // A text from a number two people gave is nobody's in particular.
        UUID primary = shared ? null : primaryOf(people);
        if (providerMessageId != null && !providerMessageId.isBlank()) {
            CandidateMessage seen = messages.findByProviderAndProviderMessageId(provider, providerMessageId).orElse(null);
            if (seen != null) {
                // Delivered again. Handled already, or being handled right now: nothing to do.
                boolean handled = seen.getProcessedAt() != null
                        || seen.getCreatedAt().isAfter(OffsetDateTime.now().minusMinutes(1));
                return new Inbound(new Texter(address, seen.getId(), ids, primary, shared), handled);
            }
        }
        CandidateMessage row = store.received(primary, address, text, provider,
                providerMessageId == null || providerMessageId.isBlank() ? null : providerMessageId);
        return new Inbound(new Texter(address, row.getId(), ids, primary, shared), false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handled(UUID inboundId) {
        store.handled(inboundId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> optOut(Texter t) {
        policy.optOut(t.address());
        OffsetDateTime now = OffsetDateTime.now();
        for (CandidateMessage offer : messages.openOffersTo(t.address())) {
            offer.setOfferClosedAt(now); // times can no longer be picked by text; the email's link still works
            messages.save(offer);
        }
        return answer(JourneyPoint.OPT_OUT, t, replySubject(t, null), Map.of());
    }

    /** START is the candidate saying, in their own words, that they may be texted. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> optIn(Texter t) {
        policy.optIn(t.address());
        // START from a number two people gave is not agreement from each of them.
        for (Candidate c : t.shared() ? List.<Candidate>of() : candidates.findAllById(t.candidateIds())) {
            if (c.getSmsConsentAt() == null) {
                c.setSmsConsentAt(OffsetDateTime.now());
                candidates.save(c);
            }
        }
        return answer(JourneyPoint.OPT_IN, t, replySubject(t, null), Map.of());
    }

    /** A number that said STOP texted something other than HELP or START: it is recorded and not acted on. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> notActedOn(Texter t, String what) {
        Subject s = replySubject(t, null);
        return List.of(store.notSent(
                draft(JourneyPoint.HELP, CandidateMessage.SMS, s, t.address(), null, "", null, null, null),
                CandidateMessage.SUPPRESSED,
                "This number opted out of texts (STOP). “" + what + "” was received and not acted on"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> help(Texter t) {
        if (!t.known()) {
            return unknownSender(t);
        }
        // HELP asked for by name is always answered. Only texts nobody understood are ever met with silence.
        return answer(JourneyPoint.HELP, t, replySubject(t, null), Map.of());
    }

    /** Not understood. With times on offer, the likeliest help is the times again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> unclear(Texter t) {
        if (!t.known()) {
            return unknownSender(t);
        }
        if (t.shared()) {
            return sharedNumber(t);
        }
        if (answeredTooOften(t)) {
            return List.of(silence(t));
        }
        CandidateMessage offer = openOffer(t);
        if (offer != null) {
            return restate(t, offer);
        }
        return answer(JourneyPoint.HELP, t, replySubject(t, null), Map.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> status(Texter t) {
        if (!t.known()) {
            return unknownSender(t);
        }
        if (t.shared()) {
            return sharedNumber(t);
        }
        List<Application> apps = applicationsOf(t).stream()
                .sorted(Comparator
                        .comparing((Application a) -> StatusWording.closed(a.getStage()))
                        .thenComparing(Application::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .toList();
        if (apps.isEmpty()) {
            return unknownSender(t);
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<String> lines = new ArrayList<>();
        for (Application a : apps) {
            Interview iv = interviewThatMatters(a.getId());
            String title = jobs.findById(a.getJobId()).map(Job::getTitle).orElse("your application");
            String wording = StatusWording.of(a.getStage(),
                    iv == null ? null : iv.getStatus(), iv == null ? null : iv.getScheduledAt(), now);
            lines.add((apps.size() == 1 ? "Your application for " + title : title) + ": " + wording + ".");
        }
        Subject s = replySubject(t, apps.get(0));
        Map<String, String> v = values.of(s.candidate, s.application, s.job, null);
        v.put("status_summary", String.join("\n", lines));
        return answer(JourneyPoint.STATUS_REPLY, t, s, v);
    }

    /**
     * A number was texted. Decide what it means: a time to book, a time that
     * is gone, or something to answer at once.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Pick pick(Texter t, int n) {
        if (!t.known()) {
            return answered(unknownSender(t));
        }
        if (t.shared()) {
            return answered(sharedNumber(t));
        }
        CandidateMessage offer = openOffer(t);
        if (offer == null) {
            Interview booked = bookedFor(t);
            if (booked != null) {
                return answered(alreadyBooked(t, booked));
            }
            return answered(answeredTooOften(t)
                    ? List.of(silence(t))
                    : answer(JourneyPoint.HELP, t, replySubject(t, null), Map.of()));
        }
        List<Offered> times = readOffer(offer.getOffer());
        if (n < 1 || n > times.size()) {
            return answered(answeredTooOften(t) ? List.of(silence(t)) : restate(t, offer));
        }
        OffsetDateTime wanted = times.get(n - 1).startsAt();
        OffsetDateTime earliest = OffsetDateTime.now().plus(PICK_NOTICE);
        InterviewSlot slot = slots.findByInterviewIdAndStatusOrderByStartsAt(offer.getInterviewId(), "PROPOSED").stream()
                .filter(sl -> sl.getStartsAt().toInstant().equals(wanted.toInstant()))
                .filter(sl -> sl.getStartsAt().isAfter(earliest))
                .findFirst().orElse(null);
        if (slot == null) {
            return new Pick(PickNext.GONE, offer.getId(), null, offer.getInterviewId(), List.of());
        }
        return new Pick(PickNext.BOOK, offer.getId(), slot.getId(), offer.getInterviewId(), List.of());
    }

    /** Take the offer, so it is acted on once however many replies arrive together. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(UUID offerId) {
        return messages.claimOffer(offerId, OffsetDateTime.now()) == 1;
    }

    /** The time picked is gone. Say so, and offer what there is. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> gone(Texter t, UUID interviewId) {
        Interview interview = interviews.findById(interviewId).orElse(null);
        if (interview == null) {
            return answer(JourneyPoint.HELP, t, replySubject(t, null), Map.of());
        }
        if (InterviewService.isBooked(interview)) {
            return alreadyBooked(t, interview);
        }
        if (open(interviewId).isEmpty()) {
            interviewService.autoPropose(interviewId);
        }
        return offer(interviewId, JourneyPoint.SLOT_TAKEN, t);
    }

    /** MORE: none of the times offered suit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> more(Texter t) {
        if (!t.known()) {
            return unknownSender(t);
        }
        if (t.shared()) {
            return sharedNumber(t);
        }
        CandidateMessage offer = openOffer(t);
        if (offer == null) {
            Interview booked = bookedFor(t);
            return booked != null ? alreadyBooked(t, booked)
                    : answer(JourneyPoint.HELP, t, replySubject(t, null), Map.of());
        }
        List<SlotResponse> later = interviewService.proposeLater(offer.getInterviewId());
        if (later.isEmpty()) {
            // Nothing later: the times offered stand, and the candidate is told so.
            return offer(offer.getInterviewId(), JourneyPoint.NO_OTHER_TIMES, t);
        }
        return offer(offer.getInterviewId(), JourneyPoint.MORE_TIMES, t);
    }

    /** TIMES: show me the times. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> times(Texter t) {
        if (!t.known()) {
            return unknownSender(t);
        }
        if (t.shared()) {
            return sharedNumber(t);
        }
        CandidateMessage offer = openOffer(t);
        UUID interviewId = offer != null ? offer.getInterviewId() : waitingFor(t);
        if (interviewId != null) {
            return offer(interviewId, JourneyPoint.PICK_A_NUMBER, t);
        }
        Interview booked = bookedFor(t);
        return booked != null ? alreadyBooked(t, booked) : status(t);
    }

    /**
     * RESCHEDULE: find what is to be moved, before anything is moved.
     *
     * <p>A reply is about what was last texted. With times on offer, the
     * candidate is asking for other times, and an interview they are booked
     * for elsewhere is left alone. With more than one interview booked there
     * is no telling which is meant, so none is moved.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reschedule reschedule(Texter t) {
        if (!t.known()) {
            return new Reschedule(null, false, unknownSender(t));
        }
        if (t.shared()) {
            return new Reschedule(null, false, sharedNumber(t));
        }
        if (openOffer(t) != null) {
            return new Reschedule(null, true, List.of());
        }
        List<Interview> booked = allBookedFor(t);
        if (booked.size() == 1) {
            return new Reschedule(booked.get(0).getId(), false, List.of());
        }
        if (booked.size() > 1) {
            Interview soonest = booked.get(0);
            Subject s = subject(soonest.getApplicationId(), soonest);
            Map<String, String> v = values.of(s.candidate, s.application, s.job, soonest);
            v.put("reason", "You have more than one interview booked; to change one, use the link in its "
                    + "confirmation email.");
            askRecruiter(s, "asked by text to move an interview and has " + booked.size() + " booked",
                    "Nothing was moved: it could not be told which interview was meant");
            return new Reschedule(null, false, answer(JourneyPoint.RESCHEDULE_DECLINED, t, s, v));
        }
        return new Reschedule(null, false,
                answer(JourneyPoint.NOTHING_TO_RESCHEDULE, t, replySubject(t, null), Map.of()));
    }

    /**
     * A text that may have been asking to move an interview. Nothing is
     * moved: the candidate is told what they are booked for and how to ask.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> bookedAsItIs(Texter t, UUID interviewId) {
        Interview interview = interviews.findById(interviewId).orElse(null);
        if (interview == null || !InterviewService.isBooked(interview)) {
            return answer(JourneyPoint.NOTHING_TO_RESCHEDULE, t, replySubject(t, null), Map.of());
        }
        return alreadyBooked(t, interview);
    }

    /** Tell the candidate what came of asking to move their interview. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CandidateMessage> rescheduled(Texter t, UUID interviewId, RescheduleAttempt attempt) {
        Interview interview = interviews.findById(interviewId).orElse(null);
        if (interview == null) {
            return answer(JourneyPoint.NOTHING_TO_RESCHEDULE, t, replySubject(t, null), Map.of());
        }
        Subject s = subject(interview.getApplicationId(), interview);
        Map<String, String> v = values.of(s.candidate, s.application, s.job, interview);
        switch (attempt.result()) {
            case MOVED:
                return offer(interviewId, JourneyPoint.RESCHEDULE_OPTIONS, t);
            case REFUSED:
                v.put("reason", attempt.refusal().reason() == RescheduleRefusalReason.LIMIT_REACHED
                        ? "You have used the changes allowed for this interview."
                        : "It is less than " + attempt.refusal().hours() + " hours away.");
                askRecruiter(s, "asked by text to move an interview and could not",
                        attempt.refusal().reason() == RescheduleRefusalReason.LIMIT_REACHED
                                ? "The reschedule limit is used up" : "The interview is inside the cutoff");
                return answer(JourneyPoint.RESCHEDULE_DECLINED, t, s, v);
            case NO_OTHER_TIMES:
                askRecruiter(s, "asked by text to move an interview", "No other times are open; the booking was kept");
                return answer(JourneyPoint.RESCHEDULE_NO_TIMES, t, s, v);
            default:
                return answer(JourneyPoint.NOTHING_TO_RESCHEDULE, t, s, v);
        }
    }

    // =====================================================================
    // Offering times
    // =====================================================================

    /**
     * Send the times now on offer for an interview, and keep them with the
     * message so a number in reply means what the candidate read.
     *
     * @param texter set when this answers a text; null when nobody asked
     */
    private List<CandidateMessage> offer(UUID interviewId, JourneyPoint point, Texter texter) {
        return offer(interviewId, point, texter, false);
    }

    /** @param once true from a scheduled job: a message held back is then recorded once, not every sweep */
    private List<CandidateMessage> offer(UUID interviewId, JourneyPoint point, Texter texter, boolean once) {
        Interview interview = interviews.findById(interviewId).orElseThrow();
        Subject s = subject(interview.getApplicationId(), interview);
        List<SlotResponse> open = open(interviewId);
        // Nobody asked: what goes out, and whether anything does, is for the drawing to say.
        Plan plan = plans.current();
        boolean notice = point.kind() == JourneyPoint.Kind.NOTICE;
        boolean byText = plan.on() && (notice || plan.draws(point, CandidateMessage.SMS));
        boolean byEmail = plan.on() && point.byEmail() && (notice || plan.draws(point, CandidateMessage.EMAIL));
        if (open.isEmpty()) {
            Map<String, String> v = values.of(s.candidate, s.application, s.job, interview);
            if (texter != null) {
                return answer(JourneyPoint.NO_TIMES_AVAILABLE, texter, s, v);
            }
            if (!byText) {
                return List.of(); // no text would follow when times open, so none is promised
            }
            // Once a day at most: the promise is "we will text you", not a running commentary.
            String key = "NO_TIMES_AVAILABLE:" + interviewId + ":" + OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
            CandidateMessage told = write(JourneyPoint.NO_TIMES_AVAILABLE, CandidateMessage.SMS, s, v,
                    s.candidate.getPhoneE164(), false, key, null, true);
            return told == null ? List.of() : List.of(told);
        }
        List<OffsetDateTime> times = open.stream().map(SlotResponse::startsAt).toList();
        Map<String, String> v = values.of(s.candidate, s.application, s.job, interview);
        JourneyValues.offer(v, times);
        String offered = writeOffer(open);

        if (texter != null) {
            store.closeOffers(interviewId);
            return answer(point, texter, s, v, offered);
        }
        // One key for one putting-on-offer: the same times offered on another occasion are news again.
        String key = point.name() + ":" + interviewId + ":" + proposal(interviewId);
        // Numbers in reply need one open offer for a number, and one person behind it: with two
        // offers "2" would have two meanings, and a shared phone cannot say whose reply it is.
        String address = s.candidate.getPhoneE164();
        boolean anotherOpen = address != null && messages.openOffersTo(address).stream()
                .anyMatch(m -> !interviewId.equals(m.getInterviewId()) && stillOpen(m));
        boolean shared = address != null && !SamePerson.all(candidates.findByPhoneE164(address));
        if (anotherOpen || shared) {
            JourneyPoint byLink = point == JourneyPoint.TEAM_RESCHEDULED
                    ? JourneyPoint.TEAM_RESCHEDULED_BY_LINK : JourneyPoint.INVITE_BY_LINK;
            List<CandidateMessage> written = new ArrayList<>();
            if (byText) {
                add(written, write(byLink, CandidateMessage.SMS, s, v, address, false, key + ":LINK:SMS", null, once));
            }
            if (byEmail) {
                add(written, write(point, CandidateMessage.EMAIL, s, v, s.candidate.getEmail(), false,
                        key + ":EMAIL", null, once));
            }
            alertWhenUnreached(point, s, written);
            return written;
        }
        if (store.known(key + ":SMS")) {
            return List.of(); // this very offer was sent already
        }
        store.closeOffers(interviewId);
        return tell(point, s, v, key, offered, false, once);
    }

    /**
     * What tells one putting-on-offer from another: the first of the times put
     * on offer together. Times offered again are new rows, so an interview
     * moved twice is announced twice, even to the same times.
     */
    private String proposal(UUID interviewId) {
        return slots.findByInterviewIdAndStatusOrderByStartsAt(interviewId, "PROPOSED").stream()
                .findFirst().map(sl -> String.valueOf(sl.getId())).orElse("none");
    }

    /** The times on offer that can still be picked. */
    private List<SlotResponse> open(UUID interviewId) {
        OffsetDateTime earliest = OffsetDateTime.now().plus(PICK_NOTICE);
        return interviewService.proposedSlots(interviewId).stream()
                .filter(sl -> sl.startsAt().isAfter(earliest))
                .toList();
    }

    /** Say the times of an open offer again, as they were sent. */
    private List<CandidateMessage> restate(Texter t, CandidateMessage offer) {
        Interview interview = interviews.findById(offer.getInterviewId()).orElse(null);
        Subject s = interview == null ? replySubject(t, null) : subject(interview.getApplicationId(), interview);
        Map<String, String> v = values.of(s.candidate, s.application, s.job, interview);
        JourneyValues.offer(v, readOffer(offer.getOffer()).stream().map(Offered::startsAt).toList());
        return answer(JourneyPoint.PICK_A_NUMBER, t, s, v);
    }

    private List<CandidateMessage> alreadyBooked(Texter t, Interview booked) {
        Subject s = subject(booked.getApplicationId(), booked);
        return answer(JourneyPoint.ALREADY_BOOKED, t, s, values.of(s.candidate, s.application, s.job, booked));
    }

    private String writeOffer(List<SlotResponse> open) {
        ArrayNode array = json.createArrayNode();
        int n = 1;
        for (SlotResponse slot : open) {
            ObjectNode one = array.addObject();
            one.put("n", n++);
            one.put("startsAt", slot.startsAt().toString());
            one.put("endsAt", slot.endsAt().toString());
        }
        return array.toString();
    }

    List<Offered> readOffer(String offer) {
        List<Offered> times = new ArrayList<>();
        try {
            for (JsonNode one : json.readTree(offer == null ? "[]" : offer)) {
                times.add(new Offered(one.path("n").asInt(),
                        OffsetDateTime.parse(one.path("startsAt").asText()),
                        OffsetDateTime.parse(one.path("endsAt").asText())));
            }
        } catch (Exception e) {
            return List.of();
        }
        return times;
    }

    // =====================================================================
    // Who and what a text is about
    // =====================================================================

    /** The newest offer to this number that can still be acted on. Offers that cannot are closed on the way. */
    private CandidateMessage openOffer(Texter t) {
        for (CandidateMessage offer : messages.openOffersTo(t.address())) {
            if (stillOpen(offer)) {
                return offer;
            }
            offer.setOfferClosedAt(OffsetDateTime.now());
            messages.save(offer);
        }
        return null;
    }

    private boolean stillOpen(CandidateMessage offer) {
        if (offer.getInterviewId() == null) {
            return false;
        }
        Interview interview = interviews.findById(offer.getInterviewId()).orElse(null);
        if (interview == null || InterviewService.isBooked(interview)
                || !List.of("REQUESTED", "SLOTS_PROPOSED").contains(interview.getStatus())) {
            return false;
        }
        // Booked some other way since the offer went out: picking a time now would book a second interview.
        if (interviewService.bookedAhead(interview.getApplicationId()).isPresent()) {
            return false;
        }
        return applications.findById(interview.getApplicationId())
                .map(a -> !StatusWording.closed(a.getStage()))
                .orElse(false);
    }

    private List<Application> applicationsOf(Texter t) {
        List<Application> all = new ArrayList<>();
        for (UUID id : t.candidateIds()) {
            all.addAll(applications.findByCandidateId(id));
        }
        return all;
    }

    private List<Application> runningApplicationsOf(Texter t) {
        return applicationsOf(t).stream().filter(a -> !StatusWording.closed(a.getStage())).toList();
    }

    /** The soonest interview this person is booked for, among applications still running. */
    private Interview bookedFor(Texter t) {
        List<Interview> all = allBookedFor(t);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every interview this person is booked for, soonest first. */
    private List<Interview> allBookedFor(Texter t) {
        List<UUID> ids = runningApplicationsOf(t).stream().map(Application::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return interviews.findByApplicationIdIn(ids).stream()
                .filter(InterviewService::isBooked)
                .sorted(Comparator.comparing(Interview::getScheduledAt))
                .toList();
    }

    /** An interview with times on offer that were never texted: offered in chat, or on the web page. */
    private UUID waitingFor(Texter t) {
        List<UUID> ids = runningApplicationsOf(t).stream().map(Application::getId).toList();
        if (ids.isEmpty()) {
            return null;
        }
        return interviews.findByApplicationIdIn(ids).stream()
                .filter(i -> "SLOTS_PROPOSED".equals(i.getStatus()))
                .filter(i -> !open(i.getId()).isEmpty())
                .max(Comparator.comparing(Interview::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(Interview::getId)
                .orElse(null);
    }

    /** Of an application's interviews, the one a candidate would mean: booked ahead, else on offer, else the latest. */
    private Interview interviewThatMatters(UUID applicationId) {
        List<Interview> all = interviews.findByApplicationId(applicationId);
        return all.stream().filter(InterviewService::isBooked).min(Comparator.comparing(Interview::getScheduledAt))
                .or(() -> all.stream().filter(i -> "SLOTS_PROPOSED".equals(i.getStatus())).findFirst())
                .or(() -> all.stream().max(Comparator.comparing(
                        Interview::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))))
                .orElse(null);
    }

    /** Of the records sharing a number, the one whose application moved last. */
    private UUID primaryOf(List<Candidate> people) {
        UUID best = null;
        OffsetDateTime latest = null;
        for (Candidate c : people) {
            OffsetDateTime moved = applications.findByCandidateId(c.getId()).stream()
                    .map(Application::getUpdatedAt).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(c.getCreatedAt());
            if (best == null || (moved != null && (latest == null || moved.isAfter(latest)))) {
                best = c.getId();
                latest = moved;
            }
        }
        return best;
    }

    private record Subject(Candidate candidate, Application application, Job job, Interview interview) {

        Subject with(Interview other) {
            return new Subject(candidate, application, job, other);
        }
    }

    private Subject subject(UUID applicationId, Interview interview) {
        Application app = applicationId == null ? null : applications.findById(applicationId).orElse(null);
        if (app == null) {
            return null;
        }
        Candidate candidate = candidates.findById(app.getCandidateId()).orElse(null);
        if (candidate == null) {
            return null;
        }
        return new Subject(candidate, app, jobs.findById(app.getJobId()).orElse(null), interview);
    }

    /** Who an answer is addressed to, when it is not about one interview. */
    private Subject replySubject(Texter t, Application about) {
        Candidate candidate = t.primary() == null ? null : candidates.findById(t.primary()).orElse(null);
        if (about != null) {
            Candidate owner = candidates.findById(about.getCandidateId()).orElse(candidate);
            return new Subject(owner, about, jobs.findById(about.getJobId()).orElse(null), null);
        }
        return new Subject(candidate, null, null, null);
    }

    // =====================================================================
    // Writing a message
    // =====================================================================

    /**
     * Tell a candidate something nobody asked for, on the channels the
     * drawing gives this point. If it reaches them on none, the recruiter is
     * told, because otherwise nobody would know.
     *
     * @param asAnswer true when the text is the answer to the candidate's own text: it is
     *                 then always given, and only the email follows the drawing
     */
    private List<CandidateMessage> tell(
            JourneyPoint point, Subject s, Map<String, String> v, String key, String offer,
            boolean asAnswer, boolean once) {
        Plan plan = plans.current();
        boolean notice = point.kind() == JourneyPoint.Kind.NOTICE;
        List<CandidateMessage> written = new ArrayList<>();
        if (asAnswer || (plan.on() && (notice || plan.draws(point, CandidateMessage.SMS)))) {
            add(written, write(point, CandidateMessage.SMS, s, v, s.candidate.getPhoneE164(), asAnswer,
                    key + ":SMS", offer, once));
        }
        if (plan.on() && point.byEmail() && (notice || plan.draws(point, CandidateMessage.EMAIL))) {
            add(written, write(point, CandidateMessage.EMAIL, s, v, s.candidate.getEmail(), false,
                    key + ":EMAIL", null, once));
        }
        alertWhenUnreached(point, s, written);
        return written;
    }

    /** A candidate told on no channel at all is somebody's to chase: say so where recruiters look. */
    private void alertWhenUnreached(JourneyPoint point, Subject s, List<CandidateMessage> written) {
        if (!written.isEmpty() && written.stream().noneMatch(m -> CandidateMessage.QUEUED.equals(m.getStatus()))) {
            String why = written.stream()
                    .map(m -> (CandidateMessage.SMS.equals(m.getChannel()) ? "Text: " : "Email: ") + m.getReason())
                    .reduce((a, b) -> a + ". " + b).orElse("");
            askRecruiter(s, "was not told: " + point.label().toLowerCase(), why);
        }
    }

    private List<CandidateMessage> answer(JourneyPoint point, Texter t, Subject s, Map<String, String> v) {
        return answer(point, t, s, v, null);
    }

    /** Answer a text. One answer of a kind to one text: a text handled twice is answered once. */
    private List<CandidateMessage> answer(JourneyPoint point, Texter t, Subject s, Map<String, String> v, String offer) {
        Map<String, String> all = values.of(s.candidate, s.application, s.job, s.interview);
        all.putAll(v);
        CandidateMessage m = write(point, CandidateMessage.SMS, s, all, t.address(), true,
                "reply:" + t.inboundId() + ":" + point.name(), offer, false);
        return m == null ? List.of() : List.of(m);
    }

    private List<CandidateMessage> unknownSender(Texter t) {
        long told = messages.countAnswers(t.address(), Set.of(JourneyPoint.UNKNOWN_SENDER.name()),
                OffsetDateTime.now().minusHours(24));
        if (told > 0) {
            return List.of(); // told once today already
        }
        return answer(JourneyPoint.UNKNOWN_SENDER, t, new Subject(null, null, null, null), Map.of());
    }

    /**
     * A text from a number more than one person gave. Whose applications and
     * whose interviews it speaks of cannot be known, so nothing is said of
     * them and nothing is changed; the recruiters are told the number is
     * shared, once a day.
     */
    private List<CandidateMessage> sharedNumber(Texter t) {
        long told = messages.countAnswers(t.address(), Set.of(JourneyPoint.SHARED_NUMBER.name()),
                OffsetDateTime.now().minusHours(24));
        if (told > 0) {
            return List.of();
        }
        List<String> names = candidates.findAllById(t.candidateIds()).stream().map(Candidate::getName).toList();
        notifications.notifyRole("RECRUITER", "MESSAGE",
                clip("A text came from a number " + names.size() + " candidates gave", 200),
                clip(String.join(", ", names) + " gave " + t.address()
                        + ". It was not answered with anything about them. Reach them by email.", 400),
                "/candidates");
        return answer(JourneyPoint.SHARED_NUMBER, t, new Subject(null, null, null, null), Map.of());
    }

    private boolean answeredTooOften(Texter t) {
        return messages.countAnswers(t.address(), ANSWERS_THAT_CAN_LOOP, OffsetDateTime.now().minusMinutes(10))
                >= ANSWERS_BEFORE_SILENCE;
    }

    private CandidateMessage silence(Texter t) {
        return store.notSent(
                draft(JourneyPoint.HELP, CandidateMessage.SMS, replySubject(t, null), t.address(), null, "", null, null, null),
                CandidateMessage.SUPPRESSED,
                "Answered three times in ten minutes already. Not answered again, in case a machine is replying");
    }

    /**
     * Write one message on one channel: decide whether it may go, find its
     * wording, fill it in, and record it.
     *
     * @param once true when a message held back is to be recorded once only: it then carries
     *             the key with {@code :held} added, and is not written again
     * @return the record, or null when a message with this key is on record already
     */
    private CandidateMessage write(
            JourneyPoint point, String channel, Subject s, Map<String, String> v,
            String address, boolean answering, String key, String offer, boolean once) {
        if (store.known(key)) {
            return null;
        }
        // Held back before and recorded then: it is not recorded again, but it may go now.
        String held = once ? key + ":held" : null;
        boolean recorded = store.known(held);
        boolean text = CandidateMessage.SMS.equals(channel);
        OffsetDateTime consent = s.candidate == null ? null : s.candidate.getSmsConsentAt();
        MessagePolicy.Decision may = text
                ? policy.text(address, consent, answering, point.name())
                : policy.email(address);
        if (!may.send()) {
            return recorded ? null : store.notSent(draft(point, channel, s, address, null, "", null, null, held),
                    CandidateMessage.SUPPRESSED, may.reason());
        }
        Plan plan = plans.current();
        List<Wording> wordings = text ? templates.forText(point, plan) : templates.forEmail(point, plan);
        if (wordings.isEmpty()) {
            return recorded ? null : store.notSent(draft(point, channel, s, address, null, "", null, null, held),
                    CandidateMessage.FAILED,
                    "The journey has no active template for “" + point.label() + "”");
        }
        List<String> missing = List.of();
        for (Wording w : wordings) {
            Rendered body = text ? TemplateRenderer.plain(w.body(), v) : TemplateRenderer.html(w.body(), v);
            Rendered subject = text ? null : TemplateRenderer.plain(w.subject(), v);
            if (!body.complete() || (subject != null && !subject.complete())) {
                missing = body.complete() ? subject.missing() : body.missing();
                continue; // this wording asks for something there is no value for: try the journey's own
            }
            String out = body.text();
            if (text) {
                out = SmsText.plain(out);
                if (!messages.hasBeenTexted(address) && !out.toUpperCase().contains("STOP")) {
                    out = out + FIRST_TEXT_FOOTER;
                }
            }
            return store.queue(draft(point, channel, s, address,
                    subject == null ? null : subject.text(), out, w.templateId(), w.name(), key).withOffer(offer));
        }
        return recorded ? null : store.notSent(draft(point, channel, s, address, null, "", wordings.get(0).templateId(),
                        wordings.get(0).name(), held),
                CandidateMessage.FAILED,
                "The template needs " + String.join(", ", missing.stream().map(f -> "{{" + f + "}}").toList())
                        + ", which has no value here");
    }

    private static Draft draft(
            JourneyPoint point, String channel, Subject s, String address,
            String subject, String body, UUID templateId, String templateName, String key) {
        return new Draft(
                s.candidate == null ? null : s.candidate.getId(),
                s.application == null ? null : s.application.getId(),
                s.interview == null ? null : s.interview.getId(),
                channel, address, subject, body, point.name(), templateId, templateName, key, null);
    }

    private static void add(List<CandidateMessage> to, CandidateMessage m) {
        if (m != null) {
            to.add(m);
        }
    }

    /** Put a matter in front of the recruiters; they are the ones who can act on it. */
    private void askRecruiter(Subject s, String what, String why) {
        String name = s.candidate == null ? "A candidate" : s.candidate.getName();
        String title = clip(name + " " + what, 200);
        String link = s.candidate == null ? "/candidates" : "/candidates/" + s.candidate.getId();
        notifications.notifyRole("RECRUITER", "MESSAGE", title, clip(why, 400), link);
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static Pick answered(List<CandidateMessage> written) {
        return new Pick(PickNext.ANSWERED, null, null, null, written);
    }

    /** For tests and the reminder job: the interviews a reminder may be due for. */
    Optional<Interview> interview(UUID id) {
        return interviews.findById(id);
    }
}
