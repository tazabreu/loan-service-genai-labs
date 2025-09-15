package com.example.loan.notification;

import com.example.loan.events.LoanEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;

class LoanEventsConsumerTest {

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoanEventsConsumer.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoanEventsConsumer.class);
        logger.detachAppender(listAppender);
    }

    @Test
    void onMessage_logsPayload() {
        LoanEventsConsumer consumer = new LoanEventsConsumer();
        String payload = "{\"id\":\"00000000-0000-0000-0000-000000000000\",\"type\":\"LoanSimulatedEvent\"}";

        consumer.onMessage(payload);

        assertThat(listAppender.list)
                .anySatisfy(evt -> assertThat(evt.getFormattedMessage()).contains("loan_event=").contains("LoanSimulatedEvent"));
    }
}
