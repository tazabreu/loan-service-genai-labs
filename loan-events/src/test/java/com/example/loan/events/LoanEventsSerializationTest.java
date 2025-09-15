package com.example.loan.events;

import com.example.loan.events.model.LoanEventsPayloads;
import com.example.loan.events.model.LoanStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoanEventsSerializationTest {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void roundtrip_LoanSimulatedEvent() throws Exception {
        LoanEventsPayloads.LoanSimulatedEvent payload = new LoanEventsPayloads.LoanSimulatedEvent(
                UUID.randomUUID(), new BigDecimal("1000"), 12, new BigDecimal("0.10"), "c-1", Instant.now(), LoanStatus.SIMULATED);
        String json = mapper.writeValueAsString(payload);
        LoanEventsPayloads.LoanSimulatedEvent back = mapper.readValue(json, LoanEventsPayloads.LoanSimulatedEvent.class);
        assertThat(back.id()).isEqualTo(payload.id());
        assertThat(back.amount()).isEqualByComparingTo(payload.amount());
        assertThat(back.status()).isEqualTo(LoanStatus.SIMULATED);
    }

    @Test
    void roundtrip_PaymentMadeEvent() throws Exception {
        LoanEventsPayloads.LoanPaymentMadeEvent payload = new LoanEventsPayloads.LoanPaymentMadeEvent(
                UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("900"), Instant.now());
        String json = mapper.writeValueAsString(payload);
        LoanEventsPayloads.LoanPaymentMadeEvent back = mapper.readValue(json, LoanEventsPayloads.LoanPaymentMadeEvent.class);
        assertThat(back.remainingBalance()).isEqualByComparingTo("900");
    }
}


