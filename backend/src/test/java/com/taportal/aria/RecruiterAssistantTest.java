package com.taportal.aria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.services.blocking.MessageService;
import com.taportal.aria.RecruiterAssistant.Reply;
import com.taportal.aria.RecruiterBrain.Answer;
import com.taportal.aria.RecruiterBrain.Turn;
import com.taportal.domain.briefing.Briefing;
import com.taportal.domain.briefing.Briefing.Link;
import com.taportal.domain.briefing.Briefing.Section;
import com.taportal.domain.briefing.Briefing.Why;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The recruiter assistant: names kept from the brain, both brains' answers, and the fallback. */
class RecruiterAssistantTest {

    final Briefing briefing = new Briefing(Briefing.CANDIDATE, "[C1], applying for Teller",
            List.of(new Section("Application for Teller", List.of("Stage: Interview.", "Phone screen: booked; panel [S1]."))),
            List.of(new Why("No time is set for the phone screen for Teller",
                    "times were offered 2 days ago and [C1] has not picked one", "Open the scheduling drawer.")),
            List.of("No interview feedback is recorded for these interviews."),
            Map.of("[C1]", "Maya Thompson", "[S1]", "Priya Raman"),
            List.of(new Link("Open [C1]'s record", "/candidates/1")));

    @Nested
    class Names {

        /** A brain that records what it was given and answers with a placeholder. */
        final List<Object> seen = new ArrayList<>();
        final RecruiterBrain recording = (intent, question, b, history) -> {
            seen.add(question);
            seen.addAll(history.stream().map(Turn::text).toList());
            return new Answer("[C1] has not picked a time; [S1] is on the panel.", true);
        };

        @Test
        void theBrainNeverSeesANameAndTheRecruiterAlwaysDoes() {
            Reply r = new RecruiterAssistant(recording).ask(RecruiterBrain.ASK, "Why is Maya Thompson waiting?", briefing,
                    List.of(new Turn(true, "Brief me on Maya"), new Turn(false, "Maya Thompson is at Interview with Priya Raman.")));

            assertThat(seen).containsExactly("Why is [C1] waiting?", "Brief me on [C1]", "[C1] is at Interview with [S1].");
            assertThat(r.answer()).isEqualTo("Maya Thompson has not picked a time; Priya Raman is on the panel.");
            assertThat(r.about()).isEqualTo("Maya Thompson, applying for Teller");
            assertThat(r.links()).containsExactly(new RecruiterAssistant.Link("Open Maya Thompson's record", "/candidates/1"));
            assertThat(r.byModel()).isTrue();
        }

        @Test
        void onlyTheLatestTurnsAreSentOn() {
            List<Turn> long_ = IntStream.range(0, 20).mapToObj(i -> new Turn(i % 2 == 0, "turn " + i)).toList();

            new RecruiterAssistant(recording).ask(RecruiterBrain.BRIEF, null, briefing, long_);

            assertThat(seen.subList(1, seen.size())).hasSize(RecruiterAssistant.MAX_HISTORY).last().isEqualTo("turn 19");
        }
    }

    @Nested
    class Scripted {

        final ScriptedRecruiterBrain brain = new ScriptedRecruiterBrain();

        @Test
        void briefLeadsWithWhatNeedsALookThenTheRecords() {
            String a = brain.answer(RecruiterBrain.BRIEF, null, briefing, List.of()).text();
            assertThat(a).startsWith("Here is where things stand for [C1], applying for Teller. One item needs a look")
                    .contains("Application for Teller\n- Stage: Interview.");
        }

        @Test
        void whyGivesEachReasonAndItsNextStep() {
            assertThat(brain.answer(RecruiterBrain.WHY, null, briefing, List.of()).text()).isEqualTo(
                    "For [C1], applying for Teller:\n- No time is set for the phone screen for Teller, because times were "
                            + "offered 2 days ago and [C1] has not picked one. Open the scheduling drawer.");
        }

        @Test
        void whyWithNothingWaitingSaysSo() {
            Briefing calm = new Briefing(Briefing.DAY, "Interviews across every job", List.of(), List.of(), List.of(), Map.of(), List.of());
            assertThat(brain.answer(RecruiterBrain.WHY, null, calm, List.of()).text())
                    .isEqualTo("Nothing for Interviews across every job is waiting, held back or not sent.");
        }

