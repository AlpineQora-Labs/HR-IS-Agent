package com.taportal.domain.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.taportal.domain.comms.EmailTemplate;
import com.taportal.domain.comms.EmailTemplateRepository;
import com.taportal.domain.comms.SmsTemplate;
import com.taportal.domain.comms.SmsTemplateRepository;
import com.taportal.domain.journey.JourneyPlan.Advice;
import com.taportal.domain.journey.JourneyPlan.Chosen;
import com.taportal.domain.journey.JourneyPlan.Plan;
import com.taportal.domain.messaging.CandidateMessage;
import com.taportal.domain.messaging.TemplateRenderer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Finds the wording for a journey point: the template the drawing's step
 * names when it is fit to send, otherwise the journey's own template for the
 * point.
 *
 * <p>Whether a template is fit for a point is decided here and nowhere else:
 * it is Active, it is not the wording of some other point, it carries the
 * fields the point needs (an invitation that lists no times is not sent in an
 * invitation's place), and it uses no field the point has no value for. The
 * screens ask; they do not work it out.
 */
@Service
public class JourneyTemplates {

    private final SmsTemplateRepository smsTemplates;
    private final EmailTemplateRepository emailTemplates;

    public JourneyTemplates(SmsTemplateRepository smsTemplates, EmailTemplateRepository emailTemplates) {
        this.smsTemplates = smsTemplates;
        this.emailTemplates = emailTemplates;
    }

    /** @param subject set for an email, null for a text */
    public record Wording(UUID templateId, String name, String subject, String body, String fromName) {
    }

    /** The wordings to try, best first. Empty when the point has no wording at all. */
    public List<Wording> forText(JourneyPoint point, Plan plan) {
        List<Wording> found = new ArrayList<>();
        plan.template(point, CandidateMessage.SMS)
                .flatMap(smsTemplates::findById)
                .filter(t -> unfit(point, t) == null)
                .ifPresent(t -> found.add(wording(t)));
        smsTemplates.findByTemplateKey(point.templateKey())
                .filter(t -> "ACTIVE".equals(t.getStatus()))
                .filter(t -> found.stream().noneMatch(w -> w.templateId().equals(t.getId())))
                .ifPresent(t -> found.add(wording(t)));
        return found;
    }

    public List<Wording> forEmail(JourneyPoint point, Plan plan) {
        List<Wording> found = new ArrayList<>();
        plan.template(point, CandidateMessage.EMAIL)
                .flatMap(emailTemplates::findById)
                .filter(t -> unfit(point, t) == null)
                .ifPresent(t -> found.add(wording(t)));
        emailTemplates.findByTemplateKey(point.templateKey())
                .filter(t -> "ACTIVE".equals(t.getStatus()))
                .filter(t -> found.stream().noneMatch(w -> w.templateId().equals(t.getId())))
                .ifPresent(t -> found.add(wording(t)));
        return found;
    }

    /** The text templates a step for this point can be given. */
    public List<UUID> textsFor(JourneyPoint point) {
        return smsTemplates.findAll().stream().filter(t -> unfit(point, t) == null).map(SmsTemplate::getId).toList();
    }

    /** The email templates a step for this point can be given. */
    public List<UUID> emailsFor(JourneyPoint point) {
        if (!point.byEmail()) {
            return List.of();
        }
        return emailTemplates.findAll().stream().filter(t -> unfit(point, t) == null).map(EmailTemplate::getId)
                .toList();
    }

    /**
     * What about a drawing's chosen templates will not do what its author
     * expects: a step whose template cannot word its point sends the journey's
     * own wording instead, and the author should know.
     */
    public List<Advice> advise(JsonNode graph) {
        List<Advice> advice = new ArrayList<>();
        for (Chosen step : JourneyPlan.chosen(graph)) {
            boolean text = CandidateMessage.SMS.equals(step.channel());
            String name;
            String why;
            if (text) {
                SmsTemplate t = smsTemplates.findById(step.templateId()).orElse(null);
                name = t == null ? null : t.getName();
                why = t == null ? "no longer exists" : unfit(step.point(), t);
            } else {
                EmailTemplate t = emailTemplates.findById(step.templateId()).orElse(null);
                name = t == null ? null : t.getName();
                why = t == null ? "no longer exists" : unfit(step.point(), t);
            }
            if (why != null) {
                advice.add(new Advice("STEP_TEMPLATE_UNFIT", step.nodeId(),
                        "The template chosen for “" + step.point().label() + "”"
                                + (name == null ? "" : ", “" + name + "”,") + " " + why
                                + ". The journey’s own wording is sent instead."));
            }
        }
        return advice;
    }

    /** Why this template cannot word this point, in words that follow its name; null when it can. */
    public static String unfit(JourneyPoint point, SmsTemplate t) {
        return unfit(point, t.getStatus(), t.getTemplateKey(), t.getBody());
    }

    public static String unfit(JourneyPoint point, EmailTemplate t) {
        return unfit(point, t.getStatus(), t.getTemplateKey(),
                (t.getSubject() == null ? "" : t.getSubject()) + " " + (t.getBodyHtml() == null ? "" : t.getBodyHtml()));
    }

    /** Pure. */
    static String unfit(JourneyPoint point, String status, String templateKey, String wording) {
        if (!"ACTIVE".equals(status)) {
            return "is not Active";
        }
        if (templateKey != null && !templateKey.equals(point.templateKey())) {
            return "is the wording of another point of the journey";
        }
        Set<String> used = TemplateRenderer.fieldsIn(wording);
        List<String> lacks = point.needs().stream().filter(f -> !used.contains(f)).sorted().toList();
        if (!lacks.isEmpty()) {
            return "lacks " + fields(lacks);
        }
        List<String> unknown = unfillable(point, used);
        if (!unknown.isEmpty()) {
            return "uses " + fields(unknown) + ", which " + (unknown.size() == 1 ? "has" : "have")
                    + " no value at this point";
        }
        return null;
    }

    /** The fields among these that a message at this point has no value for. */
    public static List<String> unfillable(JourneyPoint point, Set<String> used) {
        Set<String> has = point.has();
        return new LinkedHashSet<>(used).stream().filter(f -> !has.contains(f)).toList();
    }

    static String fields(List<String> names) {
        return String.join(" and ", names.stream().map(f -> "{{" + f + "}}").toList());
    }

    private static Wording wording(SmsTemplate t) {
        return new Wording(t.getId(), t.getName(), null, t.getBody(), null);
    }

    private static Wording wording(EmailTemplate t) {
        return new Wording(t.getId(), t.getName(), t.getSubject(), t.getBodyHtml(), t.getFromName());
    }
}
