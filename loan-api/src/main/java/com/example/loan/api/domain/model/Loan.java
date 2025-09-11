package com.example.loan.api.domain.model;

import com.example.loan.events.model.LoanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Loan(
        UUID id,
        BigDecimal amount,
        int termMonths,
        BigDecimal interestRate,
        BigDecimal remainingBalance,
        LoanStatus status,
        Instant simulatedAt,
        Instant approvedAt,
        Instant contractedAt,
        Instant disbursedAt,
        Instant lastPaymentAt,
        String customerId
) {}

