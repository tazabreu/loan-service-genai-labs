package com.example.loan.api.domain.service;

import com.example.loan.api.domain.model.Loan;
import com.example.loan.api.persistence.LoanEntity;
import com.example.loan.api.persistence.LoanRepository;
import com.example.loan.events.model.LoanStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanServiceTest {

    LoanRepository repo;
    OutboxWriter outbox;
    LoanService service;

    @BeforeEach
    void setUp() {
        repo = mock(LoanRepository.class);
        outbox = mock(OutboxWriter.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        service = new LoanService(repo, outbox, mapper, new BigDecimal("10000"), new SimpleMeterRegistry());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void simulate_overThreshold_withManual_requiresApproval() {
        Loan loan = service.simulate(new BigDecimal("15000"), 12, new BigDecimal("0.12"), "c-1", true);
        assertThat(loan.status()).isEqualTo(LoanStatus.PENDING_APPROVAL);
        verify(repo, atLeastOnce()).save(any());
        verify(outbox, times(2)).write(eq("loan-events"), anyString(), anyString());
    }

    @Test
    void simulate_underThreshold_autoApproves() {
        Loan loan = service.simulate(new BigDecimal("5000"), 12, new BigDecimal("0.12"), "c-2", true);
        assertThat(loan.status()).isEqualTo(LoanStatus.APPROVED);
        verify(outbox, times(2)).write(eq("loan-events"), anyString(), anyString());
    }

    @Test
    void pay_fullBalance_setsPaid_andEmitsEvent() {
        UUID id = UUID.randomUUID();
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(new BigDecimal("1000"));
        e.setRemainingBalance(new BigDecimal("1000"));
        e.setStatus(LoanStatus.DISBURSED);
        e.setDisbursedAt(Instant.now());
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));

        Loan updated = service.pay(id, new BigDecimal("1000"));

        assertThat(updated.remainingBalance()).isZero();
        assertThat(updated.status()).isEqualTo(LoanStatus.PAID);
        verify(outbox).write(eq("loan-events"), eq("LoanPaymentMadeEvent"), anyString());
    }
}
