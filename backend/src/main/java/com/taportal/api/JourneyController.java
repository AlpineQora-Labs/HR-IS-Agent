package com.taportal.api;

import com.taportal.api.MessageDtos.JourneyInfo;
import com.taportal.api.MessageDtos.ReminderResult;
import com.taportal.domain.journey.JourneyQueries;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the screens need to know about the candidate journey. They decide none of it. */
@RestController
public class JourneyController {

    private final JourneyQueries queries;

    public JourneyController(JourneyQueries queries) {
        this.queries = queries;
    }

    /** The journey's points, the fields a message can carry, and the rules a journey is drawn with. */
    @GetMapping("/v1/journey")
    public JourneyInfo info() {
        return queries.info();
    }

    /**
     * Send a reminder for a booked interview now, without waiting for its
     * hour. Every other rule still holds: the step has to be on the drawing,
     * the candidate has to have agreed to texts, and a reminder already sent
     * for this booking is not sent again.
     *
     * @param which {@code 24h} or {@code 1h}
     */
    @PostMapping("/v1/interviews/{id}/reminders/{which}")
    public ReminderResult remindNow(@PathVariable UUID id, @PathVariable String which) {
        return queries.remindNow(id, which);
    }
}
