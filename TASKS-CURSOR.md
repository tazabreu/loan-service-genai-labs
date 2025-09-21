# TASKS (CURATED FOR RESILIENCE)

Purpose: This is a focused, actionable task list oriented strictly around operational resilience and correctness. It trims general scaffolding and keeps the highest-impact items to harden durability, availability, and observability.

Scope of curation:
- Removed: initial scaffolding, broad docs/CI chores, generic feature backlog.
- Kept: reliability-critical items for messaging, storage, startup/disposability, and runtime safety.

## Top 3 Next Tasks (detailed plans)

### 1) Transactional Outbox Reliability Hardening (loan-api)

Why: Current poller selects unsent rows and publishes in a single transaction without row-level claiming or retry/backoff. Under concurrency or failures this risks duplicates, stuck rows, and head-of-line blocking.

Key improvements:
- Row-level claiming with SKIP LOCKED or advisory locks to avoid double-publishing.
- Attempt tracking and exponential backoff to prevent hot-looping failures.
- Idempotent Kafka producer config (acks=all, enable.idempotence=true, tuned retries) to improve delivery guarantees.
- DLQ routing for max-attempt exhausted events.
- Backlog and failure metrics; batch sizing via config.

Implementation steps:
1. Schema migration (Flyway V2):
   - Table `outbox_event`: add columns
     - `attempts INT NOT NULL DEFAULT 0`
     - `locked_at TIMESTAMP WITH TIME ZONE NULL`
     - `locked_by TEXT NULL`
     - `sent_at TIMESTAMP WITH TIME ZONE NULL`
     - `last_error_at TIMESTAMP WITH TIME ZONE NULL`
     - `next_attempt_at TIMESTAMP WITH TIME ZONE NULL`
     - `last_error TEXT NULL`
   - Indexes: `(sent)`, `(next_attempt_at)`, `(created_at)`

2. Repository updates (loan-api):
   - Replace `findTop100BySentFalseOrderByCreatedAtAsc()` with a native claim query using `FOR UPDATE SKIP LOCKED` over rows due now:
     - `SELECT ... FROM outbox_event WHERE sent = false AND (next_attempt_at IS NULL OR next_attempt_at <= now()) ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED`.
     - Update claimed rows with `locked_by = :workerId, locked_at = now()` in the same transaction, returning the rows.
   - Add methods to mark success (`sent=true, sent_at=now()`) and to record failure (`attempts=attempts+1, last_error, last_error_at, next_attempt_at=now()+backoff`).

3. Poller logic (loan-api `OutboxPoller`):
   - Add `outbox.batch-size`, `outbox.max-attempts`, `outbox.backoff.initial-ms`, `outbox.backoff.max-ms` properties.
   - Generate a stable `workerId` at startup (hostname+pid+uuid).
   - Each tick:
     - Begin transaction to claim up to `batch-size` rows using SKIP LOCKED; commit claim quickly.
     - For each claimed row, publish individually; on success mark sent; on failure increment attempts and compute exponential backoff (e.g., `min(max, initial * 2^attempts)` with jitter).
     - If attempts exceed `max-attempts`, publish the payload to `loan-events-dlq` with metadata (original topic/id/attempts/last_error) and mark outbox row as `sent=true,sent_at=now()` (consider separate terminal state if preferred).
   - Avoid long-running single transaction; use per-event transactions for updates to reduce lock contention.
   - Keep existing trace headers; ensure key uses event id to preserve ordering per entity where relevant.

4. Kafka producer configuration (loan-api):
   - Set producer props (via `application.yml` or `ProducerFactory`):
     - `acks=all`
     - `enable.idempotence=true`
     - `retries=Integer.MAX_VALUE`
     - `delivery.timeout.ms=120000` (tuned)
     - `max.in.flight.requests.per.connection=1` (or 5 if ordering by key is acceptable)
   - Consider `linger.ms`/`batch.size` for throughput.

5. Metrics & logging:
   - Counters: `loan_outbox_publish_failures_total`, successes; summary: `loan_outbox_batch_size` (exists).
   - Gauges: backlog size (unsent & due), in-flight claimed count, DLQ publishes.

6. Tests:
   - Unit: backoff calculation, claim logic, success/failure state transitions, DLQ routing condition.
   - Integration (Testcontainers: Postgres + Redpanda): concurrent pollers do not double-publish; attempts increment and back off; DLQ written after max attempts; messages are delivered exactly once per event id under idempotent producer.
   - E2E: chaos case where broker is temporarily unavailable; verify no data loss and eventual delivery/ DLQ.

Acceptance criteria:
- No duplicate publishes under two or more pollers for the same batch.
- Failed events retry with exponential backoff and eventually move to DLQ after `max-attempts`.
- Producer uses idempotence and `acks=all`.
- Backlog and failure metrics visible in Prometheus; Grafana panel added to overview.

Impacted files (non-exhaustive):
- `loan-api/src/main/resources/db/migration/V2__outbox_reliability.sql` (new)
- `loan-api/src/main/java/com/example/loan/api/persistence/OutboxEventEntity.java`
- `loan-api/src/main/java/com/example/loan/api/persistence/OutboxEventRepository.java`
- `loan-api/src/main/java/com/example/loan/api/config/OutboxPoller.java`
- `loan-api/src/main/java/com/example/loan/api/config/KafkaConfig.java` (producer props)
- `loan-api/src/main/resources/application.yml`

---

