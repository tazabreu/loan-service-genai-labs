package com.example.loan.api.web;

import com.example.loan.api.domain.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoanApiController.class)
class LoanApiControllerValidationTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    LoanService service;

    @Test
    void simulate_invalidPayload_returnsBadRequest() throws Exception {
        // negative amount should be rejected by validation
        String body = "{\n  \"amount\": -1, \"termMonths\": 12, \"interestRate\": 0.12, \"customerId\": \"c-1\"\n}";
        mvc.perform(post("/loans/simulate").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
    }
}

