package com.example.loan.api.config;

import com.example.loan.api.persistence.OutboxEventEntity;
import com.example.loan.api.persistence.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {
    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private final OutboxEventRepository repo;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPoller(OutboxEventRepository repo, KafkaTemplate<String, String> kafka) {
        this.repo = repo; this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${outbox.polling.interval:1000}")
    @Transactional
    public void publishBatch() {
        List<OutboxEventEntity> batch = repo.findTop100BySentFalseOrderByCreatedAtAsc();
        for (OutboxEventEntity e : batch) {
            try {
                kafka.send(new ProducerRecord<>(e.getTopic(), e.getId().toString(), e.getPayload())).get();
                e.setSent(true);
                repo.save(e);
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {}", e.getId(), ex);
            }
        }
    }
}
