package com.example.loan.api.domain.service;

import com.example.loan.api.domain.model.Loan;
import com.example.loan.api.persistence.LoanEntity;

public final class LoanMapper {
    private LoanMapper() {}

    public static Loan toDomain(LoanEntity e) {
        return new Loan(
                e.getId(), e.getAmount(), e.getTermMonths(), e.getInterestRate(), e.getRemainingBalance(),
                e.getStatus(), e.getSimulatedAt(), e.getApprovedAt(), e.getContractedAt(), e.getDisbursedAt(),
                e.getLastPaymentAt(), e.getCustomerId());
    }
}

