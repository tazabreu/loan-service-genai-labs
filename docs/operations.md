# Operations Runbook

Rapid reference for deploying and supporting the loan platform.

## Deployments
- Build images with `mvn -DskipTests package` then `docker build` (both service Dockerfiles pull the fat jars).
- Publish the resulting images to your registry and roll out via Compose, Kubernetes, or your orchestrator of choice.
- Keep environment configuration in variables: database/Kafka URLs, OTEL exporter endpoint, feature flag location.

## Database Migrations
- Flyway runs on application startup; run `mvn -pl loan-api -am flyway:migrate` against the target database ahead of production cutovers.
- Rollbacks rely on forward fixes—prepare compensating migrations rather than dropping versions.

## Monitoring & Alerting
- Grafana dashboard `loan-system-overview` surfaces API latency, Kafka throughput, and outbox depth proxy metrics.
- Prometheus scrapes `/actuator/prometheus`; configure alert rules for elevated error rates and stalled outbox counters (`loan_outbox_publish_failures_total`).
- Tempo/Loki hold traces and logs—use trace IDs emitted by the services to correlate issues.

## Incident Handling
- **Kafka outage**: keep services running; events remain in the outbox. Restore broker availability, then confirm the outbox drains.
- **Postgres issues**: remediation requires database-level fixes; application pods restart once the connection is healthy.
- **Notification lag**: check consumer logs/metrics, ensure the Kafka group is balanced, and scale replicas if needed.
