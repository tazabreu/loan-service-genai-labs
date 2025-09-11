# TASKS

This file is our working memory bank. We track the plan, decisions, and progress as checkable tasks. Update it as steps complete.

## Working Prompt
- Loan Management System implementation plan for two Spring Boot 4.0 microservices (loan-api and loan-notification-service) with PostgreSQL, Redpanda, OpenTelemetry, Grafana Stack, Flagd, thorough testing, Docker Compose, GraalVM native images, and CI/CD readiness.

## Goals
- Deliver a concise, verifiable implementation aligned with the provided prompt.
- Maintain 12‑factor practices, tests, and clear developer ergonomics.

## Assumptions (to confirm)
- Primary stack (Python or Node) will be specified with the prompt.
- Acceptance criteria will be explicit and testable.

## Implementation Plan (high‑level)
1) Architecture & tech stack decisions
2) Project scaffolding (Maven multi-module)
3) Domain, persistence, and API (loan-api)
4) Outbox pattern and Kafka publishing
5) Notification consumer (loan-notification-service)
6) Observability (metrics, logs, traces, dashboards)
7) Testing (unit, integration, e2e, mutation)
8) Local run (Docker Compose) and docs
9) CI/CD and Kubernetes manifests

## Task List
- [x] Receive target prompt from user
- [x] Freeze scope, constraints, and assumptions
- [x] Define acceptance criteria and test plan (from prompt)
- [x] Draft detailed implementation plan (this file)
- [ ] Design components and data flows (ASCII diagram)
- [x] Scaffold Maven multi-module: parent, `loan-api`, `loan-notification-service`, `loan-events`
- [x] Add core dependencies (Spring Boot 4.0, Kafka, JPA, Validation, Testcontainers, OpenFeature, Springdoc)
- [x] Define DB schema (Loan, OutboxEvent) and Liquibase/Flyway migrations
- [x] Implement `loan-events` records (events shared lib)
- [x] Implement domain + JPA entities + repositories (loan-api)
- [x] Implement controllers, DTOs, validation, state transitions (loan-api)
- [x] Implement transactional outbox writer and poller with advisory locks (poller; advisory lock TBD)
- [x] Configure Kafka topics (`loan-events`, `loan-events-dlq`) and producers
- [x] Implement loan-notification-service consumer and Loki logging
- [x] Integrate OpenFeature + Flagd for `manual-approval-enabled`
- [ ] Observability: OTel Java agent config, JSON logs, metrics, tracing to Alloy
- [x] Docker Compose for all services (Postgres, Redpanda, Flagd, Alloy, Loki, Tempo, Prometheus, Grafana, MinIO, apps)
- [ ] Unit tests (controllers, services, outbox, consumer) ≥80% coverage
- [ ] Integration/E2E with Testcontainers (full lifecycle, Awaitility)
- [ ] Mutation testing with PITest (≥80% business logic)
- [x] OpenAPI specs and Springdoc UI (placeholder spec added)
- [x] README with business context and runbook
- [x] CI workflow (build, unit/integration, mutation)
- [ ] GraalVM native profile and Dockerfiles
- [ ] Kubernetes manifests (ConfigMaps, Secrets, Deployments, Services)
- [ ] Final review: lint/format, docs, green CI

## Detailed Implementation Plan

### Architecture Overview
- Services: `loan-api` (REST + PostgreSQL + transactional outbox) and `loan-notification-service` (Kafka consumer -> Loki logs).
- Data flow: Requests -> `loan-api` domain -> persist `Loan` + `OutboxEvent` (same tx) -> outbox poller publishes to Redpanda topic `loan-events` -> notification service consumes -> JSON logs with trace correlation.
- Observability: OTel Java agent exports to Grafana Alloy (`OTLP http://alloy:4318`), metrics to Prometheus, logs to Loki, traces to Tempo; dashboards in Grafana.
- Feature flags: OpenFeature + Flagd with `manual-approval-enabled` boolean; threshold via `loan.approval.threshold`.

