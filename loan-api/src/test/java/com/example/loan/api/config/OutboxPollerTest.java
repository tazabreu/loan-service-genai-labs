package com.example.loan.api.config;

import com.example.loan.api.persistence.OutboxEventEntity;
import com.example.loan.api.persistence.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxPollerTest {
    @Test
    void publishesAndMarksSent() throws Exception {
        OutboxEventRepository repo = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

        OutboxEventEntity e = new OutboxEventEntity();
        e.setId(UUID.randomUUID());
        e.setTopic("loan-events");
        e.setEventType("TestEvent");
        e.setPayload("{\"x\":1}");
        e.setCreatedAt(Instant.now());
        e.setSent(false);

        when(repo.findTop100BySentFalseOrderByCreatedAtAsc()).thenReturn(List.of(e));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(completableFuture());

        OutboxPoller poller = new OutboxPoller(repo, kafka);
        poller.publishBatch();

        verify(kafka, times(1)).send(any(ProducerRecord.class));
        verify(repo, times(1)).save(eq(e));
        // e.setSent(true) is invoked by poller
        assert e.isSent();
    }

    private static <T> java.util.concurrent.CompletableFuture<T> completableFuture() {
        java.util.concurrent.CompletableFuture<T> f = new java.util.concurrent.CompletableFuture<>();
        f.complete(null);
        return f;
    }
}

