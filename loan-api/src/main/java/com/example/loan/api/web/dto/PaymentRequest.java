package com.example.loan.api.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PaymentRequest(@NotNull @Positive BigDecimal amount) {}

