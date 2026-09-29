package com.taportal.aria;

import com.taportal.domain.interview.Interview;
import com.taportal.domain.interview.InterviewSlot;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.KnockoutQuestion;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, warm "Aria" copy. No external API calls — the default
 * {@link AssistantBrain} and the fallback for {@link ClaudeBrain}. Wired by
 * {@link AriaConfig}. Steps mirror {@link ConversationEngine}.
 */
public class ScriptedBrain implements AssistantBrain {

    /** All candidate-facing times are rendered in ET, whatever offset the DB returns. */
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter SLOT_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.ENGLISH);

    @Override
    public String greeting(Job job, String language) {
        String title = job != null ? job.getTitle() : "this role";
        return "Hello, I'm Aria, your recruiting assistant. "
                + "Thank you for your interest in the " + title + " role. "
                + "I'll ask a few questions to start your application. It takes about a minute. "
                + "To begin, what's your full name?";
    }

    @Override
    public String ask(String step, Job job, KnockoutQuestion question) {
        switch (step) {
            case ConversationEngine.STEP_COLLECT_NAME:
                return "What's your full name?";
            case ConversationEngine.STEP_COLLECT_EMAIL:
                return "Thank you. What's the best email to reach you at?";
            case ConversationEngine.STEP_COLLECT_PHONE:
                return "What mobile number can we text updates to? "
                        + "Message and data rates may apply, and you can reply STOP at any time to opt out.";
            case ConversationEngine.STEP_KNOCKOUT:
                return question != null ? question.getPrompt() : "Just a couple of quick questions.";
            default:
                return "Could you tell me a little more?";
        }
    }

    @Override
    public String acknowledge(String step, String userText) {
        switch (step) {
            case ConversationEngine.STEP_COLLECT_NAME:
                return "Good to meet you, " + firstName(userText) + ".";
            case ConversationEngine.STEP_COLLECT_EMAIL:
                return "Thank you.";
            case ConversationEngine.STEP_COLLECT_PHONE:
                return "Thank you. A few screening questions come next.";
            case ConversationEngine.STEP_KNOCKOUT:
                return "Thanks for that.";
            default:
                return "Thank you.";
        }
    }

    @Override
    public String decline(Job job) {
        return "Thank you so much for taking the time to apply. Based on your responses, "
                + "this particular role isn't the right match right now. "
                + "I'd love to keep you in mind for future openings that fit you better — "
                + "we wish you all the best in your search.";
    }

    @Override
    public String schedulePrompt(List<InterviewSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return "Good news: you're through to the next step. "
                    + "Our team will reach out shortly to set up a conversation.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Good news: you're through to the next step. ")
                .append("Let's put a phone screen on the calendar. ")
                .append("These times work for the hiring team. Reply with the one you would like:\n");
        int i = 1;
        for (InterviewSlot slot : slots) {
            sb.append("\n").append(i++).append(". ").append(formatSlot(slot));
        }
        return sb.toString();
    }

    @Override
    public String confirmation(Job job, Interview interview) {
        String when = interview != null && interview.getScheduledAt() != null
                ? interview.getScheduledAt().atZoneSameInstant(ZONE).format(SLOT_FMT) + " ET"
                : "your selected time";
        return "You're all set. Your phone screen is booked for " + when + ". "
                + "A confirmation with the details is on its way. "
                + "Thank you for applying. We look forward to speaking with you.";
    }

    /** Shared so the engine can render slot quick-reply options identically. */
    static String formatSlot(InterviewSlot slot) {
        return slot.getStartsAt().atZoneSameInstant(ZONE).format(SLOT_FMT) + " ET";
    }

    private static String firstName(String text) {
        if (text == null || text.isBlank()) {
            return "there";
        }
        String trimmed = text.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }
}
