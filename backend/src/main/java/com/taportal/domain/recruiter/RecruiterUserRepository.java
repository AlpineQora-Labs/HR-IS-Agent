package com.taportal.domain.recruiter;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruiterUserRepository extends JpaRepository<RecruiterUser, UUID> {

    RecruiterUser findFirstByRole(String role);

    List<RecruiterUser> findAll();
}
