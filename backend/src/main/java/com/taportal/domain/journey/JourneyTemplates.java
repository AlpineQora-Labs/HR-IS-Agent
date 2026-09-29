package com.taportal.domain.journey;

import com.taportal.domain.comms.EmailTemplate;
import com.taportal.domain.comms.EmailTemplateRepository;
import com.taportal.domain.comms.SmsTemplate;
import com.taportal.domain.comms.SmsTemplateRepository;
import com.taportal.domain.journey.JourneyPlan.Plan;
import com.taportal.domain.messaging.CandidateMessage;
import com.taportal.domain.messaging.TemplateRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Finds the wording for a journey point: the template the drawing's step
 * names when it is fit to send, otherwise the journey's own template for the
 * point. A template is fit when it is Active and carries the fields the point
 * needs — an invitation that lists no times is not sent in an invitation's
 * place.
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
                .filter(t -> fit(t.getStatus(), t.getBody(), point))
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
                .filter(t -> "ACTIVE".equals(t.getStatus()))
                .ifPresent(t -> found.add(wording(t)));
        emailTemplates.findByTemplateKey(point.templateKey())
                .filter(t -> "ACTIVE".equals(t.getStatus()))
                .filter(t -> found.stream().noneMatch(w -> w.templateId().equals(t.getId())))
                .ifPresent(t -> found.add(wording(t)));
        return found;
    }

    private static boolean fit(String status, String body, JourneyPoint point) {
        return "ACTIVE".equals(status) && TemplateRenderer.fieldsIn(body).containsAll(point.needs());
    }

    private static Wording wording(SmsTemplate t) {
        return new Wording(t.getId(), t.getName(), null, t.getBody(), null);
    }

    private static Wording wording(EmailTemplate t) {
        return new Wording(t.getId(), t.getName(), t.getSubject(), t.getBodyHtml(), t.getFromName());
    }
}
