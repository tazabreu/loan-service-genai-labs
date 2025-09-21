# loan-notification-service

Spring Boot Kafka consumer that listens to loan lifecycle events and emits structured JSON logs for downstream notification channels.

## Responsibilities
- Consume `loan-events` topic produced by `loan-api`.
- Log each event as `loan_event=<payload>` for scraping by Promtail/Loki.
- Expose health checks and Prometheus metrics.

## Module Layout
| Path | Purpose |
| --- | --- |
| `src/main/java/com/example/loan/notification/LoanNotificationServiceApplication.java` | Spring Boot entrypoint. |
| `src/main/java/com/example/loan/notification/LoanEventsConsumer.java` | Kafka listener that logs payloads. |
| `src/main/resources/application.yml` | Service configuration (Kafka, logging, management endpoints). |
| `src/test/java` | Unit tests for consumer and configuration. |

## Configuration
Environment variables / properties:
- `PORT` – HTTP port (default 8082).
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` – Kafka cluster address.
- Set `spring.kafka.consumer.group-id` if you need a group other than the baked-in `loan-notifier`.
- `OTEL_EXPORTER_OTLP_ENDPOINT` – Send traces to Alloy/Tempo when present.

The service depends on the `loan-events` module for shared topic names and payload structures.

## Running
```bash
mvn -pl loan-notification-service -am package
java -jar loan-notification-service/target/loan-notification-service-0.1.0-SNAPSHOT.jar
```
Within Docker Compose the container is built from `loan-notification-service/Dockerfile` and exposed on port 8082.

## Testing
```bash
mvn -pl loan-notification-service -am test
```
Add Spring/Kafka tests as behaviour evolves; none are present yet.

## Log Format
Events are logged as structured key/value pairs to simplify Loki queries:
```
INFO  loan_event={"id":"…","eventType":"LoanDisbursedEvent",…}
```
In Grafana Explore, filter with `{app="loan-notification-service"} | logfmt | loan_event="<uuid>"` to trace a single loan.

## Extending
- Add new consumer logic or sinks by subscribing to `LoanEventsPayloads` records from `loan-events`.
- For durable processing, consider writing to a database or invoking downstream APIs instead of logging.
- Update `docker/docker-compose.yml` if new env vars or ports are required.
