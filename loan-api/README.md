# loan-api

Spring Boot REST service that owns loan lifecycle state, persists to Postgres, and publishes domain events via a transactional outbox.

## Responsibilities
- Expose loan lifecycle endpoints (`simulate`, `approve/reject`, `contract`, `disburse`, `pay`).
- Persist loan aggregates and outbox records (JPA + Flyway migrations).
- Publish Kafka events defined in `loan-events` after transactions commit.
- Surface actuator endpoints for health and Prometheus metrics.
- Enforce feature flags (OpenFeature + Flagd) such as `manual-approval-enabled`.

## Module Layout
| Path | Purpose |
| --- | --- |
| `src/main/java/com/example/loan/api/config` | Spring configuration (Kafka, OpenFeature, outbox scheduler). |
| `src/main/java/com/example/loan/api/web` | REST controllers and DTOs. |
| `src/main/java/com/example/loan/api/domain` | Aggregate root, services, and transactional logic. |
| `src/main/java/com/example/loan/api/persistence` | JPA repositories, outbox entities, schedulers. |
| `src/main/resources/application*.yml` | Profiles (`default`, `local`) and datasource configuration. |
| `src/main/resources/openapi.yaml` | API contract documentation. |
| `src/test/java` | Unit + integration tests mirroring the main package structure. |

## Configuration
Environment variables / application properties:
- `PORT` – HTTP port (default 8081).
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` – JDBC connection details.
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` – Kafka brokers (`redpanda:9092` inside Docker).
- `OTEL_EXPORTER_OTLP_ENDPOINT` – Send traces to Alloy/Tempo when set.
- `FLAGD_HOST`, `FLAGD_PORT` – Feature flag service location.
- `OUTBOX_POLL_INTERVAL` / `outbox.polling.interval` – Polling interval for the outbox scheduler (ms, default 1000).
- `LOAN_APPROVAL_THRESHOLD` – Amount above which manual approval is required when the flag is enabled (default 10000).

Profiles:
- `default` – Expects Postgres + Kafka + Flagd.
- `local` – Uses in-memory H2, disables outbox publishing, no Kafka connectivity (useful for offline dev).

## Running
```bash
# Package application (includes Flyway migrations and tests)
mvn -pl loan-api -am package

# Run against Docker stack (requires Postgres/Kafka up)
java -jar loan-api/target/loan-api-0.1.0-SNAPSHOT.jar

# Local profile (H2, no Kafka)
java -jar loan-api/target/loan-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```
Within Docker Compose the service is built from `loan-api/Dockerfile` and booted on port 8081.

## Testing
```bash
# Module tests (unit scope today)
mvn -pl loan-api -am test
```
Extend the suite with Spring/Testcontainers coverage as functionality grows.

## Integration Points
- **loan-events** – shares `LoanEvents` topic names and payload records; update both modules in lockstep.
- **Kafka** – outbox publisher sends serialized JSON payloads to `loan-events` topic.
- **Notification service** – consumes emitted events; adding new event types requires updates in both modules.
- **Observability** – `/actuator/health`, `/actuator/prometheus`, and structured logs forwarded via Docker stack.

## API Contract
OpenAPI spec: `src/main/resources/openapi.yaml`. Regenerate client stubs or documentation from this file when endpoints change. Update E2E tests (`e2e-tests/src/tests`) to cover new behaviour.

## Troubleshooting
- **Flyway migration failures**: confirm schema history table is empty when switching between local and dockerized databases.
- **Outbox not draining**: ensure the scheduler is enabled (`outbox.enabled=true`) and Kafka reachable.
- **Feature flag errors**: when developing locally with `local` profile, Flagd is skipped; for Docker, check `docker/flags/flags.json`.
