package com.example.loan.api.persistence;

import com.example.loan.events.model.LoanStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "loan")
public class LoanEntity {
    public LoanEntity() {}
    @Id
    private UUID id;
    private BigDecimal amount;
    @Column(name = "term_months")
    private int termMonths;
    @Column(name = "interest_rate")
    private BigDecimal interestRate;
    @Column(name = "remaining_balance")
    private BigDecimal remainingBalance;
    @Enumerated(EnumType.STRING)
    private LoanStatus status;
    @Column(name = "simulated_at")
    private Instant simulatedAt;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "contracted_at")
    private Instant contractedAt;
    @Column(name = "disbursed_at")
    private Instant disbursedAt;
    @Column(name = "last_payment_at")
    private Instant lastPaymentAt;
    @Column(name = "customer_id")
    private String customerId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public int getTermMonths() { return termMonths; }
    public void setTermMonths(int termMonths) { this.termMonths = termMonths; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public BigDecimal getRemainingBalance() { return remainingBalance; }
    public void setRemainingBalance(BigDecimal remainingBalance) { this.remainingBalance = remainingBalance; }
    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }
    public Instant getSimulatedAt() { return simulatedAt; }
    public void setSimulatedAt(Instant simulatedAt) { this.simulatedAt = simulatedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getContractedAt() { return contractedAt; }
    public void setContractedAt(Instant contractedAt) { this.contractedAt = contractedAt; }
    public Instant getDisbursedAt() { return disbursedAt; }
    public void setDisbursedAt(Instant disbursedAt) { this.disbursedAt = disbursedAt; }
    public Instant getLastPaymentAt() { return lastPaymentAt; }
    public void setLastPaymentAt(Instant lastPaymentAt) { this.lastPaymentAt = lastPaymentAt; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
}
