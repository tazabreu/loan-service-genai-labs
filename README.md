# Loan Management System (Spring Boot)

A demo-scale loan lifecycle platform built as three Spring Boot microservices with Kafka-backed events, end-to-end TypeScript tests, and an observability stack (Grafana/Prometheus/Tempo/Loki).

## System Overview
- **loan-api** – customer-facing REST API, transactional outbox, publishes Kafka loan events.
- **loan-notification-service** – Kafka consumer that renders structured JSON notifications.
- **loan-events** – shared event contracts and constants used across services.
- **Observability stack** – Docker Compose bundle with Grafana, Prometheus, Tempo, Loki, Alloy, and Flagd.

Architecture diagram: `docs/architecture.md`.

See service-specific docs under:
- `loan-api/README.md`
- `loan-notification-service/README.md`
- `loan-events/README.md`
- `docker/README.md`
- `e2e-tests/README.md`
- `dashboards/README.md`
- `manual-tests/README.md`

## Prerequisites
| Tool | Version | Notes |
| --- | --- | --- |
| Java | 17+ | Build and run Spring Boot services |
| Maven | 3.9+ | Multi-module build |
| Docker & Docker Compose | Latest | Full-stack orchestration |
| Node.js | 18+ | TypeScript E2E tests |
| pnpm | 8+ (via Corepack) | `corepack enable pnpm` if not already available |
| Make | Optional | Convenience wrapper around commands |

Environment variables (set in shell or `.env` files when supported):
- `PORT` – service HTTP port; defaults per module (8081/8082).
- `DATABASE_URL` – JDBC URL for Postgres when running outside Docker.
- `KAFKA_BOOTSTRAP_SERVERS` – Kafka brokers (e.g., `localhost:19092`).
- `OTEL_EXPORTER_OTLP_ENDPOINT` – OpenTelemetry collector URL (Tempo/Alloy).
- `FLAGD_HOST` / `FLAGD_PORT` – feature flag service, defaults baked into Docker Compose.

## Quickstart
### Full Stack via Docker Compose
```bash
# Build all services
mvn -DskipTests package

# Start infrastructure + services
make up              # docker compose up --build -d

# Tear everything down (removes volumes)
make down
```

Services exposed locally:
- API: http://localhost:8081
- Notification service: http://localhost:8082
- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090
- Kafka (external listener): localhost:19092

### Service-Only Development (no Docker)
```bash
# Build once
mvn -pl loan-api -am package

# Run loan-api with local profile (H2, disabled outbox)
java -jar loan-api/target/loan-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```
Provides a self-contained H2-backed API for quick manual testing. Pair with curl/Postman requests and feature flags disabled.

## Testing Matrix
| Scope | Command | Notes |
| --- | --- | --- |
| All modules | `mvn test` | Runs unit + integration tests across Maven reactor |
| loan-api only | `mvn -pl loan-api -am test` | Runs the module test suite (currently unit-level only) |
| TypeScript E2E | `make e2e` | Spins up Docker stack, waits for readiness, runs Jest specs, tears down |
| E2E (keep stack) | `make e2e-ts-keep` | Leaves compose services running for manual debugging |
| Manual API checks | see `manual-tests/README.md` | Fire curated HTTP requests with VS Code REST client or similar |

Refer to `e2e-tests/README.md` for configuration overrides (e.g., `E2E_COMPOSE_FILE`).

## Loan Lifecycle
Loan applications move through deterministic state transitions driven by REST calls and Kafka events:
1. `simulate` – calculate preliminary offer; optional feature flag gating manual approval (`manual-approval-enabled`).
2. `approve` / `reject` – finalize decision.
3. `contract` – commit loan terms and persist to Postgres; outbox event created.
4. `disburse` – mark funds released; outbox event emitted.
5. `pay` – apply repayment, update balances, emit notification event.

The transactional outbox guarantees Kafka publication after database commits. Refer to `loan-events/README.md` for schemas and versioning strategy.

## Observability Pipeline
- **Traces**: Java agent auto-instrumentation exports OTLP data to Alloy → Tempo.
- **Metrics**: Spring Boot Actuator `/actuator/prometheus` scraped by Prometheus → Grafana dashboards.
- **Logs**: JSON logs shipped by Promtail to Loki; Grafana includes starter dashboard (`dashboards/loan-system-overview.json`).

Additional detail in `docker/README.md` and `dashboards/README.md`.

## Feature Flags
Flagd supplies feature toggles consumed via OpenFeature. Current flags:
- `manual-approval-enabled` – when true, simulation may require manual approval path.

Override via Docker Compose env or direct Flagd API; see `docker/flags` for definitions.

## Troubleshooting
- **Docker compose fails on first run**: ensure ports 8081/8082/3000/9090 are free and Docker engine is running.
- **Kafka connectivity errors**: check `docker-compose.yml` external listener (`19092`) and update `KAFKA_BOOTSTRAP_SERVERS` accordingly.
- **E2E tests time out**: run `make e2e-ts-keep`, inspect logs via `make logs`, verify services are healthy (`/actuator/health`).
- **Observability stack empty**: confirm Alloy service healthy and environment variable `OTEL_EXPORTER_OTLP_ENDPOINT` set for services.

## Contributing & Next Steps
- Follow repository conventions in `AGENTS.md` (commit format, tooling expectations).
- Prefer adding or updating docs in the corresponding folder README when behaviour changes.
- Open an issue/PR with any architecture diagrams or runbooks—start with stubs under `docs/`.

For deeper dives, explore `docs/` (architecture, event schemas, runbooks – in progress).
