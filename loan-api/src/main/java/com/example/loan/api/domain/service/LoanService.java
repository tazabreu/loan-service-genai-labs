package com.example.loan.api.domain.service;

import com.example.loan.api.domain.model.Loan;
import com.example.loan.api.persistence.LoanEntity;
import com.example.loan.api.persistence.LoanRepository;
import com.example.loan.events.LoanEvents;
import com.example.loan.events.model.LoanEventsPayloads;
import com.example.loan.events.model.LoanStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoanService {
    private final LoanRepository repo;
    private final OutboxWriter outbox;
    private final ObjectMapper mapper;
    private final BigDecimal approvalThreshold;

    public LoanService(LoanRepository repo, OutboxWriter outbox, ObjectMapper mapper,
                       @Value("${loan.approval.threshold:10000}") BigDecimal approvalThreshold) {
        this.repo = repo; this.outbox = outbox; this.mapper = mapper; this.approvalThreshold = approvalThreshold;
    }

    @Transactional
    public Loan simulate(BigDecimal amount, int termMonths, BigDecimal rate, String customerId, boolean manualApprovalEnabled) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(amount);
        e.setTermMonths(termMonths);
        e.setInterestRate(rate);
        e.setRemainingBalance(amount);
        e.setStatus(LoanStatus.SIMULATED);
        e.setSimulatedAt(now);
        e.setCustomerId(customerId);
        repo.save(e);

        writeEvent(new LoanEventsPayloads.LoanSimulatedEvent(id, amount, termMonths, rate, customerId, now, LoanStatus.SIMULATED));

        if (manualApprovalEnabled && amount.compareTo(approvalThreshold) > 0) {
            e.setStatus(LoanStatus.PENDING_APPROVAL);
            repo.save(e);
            writeEvent(new LoanEventsPayloads.LoanPendingApprovalEvent(id, amount, customerId, Instant.now()));
        } else {
            e.setStatus(LoanStatus.APPROVED);
            e.setApprovedAt(Instant.now());
            repo.save(e);
            writeEvent(new LoanEventsPayloads.LoanApprovedEvent(id, "system", e.getApprovedAt()));
        }
        return LoanMapper.toDomain(e);
    }

    @Transactional
    public Loan approve(UUID id, String user) { return setApproval(id, true, user); }
    @Transactional
    public Loan reject(UUID id, String user, String reason) { return setApproval(id, false, user, reason); }

    private Loan setApproval(UUID id, boolean approve, String user) { return setApproval(id, approve, user, null); }

    private Loan setApproval(UUID id, boolean approve, String user, String reason) {
        LoanEntity e = repo.findById(id).orElseThrow();
        if (e.getStatus() != LoanStatus.PENDING_APPROVAL) throw new IllegalStateException("Invalid state");
        if (approve) {
            e.setStatus(LoanStatus.APPROVED);
            e.setApprovedAt(Instant.now());
            repo.save(e);
            writeEvent(new LoanEventsPayloads.LoanApprovedEvent(id, user, e.getApprovedAt()));
        } else {
            e.setStatus(LoanStatus.REJECTED);
            repo.save(e);
            writeEvent(new LoanEventsPayloads.LoanRejectedEvent(id, user, Optional.ofNullable(reason).orElse("unspecified"), Instant.now()));
        }
        return LoanMapper.toDomain(e);
    }

    @Transactional
    public Loan contract(UUID id) {
        LoanEntity e = repo.findById(id).orElseThrow();
        if (e.getStatus() != LoanStatus.APPROVED) throw new IllegalStateException("Invalid state");
        e.setStatus(LoanStatus.CONTRACTED);
        e.setContractedAt(Instant.now());
        repo.save(e);
        writeEvent(new LoanEventsPayloads.LoanContractedEvent(id, e.getContractedAt()));
        return LoanMapper.toDomain(e);
    }

    @Transactional
    public Loan disburse(UUID id) {
        LoanEntity e = repo.findById(id).orElseThrow();
        if (e.getStatus() != LoanStatus.CONTRACTED) throw new IllegalStateException("Invalid state");
        e.setStatus(LoanStatus.DISBURSED);
        e.setDisbursedAt(Instant.now());
        repo.save(e);
        writeEvent(new LoanEventsPayloads.LoanDisbursedEvent(id, e.getDisbursedAt()));
        return LoanMapper.toDomain(e);
    }

    @Transactional
    public Loan pay(UUID id, BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        LoanEntity e = repo.findById(id).orElseThrow();
        if (e.getStatus() != LoanStatus.DISBURSED) throw new IllegalStateException("Invalid state");
        BigDecimal newBal = e.getRemainingBalance().subtract(amount);
        if (newBal.signum() < 0) throw new IllegalArgumentException("payment exceeds balance");
        e.setRemainingBalance(newBal);
        e.setLastPaymentAt(Instant.now());
        if (newBal.signum() == 0) e.setStatus(LoanStatus.PAID);
        repo.save(e);
        writeEvent(new LoanEventsPayloads.LoanPaymentMadeEvent(id, amount, newBal, e.getLastPaymentAt()));
        return LoanMapper.toDomain(e);
    }

    private void writeEvent(Object payload) {
        try {
            outbox.write(LoanEvents.TOPIC, payload.getClass().getSimpleName(), mapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

