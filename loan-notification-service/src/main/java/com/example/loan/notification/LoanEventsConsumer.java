package com.example.loan.notification;

import com.example.loan.events.LoanEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LoanEventsConsumer {
    private static final Logger log = LoggerFactory.getLogger(LoanEventsConsumer.class);

    @KafkaListener(topics = LoanEvents.TOPIC, groupId = "loan-notifier")
    public void onMessage(String message) {
        // In production, logs are scraped by Promtail or direct Loki push; here we log JSON payload
        log.info("loan_event={}", message);
    }
}

