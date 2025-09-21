# TASKS

We trimmed the historical prompt and backlog after reviewing the codebase. Focus on the three priorities below before reintroducing any other workstreams.

## Priority 1 – Outbox reliability guardrails
**Objective:** make event publishing resilient to retries, process crashes, and downstream outages.
**Gaps observed:**
- `loan-api/src/main/java/com/example/loan/api/config/OutboxPoller.java` fetches unsent events without locking, so multiple pollers can double-send the same rows.
- Outbox records have no attempt counters, next-at timestamps, or error details, causing hot loops on permanent failures.
- Failed events never route to `LoanEvents.DLQ_TOPIC`, so poison messages block progress indefinitely.
- There is no retention policy; `outbox_event` grows unbounded after successful sends.
**Implementation plan:**
1. Add Flyway migration `loan-api/src/main/resources/db/migration/V2__outbox_reliability.sql` with `attempt_count`, `next_attempt_at`, `last_error`, `sent_at`, and optional `locked_by`, plus supporting indexes.
2. Extend `OutboxEventEntity` and `OutboxEventRepository` with the new columns and a native `FOR UPDATE SKIP LOCKED` query that respects `next_attempt_at` and a configurable batch size.
3. Refactor `OutboxPoller` to use the locking query, wrap each send in a producer span, update attempt metadata, compute exponential backoff, and publish to `LoanEvents.DLQ_TOPIC` once `attempt_count` exceeds a configurable ceiling.
4. Emit metrics (`loan_outbox_publish_success_total`, `loan_outbox_retry_total`, `loan_outbox_dlq_total`) and structured logs for both success and DLQ paths; expose new properties (`outbox.max-attempts`, `outbox.initial-backoff-ms`, `outbox.cleanup-after-days`).
5. Add a scheduled cleanup component that deletes or archives rows older than the retention window once `sent=true`.
**Validation & rollout:**
- Extend `loan-api/src/test/java/com/example/loan/api/config/OutboxPollerTest.java` to cover retry, DLQ, and cleanup paths, and add a Testcontainers-based regression (`loan-api/src/test/java/.../OutboxPollerRetryIT.java`) that simulates Kafka outages.
- Update manual HTTP collections and README to document the new env vars, migration sequencing, and DLQ expectations.

## Priority 2 – Traceable event pipeline
**Objective:** provide end-to-end trace, metric, and log correlation from HTTP request through Kafka to the notification logs.
**Gaps observed:**
- The poller writes a `traceparent` header manually but the consumer never extracts it, so traces stop at the producer span.
- `loan-notification-service/src/main/java/com/example/loan/notification/LoanEventsConsumer.java` logs raw JSON without trace or loan identifiers, making incident triage difficult.
- Neither service emits per-event metrics (latency, failures, message type throughput).
- Logback JSON encoders omit MDC fields, so even with an OTel agent the trace identifiers never reach Loki.
**Implementation plan:**
1. Replace the manual header code in `OutboxPoller` with `W3CTraceContextPropagator` injection, set span attributes (`loan.id`, `loan.event_type`, `kafka.topic`), and surface the current trace id in MDC before logging.
2. Change the consumer signature to accept `ConsumerRecord<String, String>`, use the W3C propagator to extract the context, start a `SpanKind.CONSUMER` span, and parse the payload to push `loan_id`, `event_type`, and `trace_id` into MDC.
3. Register Micrometer timers and counters in both services (`loan_events_publish_latency`, `loan_events_consume_latency`, `loan_events_consume_failures`) and expose them via Actuator/Prometheus.
4. Update `logback-spring.xml` in both services to include MDC providers for `trace_id`, `span_id`, `loan_id`, and to redact large payloads via size caps.
5. Add Grafana dashboards (`dashboards/loan-pipeline.json`) showcasing trace-to-log navigation, DLQ size, and per-event throughput; document troubleshooting steps in README.
**Validation & rollout:**
- Enhance `LoanEventsConsumerTest` to assert MDC enrichment, and add an integration test (`loan-notification-service/src/test/java/.../LoanEventsTracingIT.java`) verifying trace continuity using Testcontainers.
- Expand `manual-tests/loan-lifecycle.http` to capture the trace id and show how to locate the matching log entry in Grafana.

## Priority 3 – JVM-native end-to-end tests
**Objective:** retire the standalone TypeScript/Jest harness and run full life-cycle tests inside Maven for a simpler, hermetic pipeline.
**Gaps observed:**
- The `e2e-tests/` folder duplicates fixtures, requires Node tooling, and is not exercised by `mvn test`, so CI can pass while end-to-end flows break.
- Scenario coverage (manual approval, negative paths, log assertions) would be faster and easier to maintain if they reused existing Spring beans and repositories.
- Developers must context-switch across languages to debug failures, increasing ramp-up time.
**Implementation plan:**
1. Introduce a new Maven module `system-tests` using JUnit 5, Spring Boot test slices, and Testcontainers (`PostgreSQLContainer`, `RedpandaContainer`, `GenericContainer` for Flagd) to host external dependencies.
2. Spin up `loan-api` and `loan-notification-service` inside the same JVM via `SpringBootTest` with dynamic properties pointing at the containers; reuse repositories to assert database state and captured logs.
3. Port the existing Jest scenarios (simulate auto approval, manual approval, contract/disburse/pay, negative transitions) into parameterized `@Test` cases that drive the HTTP layer with `WebTestClient` and assert Kafka/log outcomes with Awaitility.
4. Add optional log capture (ListAppender or Loki API client) to validate structured log fields emitted by Priority 2, and expose helper utilities for future scenarios.
5. Update GitHub Actions workflow to run `mvn -pl system-tests test` in CI, document new commands in README, and archive/remove the `e2e-tests/` Node project once the JVM suite is green and equivalent.
**Validation & rollout:**
- Measure runtime (target <5 minutes) and tune container reuse; publish a quick-start guide under `docs/testing.md` describing how to run the suite with or without Docker.

## Parking lot
- Idempotency keys for POST/PUT endpoints once the outbox pipeline is hardened.
- Schema registry or JSON Schema validation for `loan-events` after DLQ observability work lands.
- Kubernetes manifests and GraalVM native images remain out of scope until the above priorities stabilize.
