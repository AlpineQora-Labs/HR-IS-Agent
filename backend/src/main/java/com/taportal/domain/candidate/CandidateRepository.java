package com.taportal.domain.candidate;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for Candidate rows (Spring Data JPA; derived queries only). */
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Optional<Candidate> findByEmailIgnoreCase(String email);

    /** Everyone who gave this number. One person who applied twice is two rows. */
    java.util.List<Candidate> findByPhoneE164(String phoneE164);
}
