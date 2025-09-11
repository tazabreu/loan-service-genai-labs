# Loan Management System (Spring Boot)

Two microservices demonstrating a loan lifecycle with transactional outbox and event-driven notifications.

## Modules
- `loan-api`: REST API + JPA + transactional outbox -> publishes to Kafka/Redpanda
- `loan-notification-service`: Kafka consumer -> structured JSON logs
- `loan-events`: Shared event records and constants

## Build
```bash
mvn -DskipTests package
```

## Run (local, no Docker)
```bash
java -jar loan-api/target/loan-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```
- H2 in-memory DB, Flyway disabled, Kafka outbox disabled

Smoke test
```bash
curl -s http://localhost:8081/loans/healthz
curl -s -X POST http://localhost:8081/loans/simulate \
  -H 'Content-Type: application/json' \
  -d '{"amount":1000,"termMonths":12,"interestRate":0.12,"customerId":"c-1"}'
```

## Run (Docker Compose)
```bash
docker compose -f docker/docker-compose.yml up --build -d
```
- API: http://localhost:8081
- Notification: http://localhost:8082
- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090

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

