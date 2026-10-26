package com.taportal.aria;

import com.taportal.aria.RecruiterBrain.Answer;
import com.taportal.aria.RecruiterBrain.Turn;
import com.taportal.domain.briefing.Briefing;
import com.taportal.domain.briefing.PeopleNames;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The recruiter assistant: answers a recruiter about what is on their screen, from a
 * {@link Briefing}, in the words of the {@link RecruiterBrain} that {@link AriaConfig} chose.
 *
 * <p>This is where names cross the privacy line, both ways. Before the brain sees anything, every
 * name the briefing knows is replaced by its placeholder in the recruiter's question and in the
 * earlier turns. After the brain answers, the placeholders are replaced by the names. So the
 * wording a model writes never needed a name to write it.
 *
 * <p>Read-only: it builds no records and keeps no thread. The chat lives in the recruiter's browser,
 * which sends the earlier turns with each question.
 */
@Service
public class RecruiterAssistant {

    /** Earlier turns sent on to the brain; older ones are dropped. */
    static final int MAX_HISTORY = 8;

    private final RecruiterBrain brain;

    public RecruiterAssistant(RecruiterBrain brain) {
        this.brain = brain;
    }

    /**
     * @param intent   BRIEF, WHY or ASK (see {@link RecruiterBrain})
     * @param question the recruiter's words, for ASK
     * @param briefing what the records say about the screen
     * @param history  earlier turns, oldest first, as the recruiter saw them (with names)
     */
    public Reply ask(String intent, String question, Briefing briefing, List<Turn> history) {
        Map<String, String> people = briefing.people();
        List<Turn> recent = history == null ? List.of()
                : history.subList(Math.max(0, history.size() - MAX_HISTORY), history.size());
        List<Turn> hidden = recent.stream()
                .map(t -> new Turn(t.fromRecruiter(), PeopleNames.hide(people, t.text())))
                .toList();
        Answer a = brain.answer(intent, PeopleNames.hide(people, question), briefing, hidden);
        return new Reply(
                PeopleNames.show(people, briefing.about()),
                PeopleNames.show(people, a.text()),
                briefing.links().stream().map(l -> new Link(PeopleNames.show(people, l.label()), l.path())).toList(),
                a.byModel());
    }

    /**
     * What the recruiter is shown.
     *
     * @param about   what the answer is about, with names
     * @param answer  the answer, with names
     * @param links   screens to open
     * @param byModel true when a model worded it; false when the rules did
     */
    public record Reply(String about, String answer, List<Link> links, boolean byModel) {
    }

    /** A screen to open: its label and its path in the app. */
    public record Link(String label, String path) {
    }
}