ASCII diagram
```
Client -> loan-api (REST) -> PostgreSQL (Loan, OutboxEvent)
                         \-> Outbox Poller -> Redpanda (loan-events) -> loan-notification-service -> Loki
OTel Java Agent -> Alloy -> Prometheus/Loki/Tempo -> Grafana
Flagd <-> OpenFeature (loan-api)
```

### Project Structure (Maven multi-module)
- `pom.xml` (parent: dependencyManagement, plugins, profiles incl. `native`)
- `loan-events/` (shared records: events, topic names)
- `loan-api/` (web, validation, jpa, kafka, openfeature, springdoc)
- `loan-notification-service/` (kafka consumer, logging)
- `docker/` (compose files, flags.json, grafana dashboards)
- `scripts/` (dev helpers: `compose-up.sh`, `wait-for.sh`)

### Technical Setup
- Maven deps: Spring Boot 4.0, `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-kafka`, OpenFeature, Springdoc, Testcontainers (junit, postgres, kafka), Awaitility, Mockito, PITest.
- Config: `application.yml` + profiles `dev`, `stg`, `prd`. Env vars: `PORT`, `DATABASE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOAN_APPROVAL_THRESHOLD`.
- DB schema:
  - `loan(id UUID PK, amount NUMERIC, term_months INT, interest_rate NUMERIC, remaining_balance NUMERIC, status TEXT, simulated_at TIMESTAMP, approved_at TIMESTAMP, contracted_at TIMESTAMP, disbursed_at TIMESTAMP, last_payment_at TIMESTAMP, customer_id TEXT)`
  - `outbox_event(id UUID PK, topic TEXT, event_type TEXT, payload JSONB, created_at TIMESTAMP, sent BOOLEAN DEFAULT FALSE)`
- Kafka: topics `loan-events` (replication 1, partitions 1 local), `loan-events-dlq`.
- Outbox poller: `@Scheduled(fixedDelayString = "${outbox.polling.interval:1000}")`, batch publish within transactional producer; advisory lock to serialize workers.

### Functional Breakdown (Endpoints)
- POST `/loans/simulate` -> simulate; if `manual-approval-enabled` and amount > threshold -> `PENDING_APPROVAL` + `LoanPendingApprovalEvent`; else auto-approve -> `APPROVED` + `LoanSimulatedEvent` + `LoanApprovedEvent`.
- POST `/loans/{id}/approve` or `/reject` -> validate state `PENDING_APPROVAL`; emit respective event.
- POST `/loans/{id}/contract` -> requires `APPROVED` -> set `CONTRACTED` + event.
- POST `/loans/{id}/disburse` -> requires `CONTRACTED` -> set `DISBURSED` + event.
- POST `/loans/{id}/pay` -> requires `DISBURSED`; apply amount, update `remainingBalance`; if 0 -> `PAID`; emit `LoanPaymentMadeEvent`.
- Validation: Jakarta annotations; map errors to 400; enforce state machines.

### Observability Plan
- OTel Java Agent enabled at runtime; JSON logs (Logback encoder) include traceId/spanId.
- Metrics: HTTP server, JVM, DB, Kafka; custom: `loan_approvals_total`, `loan_payments_total`.
- Dashboards: import Spring Boot dashboard, Kafka overview, and custom panel for loan metrics.
- Export to Alloy -> Prometheus/Loki/Tempo; Grafana at `http://localhost:3000`.

### Testing Plan
- Unit (JUnit5 + Mockito): controllers, services, state transitions, outbox writer, consumer.
- Integration (Spring Boot Test + Testcontainers: Postgres, Redpanda): repository tests; outbox poller end-to-end publish; notification consumer.
- E2E: full lifecycle via `TestRestTemplate` against embedded app containers, assert DB, Kafka, and logs presence; use Awaitility for async.
- Mutation: PITest on domain and state logic (target ≥80%).

