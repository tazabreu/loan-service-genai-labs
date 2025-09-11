package com.example.loan.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record LoanSimulationRequest(
        @NotNull @Positive BigDecimal amount,
        @Positive int termMonths,
        @NotNull @Positive BigDecimal interestRate,
        @NotBlank String customerId
) {}

