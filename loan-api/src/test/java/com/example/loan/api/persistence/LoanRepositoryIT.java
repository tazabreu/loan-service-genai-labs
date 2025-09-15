package com.example.loan.api.persistence;

import com.example.loan.events.model.LoanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LoanRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void start() {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    LoanRepository repo;

    @Test
    void save_and_load_entity_with_status_and_timestamps() {
        UUID id = UUID.randomUUID();
        LoanEntity e = new LoanEntity();
        e.setId(id);
        e.setAmount(new BigDecimal("5000"));
        e.setTermMonths(12);
        e.setInterestRate(new BigDecimal("0.10"));
        e.setRemainingBalance(new BigDecimal("5000"));
        e.setStatus(LoanStatus.SIMULATED);
        e.setSimulatedAt(Instant.now());
        e.setCustomerId("c-it-1");

        repo.save(e);

        Optional<LoanEntity> loaded = repo.findById(id);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getStatus()).isEqualTo(LoanStatus.SIMULATED);
        assertThat(loaded.get().getRemainingBalance()).isEqualByComparingTo("5000");
    }

    @Test
    void countByStatus_returns_expected_counts() {
        long before = repo.countByStatus(LoanStatus.APPROVED);

        LoanEntity e = new LoanEntity();
        e.setId(UUID.randomUUID());
        e.setAmount(new BigDecimal("1000"));
        e.setTermMonths(6);
        e.setInterestRate(new BigDecimal("0.05"));
        e.setRemainingBalance(new BigDecimal("1000"));
        e.setStatus(LoanStatus.APPROVED);
        e.setSimulatedAt(Instant.now());
        e.setApprovedAt(Instant.now());
        e.setCustomerId("c-it-2");
        repo.save(e);

        long after = repo.countByStatus(LoanStatus.APPROVED);
        assertThat(after).isEqualTo(before + 1);
    }
}


