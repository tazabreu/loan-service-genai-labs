package com.example.loan.api.domain.service;

import com.example.loan.api.domain.model.Loan;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanServiceAdditionalTest {

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
    void simulate_equalToThreshold_autoApproves() {
        Loan loan = service.simulate(new BigDecimal("10000"), 12, new BigDecimal("0.10"), "c-3", true);
        assertThat(loan.status()).isEqualTo(LoanStatus.APPROVED);
        verify(outbox, times(2)).write(eq("loan-events"), anyString(), anyString());
    }

    @Test
    void simulate_overThreshold_butManualDisabled_autoApproves() {
        Loan loan = service.simulate(new BigDecimal("15000"), 12, new BigDecimal("0.10"), "c-4", false);
        assertThat(loan.status()).isEqualTo(LoanStatus.APPROVED);
        verify(outbox, times(2)).write(eq("loan-events"), anyString(), anyString());
    }

    @Test
    void approve_fromPending_setsApproved_andEmitsEvent() {
        UUID id = UUID.randomUUID();
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(new BigDecimal("1000"));
        e.setRemainingBalance(new BigDecimal("1000"));
        e.setStatus(LoanStatus.PENDING_APPROVAL);
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));

        Loan updated = service.approve(id, "user1");

        assertThat(updated.status()).isEqualTo(LoanStatus.APPROVED);
        verify(outbox).write(eq("loan-events"), eq("LoanApprovedEvent"), anyString());
    }

    @Test
    void reject_fromPending_setsRejected_andEmitsEvent() {
        UUID id = UUID.randomUUID();
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(new BigDecimal("1000"));
        e.setRemainingBalance(new BigDecimal("1000"));
        e.setStatus(LoanStatus.PENDING_APPROVAL);
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));

        Loan updated = service.reject(id, "user2", "bad docs");

        assertThat(updated.status()).isEqualTo(LoanStatus.REJECTED);
        verify(outbox).write(eq("loan-events"), eq("LoanRejectedEvent"), anyString());
    }

    @Test
    void contract_fromApproved_setsContracted_andEmitsEvent() {
        UUID id = UUID.randomUUID();
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(new BigDecimal("1000"));
        e.setRemainingBalance(new BigDecimal("1000"));
        e.setStatus(LoanStatus.APPROVED);
        e.setApprovedAt(Instant.now());
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));

        Loan updated = service.contract(id);

        assertThat(updated.status()).isEqualTo(LoanStatus.CONTRACTED);
        verify(outbox).write(eq("loan-events"), eq("LoanContractedEvent"), anyString());
    }

    @Test
    void disburse_fromContracted_setsDisbursed_andEmitsEvent() {
        UUID id = UUID.randomUUID();
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(new BigDecimal("1000"));
        e.setRemainingBalance(new BigDecimal("1000"));
        e.setStatus(LoanStatus.CONTRACTED);
        e.setContractedAt(Instant.now());
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));

        Loan updated = service.disburse(id);

        assertThat(updated.status()).isEqualTo(LoanStatus.DISBURSED);
        verify(outbox).write(eq("loan-events"), eq("LoanDisbursedEvent"), anyString());
    }

    @Test
    void pay_negative_throws() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.pay(id, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void pay_overpayment_throws() {
        UUID id = UUID.randomUUID();
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(new BigDecimal("1000"));
        e.setRemainingBalance(new BigDecimal("100"));
        e.setStatus(LoanStatus.DISBURSED);
        e.setDisbursedAt(Instant.now());
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.pay(id, new BigDecimal("200")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void invalid_state_transitions_throw() {
        UUID id = UUID.randomUUID();
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(new BigDecimal("1000"));
        e.setRemainingBalance(new BigDecimal("1000"));
        e.setStatus(LoanStatus.SIMULATED);
        when(repo.findById(eq(id))).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.contract(id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.disburse(id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.approve(id, "u")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.reject(id, "u", "r")).isInstanceOf(IllegalStateException.class);
    }
}


