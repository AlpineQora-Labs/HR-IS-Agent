package com.taportal.domain.comms;

import com.taportal.domain.journey.JourneyPoint;
import com.taportal.domain.journey.JourneyTemplates;
import com.taportal.domain.messaging.TemplateRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * What may be done to a template. A template that words a point of the
 * candidate journey (it has a {@code template_key}) is part of how the system
 * works: it can be reworded, but it cannot be deleted, taken out of use, lose
 * the fields its point needs, or use a field its point has no value for — so
 * a journey point always has something to say, and can always say it.
 */
@Service
public class TemplateRules {

    /** Refuses with 422 and the reason. */
    public void mayDelete(String templateKey, String name) {
        if (templateKey != null) {
            throw refuse("“" + name + "” words a step of the candidate journey, so it can't be deleted. "
                    + "Change its wording instead.");
        }
    }

    /**
     * @param newSubject the email's subject; null for a text message
     * @param text       true for a text message, false for an email
     */
    public void mayChange(
            String templateKey, String name, String newStatus, String newSubject, String newBody, boolean text) {
        if (templateKey == null) {
            return;
        }
        if (!"ACTIVE".equals(newStatus)) {
            throw refuse("“" + name + "” words a step of the candidate journey, so it has to stay Active.");
        }
        JourneyPoint point = JourneyPoint.ofTemplateKey(templateKey).orElse(null);
        if (point == null) {
            return;
        }
        Set<String> uses = new java.util.LinkedHashSet<>(TemplateRenderer.fieldsIn(newSubject));
        uses.addAll(TemplateRenderer.fieldsIn(newBody));
        List<String> unknown = JourneyTemplates.unfillable(point, uses);
        if (!unknown.isEmpty()) {
            throw refuse("This message is sent " + lowerFirst(point.label()) + ". "
                    + String.join(" and ", unknown.stream().map(f -> "{{" + f + "}}").toList())
                    + (unknown.size() == 1 ? " has" : " have") + " no value then, so the message could never be sent. "
                    + "It can use " + String.join(", ", point.has().stream().map(f -> "{{" + f + "}}").toList()) + ".");
        }
        if (!text) {
            return;
        }
        Set<String> has = TemplateRenderer.fieldsIn(newBody);
        List<String> lost = new ArrayList<>();
        for (String field : point.needs()) {
            if (!has.contains(field)) {
                lost.add("{{" + field + "}}");
            }
        }
        if (!lost.isEmpty()) {
            throw refuse("This message is sent " + lowerFirst(point.label()) + ", so it has to keep "
                    + String.join(" and ", lost) + ".");
        }
    }

    /**
     * May this template be offered as a choice for a step of the workflow drawing? Only for a step that
     * can be drawn, on a channel that step uses, and only when its wording carries what the step needs
     * and nothing the step has no value for. A point's own wording already belongs to its point.
     *
     * @param journeyPoint the point's name as the editor sends it; null or blank for no step in particular
     * @param subject      the email's subject; null for a text message
     * @return the point's name to keep, or null for none. Refuses with 422 and the reason.
     */
    public String mayOffer(String journeyPoint, String templateKey, String subject, String body, boolean text) {
        if (journeyPoint == null || journeyPoint.isBlank()) {
            return null;
        }
        if (templateKey != null) {
            throw refuse("This template is the journey's own wording for one step, so it cannot be offered for another.");
        }
        JourneyPoint point = JourneyPoint.of(journeyPoint).filter(JourneyPoint::drawn)
                .orElseThrow(() -> refuse(JourneyPoint.of(journeyPoint)
                        .map(p -> "The step sent " + lowerFirst(p.label()) + " is not one the workflow draws, so nothing can be offered for it.")
                        .orElse("That step is not on the workflow.")));
        if (!text && !point.byEmail()) {
            throw refuse("No email is sent " + lowerFirst(point.label()) + ", so an email cannot be offered for it.");
        }
        String why = JourneyTemplates.unfit(point, "ACTIVE", null, point.name(),
                (subject == null ? "" : subject) + " " + (body == null ? "" : body));
        if (why != null) {
            throw refuse("This template is offered for the step sent " + lowerFirst(point.label()) + ", but it " + why
                    + ". It can use " + String.join(", ", point.has().stream().map(f -> "{{" + f + "}}").toList()) + ".");
        }
        return point.name();
    }

    /**
     * The step a copy of a template is offered for: the original's step, or the step the original is the
     * journey's own wording for, but only when that step is drawn and sends email. Otherwise none, so the
     * copy can be saved as it is and offered for any step its fields suit.
     */
    public String offerForCopy(String srcJourneyPoint, String srcTemplateKey) {
        return JourneyPoint.of(srcJourneyPoint)
                .or(() -> JourneyPoint.ofTemplateKey(srcTemplateKey))
                .filter(JourneyPoint::drawn)
                .filter(JourneyPoint::byEmail)
                .map(Enum::name)
                .orElse(null);
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() || Character.isDigit(s.charAt(0)) ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static ResponseStatusException refuse(String why) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, why);
    }
}
