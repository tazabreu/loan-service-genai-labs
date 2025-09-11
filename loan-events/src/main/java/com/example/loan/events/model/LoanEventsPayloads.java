package com.example.loan.events.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class LoanEventsPayloads {
    private LoanEventsPayloads() {}

    public record LoanSimulatedEvent(UUID id, BigDecimal amount, int termMonths, BigDecimal interestRate,
                                     String customerId, Instant simulatedAt, LoanStatus status) {}

    public record LoanPendingApprovalEvent(UUID id, BigDecimal amount, String customerId, Instant at) {}

    public record LoanApprovedEvent(UUID id, String approvedBy, Instant approvedAt) {}

    public record LoanRejectedEvent(UUID id, String rejectedBy, String reason, Instant rejectedAt) {}

    public record LoanContractedEvent(UUID id, Instant contractedAt) {}

    public record LoanDisbursedEvent(UUID id, Instant disbursedAt) {}

    public record LoanPaymentMadeEvent(UUID id, BigDecimal amount, BigDecimal remainingBalance, Instant paidAt) {}
}