        @Test
        void aTypedQuestionIsRoutedOnAFewWordsOrAnsweredHonestly() {
            assertThat(brain.answer(RecruiterBrain.ASK, "why is [C1] stuck?", briefing, List.of()).text()).startsWith("For [C1]");
            assertThat(brain.answer(RecruiterBrain.ASK, "give me a summary", briefing, List.of()).text()).startsWith("Here is where");
            Answer other = brain.answer(RecruiterBrain.ASK, "Can you send [C1] a reminder?", briefing, List.of());
            assertThat(other.text()).startsWith("Without the model I can only lay out the records");
            assertThat(other.byModel()).isFalse();
        }
    }

    @Nested
    class Model {

        final AnthropicClient client = mock(AnthropicClient.class);
        final MessageService messages = mock(MessageService.class);
        final ScriptedRecruiterBrain scripted = new ScriptedRecruiterBrain();
        final ClaudeRecruiterBrain brain = new ClaudeRecruiterBrain(client, "claude-opus-5-5", scripted);

        Model() {
            when(client.messages()).thenReturn(messages);
        }

        @Test
        void aFailedCallFallsBackToTheRulesWording() {
            when(messages.create(any(MessageCreateParams.class))).thenThrow(new AnthropicIoException("connection reset"));

            Answer a = brain.answer(RecruiterBrain.WHY, null, briefing, List.of());

            assertThat(a.byModel()).isFalse();
            assertThat(a.text()).isEqualTo(scripted.answer(RecruiterBrain.WHY, null, briefing, List.of()).text());
        }

        @Test
        void aRefusalFallsBackToTheRulesWording() {
            Message refused = mock(Message.class);
            when(refused.stopReason()).thenReturn(Optional.of(StopReason.REFUSAL));
            when(messages.create(any(MessageCreateParams.class))).thenReturn(refused);

            assertThat(brain.answer(RecruiterBrain.BRIEF, null, briefing, List.of()).byModel()).isFalse();
        }

        @Test
        void theRequestCarriesTheRecordsTheAskAndTheHistoryInTurn() {
            MessageCreateParams p = brain.params(RecruiterBrain.ASK, "Is [C1] booked?", briefing,
                    List.of(new Turn(false, "Hello, ask me about this page."), new Turn(true, "Brief me"),
                            new Turn(false, "[C1] is at Interview."), new Turn(true, "unanswered")));

            assertThat(p.outputConfig().flatMap(OutputConfig::effort)).contains(OutputConfig.Effort.LOW);
            assertThat(p.system().flatMap(MessageCreateParams.System::string)).contains(ClaudeRecruiterBrain.SYSTEM);
            List<String> sent = p.messages().stream().map(m -> m.content().asString()).toList();
            // The greeting before the recruiter spoke and the unanswered last turn are dropped.
            assertThat(sent).hasSize(3);
            assertThat(sent.get(0)).isEqualTo("Brief me");
            assertThat(sent.get(1)).isEqualTo("[C1] is at Interview.");
            assertThat(sent.get(2)).startsWith("Records:\nAbout: [C1], applying for Teller")
                    .contains("Why things are as they are:\n- No time is set")
                    .endsWith("Recruiter: Is [C1] booked?")
                    .doesNotContain("Maya", "Priya");
        }

        @Test
        void historyIsMadeToAlternateStartingWithTheRecruiter() {
            assertThat(ClaudeRecruiterBrain.alternating(List.of(
                    new Turn(false, "greeting"), new Turn(true, "a"), new Turn(true, "b"), new Turn(false, "c"))))
                    .containsExactly(new Turn(true, "b"), new Turn(false, "c"));
            assertThat(ClaudeRecruiterBrain.alternating(null)).isEmpty();
        }

        @Test
        void aMissingKeyOrModelIsNeverAHardFailure() {
            when(messages.create(any(MessageCreateParams.class))).thenThrow(new IllegalStateException("no credentials"));
            assertThat(brain.answer(RecruiterBrain.BRIEF, null, briefing, List.of()).text()).startsWith("Here is where");
        }
    }

    @Test
    void aBriefingWithoutPeopleStillAnswers() {
        Briefing empty = new Briefing(Briefing.DAY, "Interviews across every job", List.of(), List.of(), List.of(), Map.of(), List.of());
        assertThat(new RecruiterAssistant(new ScriptedRecruiterBrain()).ask(RecruiterBrain.BRIEF, null, empty, null).answer())
                .isEqualTo("Here is where things stand for Interviews across every job.");
    }
}
