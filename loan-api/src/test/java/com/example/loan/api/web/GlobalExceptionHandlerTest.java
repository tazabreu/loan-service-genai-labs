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
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoanApiController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    LoanService service;

    @Test
    void approve_nonexistent_returns404() throws Exception {
        when(service.approve(any(UUID.class), anyString())).thenThrow(new NoSuchElementException("not found"));
        mvc.perform(post("/loans/" + UUID.randomUUID() + "/approve").header("X-User", "u"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void pay_invalid_state_returns409() throws Exception {
        when(service.pay(any(UUID.class), any())).thenThrow(new IllegalStateException("Invalid state"));
        mvc.perform(post("/loans/" + UUID.randomUUID() + "/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invalid_state"));
    }
}

