package com.taportal.domain.candidate;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for Candidate rows (Spring Data JPA; derived queries only). */
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Optional<Candidate> findByEmailIgnoreCase(String email);

    /** Everyone on record under this address, as their main one or their second one. */
    @org.springframework.data.jpa.repository.Query(
            "select c from Candidate c where lower(c.email) = lower(:email) or lower(c.altEmail) = lower(:email)")
    java.util.List<Candidate> findByEitherEmail(@org.springframework.data.repository.query.Param("email") String email);

    /** Everyone who gave this number. One person who applied twice is two rows. */
    java.util.List<Candidate> findByPhoneE164(String phoneE164);
}
