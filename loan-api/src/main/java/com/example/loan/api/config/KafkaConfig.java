package com.example.loan.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {
    @Bean
    @ConditionalOnProperty(name = "kafka.topics.auto-create", havingValue = "true", matchIfMissing = true)
    NewTopic loanEventsTopic() { return new NewTopic("loan-events", 1, (short) 1); }

    @Bean
    @ConditionalOnProperty(name = "kafka.topics.auto-create", havingValue = "true", matchIfMissing = true)
    NewTopic loanEventsDlqTopic() { return new NewTopic("loan-events-dlq", 1, (short) 1); }
}

