package com.taportal.domain.comms;

import com.taportal.domain.journey.JourneyPoint;
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
 * works: it can be reworded, but it cannot be deleted, taken out of use, or
 * lose the fields its point needs — so a journey point always has something
 * to say.
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
     * @param text true for a text message, false for an email
     */
    public void mayChange(String templateKey, String name, String newStatus, String newBody, boolean text) {
        if (templateKey == null) {
            return;
        }
        if (!"ACTIVE".equals(newStatus)) {
            throw refuse("“" + name + "” words a step of the candidate journey, so it has to stay Active.");
        }
        JourneyPoint point = JourneyPoint.ofTemplateKey(templateKey).orElse(null);
        if (point == null || !text) {
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

    private static String lowerFirst(String s) {
        return s.isEmpty() || Character.isDigit(s.charAt(0)) ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static ResponseStatusException refuse(String why) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, why);
    }
}
