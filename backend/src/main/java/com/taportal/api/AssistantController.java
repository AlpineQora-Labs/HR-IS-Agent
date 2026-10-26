package com.taportal.api;

import com.taportal.aria.RecruiterAssistant;
import com.taportal.aria.RecruiterAssistant.Reply;
import com.taportal.aria.RecruiterBrain;
import com.taportal.aria.RecruiterBrain.Turn;
import com.taportal.domain.briefing.Briefing;
import com.taportal.domain.briefing.BriefingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The recruiter assistant: the chat widget on every recruiter screen. Read-only.
 *
 * <p>The controller only joins the two halves: {@link BriefingService} decides what the records say
 * about the screen (the rules), and {@link RecruiterAssistant} puts it into words (the voice). It
 * keeps no thread; the browser sends the earlier turns with each question.
 *
 * <p>Errors: 400 when the request is malformed, 404 when an id in the context is not on file.
 */
@RestController
public class AssistantController {

    private final BriefingService briefings;
    private final RecruiterAssistant assistant;

    public AssistantController(BriefingService briefings, RecruiterAssistant assistant) {
        this.briefings = briefings;
        this.assistant = assistant;
    }

    /**
     * Answer about what is on the recruiter's screen.
     *
     * <p>The briefing is for the most specific thing the context names: an application, then a
     * candidate, then a job; with none, the day's interviews across every job.
     */
    @PostMapping("/v1/assistant/ask")
    public AskReply ask(@Valid @RequestBody AskRequest req) {
        String intent = req.intent();
        if (RecruiterBrain.ASK.equals(intent) && (req.question() == null || req.question().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type a question, or choose Brief me or Explain why.");
        }
        Context c = req.context() == null ? new Context(null, null, null) : req.context();
        Briefing briefing = briefings.forContext(c.candidateId(), c.applicationId(), c.jobId());
        List<Turn> history = req.history() == null ? List.of()
                : req.history().stream().map(t -> new Turn("RECRUITER".equals(t.from()), t.text())).toList();
        Reply r = assistant.ask(intent, RecruiterBrain.ASK.equals(intent) ? req.question().trim() : null, briefing, history);
        return new AskReply(briefing.scope(), r.about(), r.answer(),
                r.links().stream().map(l -> new LinkDto(l.label(), l.path())).toList(), r.byModel());
    }

    /**
     * A question for the assistant.
     *
     * @param intent   BRIEF ("Brief me"), WHY ("Explain why") or ASK (a typed question)
     * @param question the recruiter's words; required for ASK, ignored otherwise
     * @param context  what is on the screen; empty means the day's interviews
     * @param history  the chat so far, oldest first, as the recruiter saw it
     */
    public record AskRequest(
            @NotNull @Pattern(regexp = "BRIEF|WHY|ASK", message = "must be BRIEF, WHY or ASK") String intent,
            @Size(max = 500, message = "must be 500 characters or fewer") String question,
            Context context,
            @Size(max = 20, message = "must hold 20 turns or fewer") List<@Valid TurnDto> history) {
    }

    /** The ids of what is on screen. Any may be null. */
    public record Context(UUID candidateId, UUID applicationId, UUID jobId) {
    }

    /**
     * One earlier turn.
     *
     * @param from RECRUITER or ASSISTANT
     */
    public record TurnDto(
            @NotNull @Pattern(regexp = "RECRUITER|ASSISTANT", message = "must be RECRUITER or ASSISTANT") String from,
            @NotBlank @Size(max = 4000, message = "must be 4000 characters or fewer") String text) {
    }

    /**
     * The answer.
     *
     * @param scope   DAY, CANDIDATE or JOB: what the answer is about
     * @param about   that thing in words, for example "Maya Thompson, applying for Teller (R-1042)"
     * @param answer  plain text; lines starting "- " are list items
     * @param links   screens of the portal the answer refers to
     * @param byModel true when a model worded the answer, false when the portal's rules did
     */
    public record AskReply(String scope, String about, String answer, List<LinkDto> links, boolean byModel) {
    }

    /** A screen of the portal: a label, and its path in the app (for example "/candidates/{id}"). */
    public record LinkDto(String label, String path) {
    }
}
