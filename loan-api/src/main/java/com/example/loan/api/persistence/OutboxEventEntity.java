package com.example.loan.api.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {
    @Id
    private UUID id;
    private String topic;
    @Column(name = "event_type")
    private String eventType;
    @Column(columnDefinition = "TEXT")
    private String payload;
    @Column(name = "created_at")
    private Instant createdAt;
    private boolean sent;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
}

