package com.example.loan.api.persistence;

import com.example.loan.events.model.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<LoanEntity, UUID> {
    long countByStatus(LoanStatus status);
}