### Documentation Plan
- OpenAPI 3: `src/main/resources/openapi.yaml` per service; serve UI for loan-api.
- README: personas, lifecycle, outbox rationale, local run with Docker Compose, observability links, troubleshooting.
- Use Context7 MCP for spec/code sync and to avoid outdated APIs.

### Deployment Plan
- Docker Compose: services (loan-api:8081, notification:8082), Postgres:5432, Redpanda:9092, Flagd:8013, Alloy:4318, Loki:3100, Tempo:3200, Prometheus:9090, Grafana:3000, MinIO:9000. Resource limits as specified.
- Production: GraalVM native images (`mvn -Pnative native:compile`), K8s manifests (Deployments, Services, ConfigMaps, Secrets). Flagd metrics scraped by Prometheus.

### Parallel Workflows (for two AIs)
- Workflow A (loan-api focus): domain, DB, controllers, validation, outbox, Kafka producer, OpenAPI, unit/integration tests, native image.
- Workflow B (infra/consumer/obs focus): notification consumer, Loki logging, Docker Compose, Grafana/Alloy/Tempo/Prometheus stack, Flagd, CI, dashboards, E2E tests, K8s manifests.
- Integration checkpoints: event schema contract (`loan-events`), topic/config, compose file, OTel endpoint, env naming.

### Estimated Effort (hours)
- Setup & scaffolding: 4
- Domain & persistence (loan-api): 6
- API + validation + state machine: 8
- Outbox + Kafka producer + DLQ: 8
- Notification consumer + logging: 4
- Observability wiring + dashboards: 6
- Testing (unit/integration/E2E): 12
- Mutation testing (PITest): 4
- Docker Compose + local run: 6
- Documentation (OpenAPI, README): 5
- CI + native images + K8s manifests: 6
- Total: ~69 hours (parallelizable between two workflows)

## Progress Log
- [x] 2025‑09‑11: Initialized TASKS.md and plan; awaiting prompt.
- [x] 2025‑09‑11: Received seed prompt; froze scope and constraints.
- [x] 2025‑09‑11: Drafted detailed plan and roadmap.
- [x] 2025‑09‑11: Scaffolded modules and core components; initial commit.
- [x] 2025‑09‑11: Added OpenFeature config, Dockerfiles, compose fixes; second commit.
- [x] 2025‑09‑11: Added JPA no-args constructors to entities to ensure runtime compatibility.
- [x] 2025‑09‑11: Configured Maven compiler for Java 21; added Flyway and V1 migration; switched JPA DDL to validate; build succeeded with `-DskipTests`.
- [x] 2025‑09‑11: Added JSON logging, Actuator, Prometheus registry; Prometheus scrape config in Compose.
- [x] 2025‑09‑11: Enabled Spring Boot fat JARs; added local profile with H2; guarded Outbox poller.
- [x] 2025‑09‑11: Added initial unit/web tests (service, controller, outbox poller); configured Surefire; tests green.
- [x] 2025‑09‑11: Added README and GitHub Actions CI.

## Current State
- Multi-module scaffold in place; compiles expected with Java 25 + Spring Boot 4.0.
- Docker Compose defined for local run; requires Docker and network to pull images.
- OpenAPI placeholder and basic observability hooks configured.

## Next Steps (Build & Run Verification)
- [x] Build: `mvn -DskipTests package` (fetch deps, compile, package)
- [ ] Quick run smoke for loan-api: start with local Postgres via Docker Compose or set a temporary in-memory profile (optional).
- [ ] If Docker available: `docker compose -f docker/docker-compose.yml up --build -d` and verify health endpoints.
- [ ] Log results here and commit a checkpoint.
- [x] 2025‑09‑11: Scaffolded modules and core components; initial commit.
- [x] 2025‑09‑11: Added OpenFeature config, Dockerfiles, compose fixes; second commit.

## Open Questions
- What is the exact prompt/problem statement?
- Target environment (Docker? Cloud runtime?) and data sources?
- Any performance/SLO requirements?
