package com.example.loan.api.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<LoanEntity, UUID> {}

