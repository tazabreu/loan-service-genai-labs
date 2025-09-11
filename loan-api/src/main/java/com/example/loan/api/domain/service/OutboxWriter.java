package com.example.loan.api.domain.service;

import com.example.loan.api.persistence.OutboxEventEntity;
import com.example.loan.api.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxWriter {
    private final OutboxEventRepository repo;

    public OutboxWriter(OutboxEventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public UUID write(String topic, String eventType, String payload) {
        OutboxEventEntity e = new OutboxEventEntity();
        e.setId(UUID.randomUUID());
        e.setTopic(topic);
        e.setEventType(eventType);
        e.setPayload(payload);
        e.setCreatedAt(Instant.now());
        e.setSent(false);
        repo.save(e);
        return e.getId();
    }
}

