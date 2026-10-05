package com.taportal.domain.engagement;

import com.taportal.domain.approval.ApprovalDecided;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * What an approval decision on an event sets off. Approving an event that was moved is the
 * moment its new date and place are settled, so that is when its students are told.
 */
@Component
public class EventApprovalListener {

    private final CampusService campus;

    public EventApprovalListener(CampusService campus) {
        this.campus = campus;
    }

    @EventListener
    public void decided(ApprovalDecided decision) {
        if (!"EVENT".equals(decision.itemType()) || !decision.approved()) {
            return;
        }
        UUID eventId;
        try {
            eventId = UUID.fromString(decision.itemRef());
        } catch (IllegalArgumentException e) {
            return; // not one of ours
        }
        campus.tellStudentsOfMove(eventId);
    }
}
