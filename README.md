# Loan Management System (Spring Boot)

Two microservices demonstrating a loan lifecycle with transactional outbox and event-driven notifications.

## Modules
- `loan-api`: REST API + JPA + transactional outbox -> publishes to Kafka/Redpanda
- `loan-notification-service`: Kafka consumer -> structured JSON logs
- `loan-events`: Shared event records and constants

## Quickstart
Build, run via Docker Compose, smoke test, and tear down.

```bash
# Build all modules
mvn -DskipTests package

# Bring up stack (or use: make up)
docker compose -f docker/docker-compose.yml up --build -d

# One-shot smoke (or use: make smoke)
bash scripts/dev_smoke.sh

# Tear down (or use: make down)
docker compose -f docker/docker-compose.yml down -v
```

- API: http://localhost:8081
- Notification: http://localhost:8082
- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090
 - Kafka (outside listener): localhost:19092

Observability
- Traces: OpenTelemetry Java agent auto-instrumentation exports to Alloy (OTLP), then to Tempo
- Metrics: Spring Boot Actuator Prometheus endpoint scraped by Prometheus
- Logs: JSON container logs scraped by Promtail into Loki
- Grafana is pre-provisioned with Prometheus/Loki/Tempo datasources and a starter dashboard
  - Login: admin / admin (default)
  - Dashboard: Loan System Overview

### Local Smoke Test
Runs simulate → contract → disburse → pay, then prints DB rows and consumer logs.
```bash
bash scripts/dev_smoke.sh
# or: make smoke
```
Outputs:
- Loan id, final status/balances from Postgres
- Outbox sent/pending counts and sample rows
- loan-notification-service logs filtered by loan id

## E2E (TypeScript)
Run end-to-end tests (Jest + TS) against the Docker stack.
```bash
cd e2e
npm ci
npm run e2e:local     # up + wait + tests + down
# or, keep stack running:
npm run e2e:local:keep
# Makefile aliases from repo root:
make e2e            # runs the TS e2e (up+wait+tests+down)
make e2e-ts-keep    # keeps the stack up after tests
```
Defaults:
- API `http://localhost:8081` | Postgres `localhost:5432` | Kafka `localhost:19092`
- Configure via `e2e/.env` (see `e2e/.env.example`)

Test style
- Granular BDD-style cases under an “Epic: Loan lifecycle”:
  - Given calling an API endpoint (simulate/contract/disburse/pay)
  - Then database state matches expected status/balance
  - Then a Kafka event was produced (by unique event field)
  - Then the notification service logged the event (`loan_event=`)
  
Environment overrides
- `E2E_COMPOSE_FILE` can point the tests to a different docker compose file for reading service logs.

## Dev (no Docker)
```bash
java -jar loan-api/target/loan-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```
- H2 + Flyway disabled + outbox disabled; for quick manual curls.

## Make Targets
- `make build` build jars
- `make up` start compose
- `make smoke` run local smoke
- `make logs` tail compose logs
- `make down` stop and clean

Observability
- JSON logs via Logback -> ready for Loki
- Metrics at `/actuator/prometheus` -> Prometheus scrape configured
- Traces: provide Java agent and set `OTEL_EXPORTER_OTLP_ENDPOINT`

## Tests
```bash
mvn test
# or module only
mvn -pl loan-api -am test
```

## Endpoints (loan-api)
- `POST /loans/simulate`
- `POST /loans/{id}/approve`
- `POST /loans/{id}/reject`
- `POST /loans/{id}/contract`
- `POST /loans/{id}/disburse`
- `POST /loans/{id}/pay`
- `GET /loans/healthz`

## Feature Flags
- OpenFeature + Flagd: `manual-approval-enabled` boolean influences simulation outcome.
Why TS e2e?
- Canonical black-box tests covering API → DB → Kafka → logs
- Java module-level integration tests remain for focused domain/outbox behavior
