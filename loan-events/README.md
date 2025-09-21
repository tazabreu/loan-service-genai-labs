# loan-events

Shared Java library that defines Kafka topic names and event payload contracts used across the loan system.

## Contents
| Artifact | Description |
| --- | --- |
| `LoanEvents` | Constants for primary topic (`loan-events`) and dead-letter topic (`loan-events-dlq`). |
| `LoanEventsPayloads` | Java `record` types describing payload schemas for each lifecycle event. |
| `LoanStatus` | Enumeration of canonical loan states mirrored by services and consumers. |

## Versioning Guidelines
- Treat payload records as public contracts—changes must be backwards compatible.
- When adding new fields, prefer optional semantics and default handling in consumers.
- If breaking changes are required, version topics (e.g., `loan-events-v2`) and update producers/consumers together.

## Usage
- Add this module as a dependency in other Maven modules within the repo (`<module>loan-events</module>` already included in the parent POM).
- Use the record types to serialize/deserialize JSON payloads. The producer (`loan-api`) serializes records to JSON via Jackson; consumers should mirror that configuration.
- Update tests (`loan-api` integration tests, notification service unit tests, `e2e` assertions) whenever payload structures change.

## Release Process
1. Modify record definitions.
2. Run `mvn -pl loan-events -am test`.
3. Update downstream services to consume the new shape.
4. Document payload changes in `docs/events.md` (see `docs/README.md`).
