package com.example.loan.api.domain.service;

import com.example.loan.api.persistence.LoanEntity;
import com.example.loan.api.persistence.LoanRepository;
import com.example.loan.events.model.LoanStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LoanServiceStateTest {
    LoanRepository repo;
    OutboxWriter outbox;
    LoanService service;

    @BeforeEach
    void setUp() {
        repo = mock(LoanRepository.class);
        outbox = mock(OutboxWriter.class);
        service = new LoanService(repo, outbox, new ObjectMapper().findAndRegisterModules(), new BigDecimal("10000"), new SimpleMeterRegistry());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approve_requiresPending() {
        UUID id = UUID.randomUUID();
        LoanEntity e = baseLoan(id);
        e.setStatus(LoanStatus.SIMULATED);
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.approve(id, "user")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void contract_requiresApproved() {
        UUID id = UUID.randomUUID();
        LoanEntity e = baseLoan(id);
        e.setStatus(LoanStatus.PENDING_APPROVAL);
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.contract(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disburse_requiresContracted() {
        UUID id = UUID.randomUUID();
        LoanEntity e = baseLoan(id);
        e.setStatus(LoanStatus.APPROVED);
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.disburse(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pay_requiresDisbursed_andPositiveAmount() {
        UUID id = UUID.randomUUID();
        LoanEntity e = baseLoan(id);
        e.setStatus(LoanStatus.APPROVED);
        e.setRemainingBalance(new BigDecimal("100"));
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.pay(id, new BigDecimal("10"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.pay(id, new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    private static LoanEntity baseLoan(UUID id) {
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(new BigDecimal("1000"));
        e.setTermMonths(12);
        e.setInterestRate(new BigDecimal("0.12"));
        e.setRemainingBalance(new BigDecimal("1000"));
        e.setSimulatedAt(Instant.now());
        e.setCustomerId("c-1");
        return e;
    }
}
