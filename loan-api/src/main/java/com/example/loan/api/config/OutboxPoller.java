package com.example.loan.api.config;

import com.example.loan.api.persistence.OutboxEventEntity;
import com.example.loan.api.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {
    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("loan-api");

    private final OutboxEventRepository repo;
    private final KafkaTemplate<String, String> kafka;
    private final DistributionSummary outboxBatchSize;
    private final Counter publishFailures;

    public OutboxPoller(OutboxEventRepository repo, KafkaTemplate<String, String> kafka, MeterRegistry meterRegistry) {
        this.repo = repo; this.kafka = kafka;
        this.outboxBatchSize = DistributionSummary.builder("loan_outbox_batch_size")
                .description("Number of outbox events processed per poll")
                .register(meterRegistry);
        this.publishFailures = Counter.builder("loan_outbox_publish_failures_total")
                .description("Total failures when publishing outbox events")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${outbox.polling.interval:1000}")
    @Transactional
    public void publishBatch() {
        List<OutboxEventEntity> batch = repo.findTop100BySentFalseOrderByCreatedAtAsc();
        outboxBatchSize.record(batch.size());
        for (OutboxEventEntity e : batch) {
            Span span = tracer.spanBuilder("outbox.publish").startSpan();
            try (Scope ignored = span.makeCurrent()) {
                String traceId = span.getSpanContext().getTraceId();
                String spanId = span.getSpanContext().getSpanId();
                String traceparent = String.format("00-%s-%s-01", traceId, spanId);

                ProducerRecord<String, String> record = new ProducerRecord<>(e.getTopic(), e.getId().toString(), e.getPayload());
                record.headers().add(new RecordHeader("traceparent", traceparent.getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("trace_id", traceId.getBytes(StandardCharsets.UTF_8)));

                kafka.send(record).get();
                e.setSent(true);
                repo.save(e);
            } catch (Exception ex) {
                publishFailures.increment();
                log.error("Failed to publish outbox event {}", e.getId(), ex);
            } finally {
                span.end();
            }
        }
    }
}
