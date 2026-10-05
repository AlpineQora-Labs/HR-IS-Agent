package com.taportal.domain.engagement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for EventRegistration rows (Spring Data JPA; derived queries only). */
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

    List<EventRegistration> findByEventIdOrderByCreatedAt(UUID eventId);

    long countByEventId(UUID eventId);

    long countByEventIdAndStatus(UUID eventId, String status);

    boolean existsByEventIdAndEmailIgnoreCase(UUID eventId, String email);

    long countByEventIdAndStatusIn(UUID eventId, java.util.Collection<String> statuses);

    long countByEventIdAndShortlistedTrue(UUID eventId);

    /** Whether this candidate already holds a live registration for the event, under whichever address. */
    boolean existsByEventIdAndCandidateIdAndStatusNot(UUID eventId, UUID candidateId, String status);

    /** The waitlist in the order students joined it. */
    List<EventRegistration> findByEventIdAndStatusOrderByCreatedAt(UUID eventId, String status);
}