### 2) Consumer Resilience: Retries, Backoff, and DLQ (loan-notification-service)

Why: The consumer logs messages but lacks structured error handling and DLQ. Poison pills can stall progress or cause silent drops.

Key improvements:
- Framework-managed retry with backoff and dead-letter topic routing.
- Robust deserialization with error handling and type validation.
- Trace correlation from producer headers.
- Metrics for successes, retries, dead-letters.

Implementation steps:
1. Topic & config:
   - Ensure DLQ topic exists (`loan-events-dlq` is already auto-created in loan-api; verify in compose or create here too).
   - Configure consumer props: `spring.kafka.consumer` with `auto-offset-reset=earliest`, `max.poll.records`, `max.poll.interval.ms`, `enable.auto.commit=false` (if using manual acks), and `spring.json.trusted.packages=*` or explicit model.

2. Retry topology:
   - Use Spring Kafka retryable topics (preferred): define `RetryTopicConfiguration` bean with backoff (e.g., 1s → 10s → 30s → 2m) and a final DLT.
   - Alternative: `@RetryableTopic` on the `@KafkaListener` method.
   - For non-retryable exceptions (validation), route directly to DLT.

3. Error handling & validation:
   - Enable `ErrorHandlingDeserializer` to handle deserialization exceptions and route to DLT with headers.
   - Parse and validate payload structure; if invalid, throw a classification exception to skip retries.

4. Tracing & logging:
   - Read `traceparent`/`trace_id` headers; start a span (`consumer.process`) with extracted context for correlation.
   - Log structured fields (`loan_event=...`, `trace_id=...`, `retry_attempt=...`).

5. Metrics:
   - Counters: processed, failed, retried, dead-lettered.
   - Consumer lag: export via Micrometer Kafka metrics if available.

6. Tests:
   - Unit: retry classifier, validation paths, header extraction.
   - Integration (Testcontainers Redpanda): produce poison pill → assert DLT receive; transient failure → assert retry then success.
   - TS e2e negative-path: ensure DLT receives expected record.

Acceptance criteria:
- Poison pill messages land in `loan-events-dlq` with original payload and error metadata.
- Transient errors are retried with backoff then succeed without manual intervention.
- Logs and traces correlate with producer via headers; metrics exported.

Impacted files (non-exhaustive):
- `loan-notification-service/src/main/java/com/example/loan/notification/LoanEventsConsumer.java`
- `loan-notification-service/src/main/java/.../KafkaRetryConfig.java` (new)
- `loan-notification-service/src/main/resources/application.yml`
- `docker/docker-compose.yml` (if topic auto-create differs)

---

### 3) Readiness/Liveness, Graceful Shutdown, and Dependency Health

Why: Robust startup/shutdown and accurate readiness signals prevent cascading failures and avoid serving traffic when dependencies are unavailable.

Key improvements:
- Health groups: `/livez` for basic process health; `/readyz` requires DB and Kafka connectivity.
- Graceful shutdown of HTTP, poller, and Kafka clients.
- Time-bounded stop to prevent stuck shutdowns.

Implementation steps:
1. Actuator health groups (both services):
   - Configure groups: `management.endpoint.health.group.readiness.include=db,kafka`, `management.endpoint.health.group.liveness.include=ping`.
   - Map convenient aliases: expose `/readyz` and `/livez` via simple MVC mappings if desired, or use `/actuator/health/readiness` and `/actuator/health/liveness`.

2. Graceful shutdown:
   - Set `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`.
   - Ensure `OutboxPoller` stops promptly: guard scheduling with `@ConditionalOnProperty`, and implement `SmartLifecycle` or cooperative cancellation via flag to stop the loop quickly.
   - Ensure Kafka producer/consumer close on shutdown hooks.

3. Tests:
   - Integration: bring services up, then stop Kafka/Postgres and assert readiness becomes `DOWN` while liveness stays `UP`.
   - Verify shutdown completes within the configured timeout under load.

Acceptance criteria:
- `/actuator/health/readiness` returns `UP` only when DB and Kafka checks pass; otherwise `DOWN`.
- Graceful shutdown completes within 30s and no in-flight outbox work is abandoned mid-send (claimed rows are released/retried on next start).
- Probes documented for Kubernetes; compose remains unaffected.

Impacted files (non-exhaustive):
- `loan-api/src/main/resources/application.yml`
- `loan-notification-service/src/main/resources/application.yml`
- `loan-api/src/main/java/com/example/loan/api/config/OutboxPoller.java`
- `README.md` (probe endpoints table)

---

## Secondary Backlog (next up after top 3)
- API idempotency for mutating endpoints via `Idempotency-Key` (dedupe store + TTL).
- Outbox TTL cleanup job and admin endpoint to purge delivered rows.
- Consumer span creation with context propagation helpers; add trace IDs to logs by default.
- Chaos experiments (compose): kill Postgres/Redpanda mid-flow; verify retry and no data loss.
- Grafana panels for backlog size, DLQ rate, consumer retry rate, readiness statuses.

## Notes
- Existing code reference:
  - Poller: `loan-api/src/main/java/com/example/loan/api/config/OutboxPoller.java`
  - Topics: `loan-api/src/main/java/com/example/loan/api/config/KafkaConfig.java`
  - Consumer: `loan-notification-service/src/main/java/com/example/loan/notification/LoanEventsConsumer.java`
  - Actuator exposure present in `application.yml` files; health groups pending.


