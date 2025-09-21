# Docker Stack

Docker Compose bundle that provisions application services, backing stores, feature flag service, and observability tooling for the loan system.

## Services
| Service | Image / Build | Ports | Notes |
| --- | --- | --- | --- |
| postgres | `postgres:16` | 5432 | Primary database for `loan-api`. Credentials: `postgres` / `postgres`. |
| redpanda | `redpandadata/redpanda` | 9092, 19092 | Kafka-compatible broker; 19092 exposes host listener. |
| flagd | `ghcr.io/open-feature/flagd` | 8013 | Serves feature flags from `./flags/flags.json`. |
| alloy | `grafana/alloy` | 4317, 4318, 12345 | OpenTelemetry collector for traces. |
| loki | `grafana/loki` | 3100 | Logs aggregation. |
| promtail | `grafana/promtail` | — | Scrapes Docker logs and ships to Loki. Requires Docker socket mount. |
| tempo | `grafana/tempo` | 3200 | Distributed tracing storage backend. |
| prometheus | `prom/prometheus` | 9090 | Scrapes metrics from Spring Boot services. Configured via `./prometheus.yml`. |
| grafana | `grafana/grafana` | 3000 | Provisioned datasources/dashboards. Credentials `admin`/`admin`. |
| loan-api | Built from `loan-api/Dockerfile` | 8081 | REST API service. |
| loan-notification-service | Built from `loan-notification-service/Dockerfile` | 8082 | Kafka consumer and notifier. |

## Usage
```bash
# Build and start in background
make up

# Tail logs
make logs

# Stop and remove volumes (Postgres data, Kafka topics, Loki logs)
make down
```

`make up` delegates to `docker compose -f docker/docker-compose.yml up --build -d`. Adjust environment variables before running to customize ports or OTEL endpoints.

## Configuration Files
- `flags/flags.json` – Flag definitions for Flagd (`manual-approval-enabled`).
- `alloy/config.alloy` – Receivers/exporters for OTEL traces.
- `grafana/provisioning` – Datasource and dashboard provisioning (mounts `../dashboards`).
- `prometheus.yml` – Scrape config targeting service actuator endpoints.
- `promtail-config.yml` – Log scrape targets (Docker containers, file positions stored in container volume).

## Customization Tips
- **Swap Kafka**: Point `SPRING_KAFKA_BOOTSTRAP_SERVERS` to external brokers or replace Redpanda service. Update `E2E_KAFKA_BROKERS` accordingly.
- **Disable observability for lightweight runs**: comment out `alloy`, `loki`, `promtail`, `tempo`, `prometheus`, and `grafana` services.
- **Add new services**: extend `docker-compose.yml` and ensure E2E `.env` references align.
- **Flag overrides**: copy `flags.json` and mount alternative file via `FLAGD_URI` or volume override.

## Health Checks
- `http://localhost:8081/actuator/health` – loan-api health.
- `http://localhost:8082/actuator/health` – notification service health.
- `http://localhost:3000` – Grafana UI.
- `http://localhost:9090` – Prometheus targets page.

Refer to `dashboards/README.md` for visualization details and `manual-tests/README.md` for guided API checks.
