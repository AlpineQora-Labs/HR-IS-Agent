package com.taportal.domain.recruiter;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for RecruiterUser rows (Spring Data JPA; derived queries only). */
public interface RecruiterUserRepository extends JpaRepository<RecruiterUser, UUID> {

    RecruiterUser findFirstByRole(String role);

    List<RecruiterUser> findAll();

    /**
     * The people of a panel, held in a fixed order so that two bookings for the
     * same interviewer wait for each other instead of both going through.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from RecruiterUser u where u.id in :ids order by u.id")
    List<RecruiterUser> lockAllByIdIn(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
}
