package com.olivia.domain.talent;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalEmployeeRepository extends JpaRepository<InternalEmployee, UUID> {}
