package com.example.loan.api.web;

import com.example.loan.api.domain.model.Loan;
import com.example.loan.api.domain.service.LoanService;
import com.example.loan.events.model.LoanStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LoanApiController.class)
class LoanApiControllerFlagTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    LoanService service;

    @Test
    void simulate_manualFlagOn_overThreshold_returnsPendingApproval() throws Exception {
        Loan loan = new Loan(UUID.randomUUID(), new BigDecimal("15000"), 12, new BigDecimal("0.12"), new BigDecimal("15000"),
                LoanStatus.PENDING_APPROVAL, Instant.now(), null, null, null, null, "c-1");
        when(service.simulate(any(), anyInt(), any(), anyString(), anyBoolean())).thenReturn(loan);

        mvc.perform(post("/loans/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n  \"amount\":15000, \"termMonths\":12, \"interestRate\":0.12, \"customerId\":\"c-1\"\n}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }
}


