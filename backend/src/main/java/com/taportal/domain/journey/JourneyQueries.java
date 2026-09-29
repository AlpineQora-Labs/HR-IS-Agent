package com.taportal.domain.journey;

import com.taportal.api.MessageDtos.FieldDto;
import com.taportal.api.MessageDtos.JourneyInfo;
import com.taportal.api.MessageDtos.MessageRow;
import com.taportal.api.MessageDtos.PointDto;
import com.taportal.api.MessageDtos.ReminderResult;
import com.taportal.api.MessageDtos.RuleDto;
import com.taportal.api.MessageDtos.Timeline;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.interview.Interview;
import com.taportal.domain.interview.InterviewRepository;
import com.taportal.domain.interview.InterviewService;
import com.taportal.domain.messaging.CandidateMessage;
import com.taportal.domain.messaging.CandidateMessageRepository;
import com.taportal.domain.messaging.MessagePolicy;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** What the screens read of the journey. Read-only, except for sending a reminder now. */
@Service
public class JourneyQueries {

    /** The rules a journey is drawn with: events in a candidate's journey, not facts about a request. */
    public static final List<RuleDto> RULES = List.of(
            new RuleDto("Candidate asks for status", "the candidate asks for their status"),
            new RuleDto("Candidate asks to reschedule", "the candidate asks to reschedule"));

    private final CandidateRepository candidates;
    private final CandidateMessageRepository messages;
    private final InterviewRepository interviews;
    private final MessagePolicy policy;
    private final JourneyPlan plans;
    private final JourneyService journey;
    private final JourneyTemplates templates;

    public JourneyQueries(
            CandidateRepository candidates, CandidateMessageRepository messages, InterviewRepository interviews,
            MessagePolicy policy, JourneyPlan plans, JourneyService journey, JourneyTemplates templates) {
        this.candidates = candidates;
        this.messages = messages;
        this.interviews = interviews;
        this.policy = policy;
        this.plans = plans;
        this.journey = journey;
        this.templates = templates;
    }

    /**
     * A person's messages. Someone who applied twice has two candidate
     * records and one phone: by text they are one person, so the timeline of
     * either record shows the texts of both.
     */
    @Transactional(readOnly = true)
    public Timeline timeline(UUID candidateId) {
        Candidate c = candidate(candidateId);
        String phone = c.getPhoneE164();
        // Only records of the same person: what was sent to somebody else who gave the number is theirs.
        List<Candidate> withTheNumber = phone == null ? List.of(c) : candidates.findByPhoneE164(phone);
        List<UUID> ids = withTheNumber.stream()
                .filter(other -> SamePerson.same(c, other)).map(Candidate::getId).toList();
        boolean shared = ids.size() < withTheNumber.size();
        MessagePolicy.Decision may = policy.text(phone, c.getSmsConsentAt(), false, "NONE");
        return new Timeline(
                c.getId(), c.getName(), phone, c.getEmail(), may.send(), may.reason(), plans.current().on(),
                shared ? "Another candidate gave this mobile number too. A text from it cannot be taken as "
                        + "coming from either, so it is not acted on, and invitations go with a link "
                        + "instead of numbered times. Reach this candidate by email." : null,
                (shared
                        ? messages.timelineOnSharedNumber(ids, phone)
                        : messages.timeline(ids.isEmpty() ? List.of(c.getId()) : ids, phone == null ? "" : phone))
                        .stream().map(MessageRow::of).toList());
    }

    /** The number a candidate's phone is played from. Refuses when there is none a text can come from. */
    @Transactional(readOnly = true)
    public String phoneOf(UUID candidateId) {
        Candidate c = candidate(candidateId);
        if (c.getPhoneE164() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    c.getName() + " has no mobile number a text can come from.");
        }
        return c.getPhoneE164();
    }

    /** What was written in answer to a text: everything for its sender from the moment it was recorded. */
    @Transactional(readOnly = true)
    public List<MessageRow> answersTo(UUID inboundId) {
        CandidateMessage heard = messages.findById(inboundId).orElse(null);
        if (heard == null) {
            return List.of();
        }
        List<UUID> ids = candidates.findByPhoneE164(heard.getAddress()).stream().map(Candidate::getId).toList();
        OffsetDateTime since = heard.getCreatedAt() == null ? OffsetDateTime.now() : heard.getCreatedAt();
        return messages.writtenSince(heard.getAddress(), ids.isEmpty() ? List.of(new UUID(0, 0)) : ids, since)
                .stream().map(MessageRow::of).toList();
    }

    @Transactional(readOnly = true)
    public JourneyInfo info() {
        return new JourneyInfo(
                plans.current().on(),
                Arrays.stream(JourneyPoint.values())
                        .map(p -> new PointDto(p.name(), p.label(), p.kind().name(), p.byText(), p.byEmail(),
                                p.drawn(), p.needs().stream().sorted().toList(), p.templateKey(),
                                p.has().stream().toList(),
                                // Which template may word a point is the journey's to say; a screen only lists them.
                                p.drawn() ? templates.textsFor(p) : List.of(),
                                p.drawn() ? templates.emailsFor(p) : List.of()))
                        .toList(),
                JourneyValues.FIELDS.stream().map(f -> new FieldDto(f.name(), f.holds())).toList(),
                RULES);
    }

    public ReminderResult remindNow(UUID interviewId, String which) {
        JourneyPoint point = switch (which == null ? "" : which.toLowerCase()) {
            case "24h" -> JourneyPoint.INTERVIEW_REMINDER_24H;
            case "1h" -> JourneyPoint.INTERVIEW_REMINDER_1H;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose 24h or 1h.");
        };
        Interview interview = interviews.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        if (!InterviewService.isBooked(interview)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This interview is not booked for a time still to come, so there is nothing to remind of.");
        }
        List<CandidateMessage> written = journey.remind(interviewId, point);
        String note = null;
        if (written.isEmpty()) {
            JourneyPlan.Plan plan = plans.current();
            note = !plan.on() ? "The candidate journey is switched off, so nothing is sent."
                    : !plan.draws(point, CandidateMessage.SMS) && !plan.draws(point, CandidateMessage.EMAIL)
                            ? "The journey has no step for this reminder."
                            : whatBecameOf(journey.reminded(interviewId, point));
        }
        return new ReminderResult(written.stream().map(MessageRow::of).toList(), note);
    }

    /** Why pressing again wrote nothing, read from the record and not assumed. */
    private static String whatBecameOf(List<CandidateMessage> onRecord) {
        if (onRecord.stream().anyMatch(m -> List.of(
                CandidateMessage.QUEUED, CandidateMessage.SENDING, CandidateMessage.SENT).contains(m.getStatus()))) {
            return "This reminder was already sent for this booking.";
        }
        String held = onRecord.stream().map(CandidateMessage::getReason).filter(r -> r != null && !r.isBlank())
                .distinct().reduce((a, b) -> a + ". " + b).orElse(null);
        if (held != null) {
            return "This reminder was not sent. " + held + ".";
        }
        return "The reminder could not be prepared, and nothing was sent. Administrators have been told.";
    }

    private Candidate candidate(UUID id) {
        return candidates.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
    }
}
