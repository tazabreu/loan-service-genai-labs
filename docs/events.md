# Loan Events Catalog

Reference for Kafka topics and payload schemas emitted by the loan system.

## Topics
| Topic | Producer | Consumers | Notes |
| --- | --- | --- | --- |
| `loan-events` | loan-api (outbox publisher) | loan-notification-service, analytics | Primary stream of lifecycle events. |
| `loan-events-dlq` | loan-api (error handler) | TBD | Reserved for poison-pill events. |

## Payloads
Payload definitions live in `loan-events/src/main/java/com/example/loan/events/model/LoanEventsPayloads.java` as Java `record` types.

| Event | Trigger | Fields |
| --- | --- | --- |
| `LoanSimulatedEvent` | `POST /loans/simulate` | `id`, `amount`, `termMonths`, `interestRate`, `customerId`, `simulatedAt`, `status`. |
| `LoanPendingApprovalEvent` | Flag-enforced manual approval path | `id`, `amount`, `customerId`, `at`. |
| `LoanApprovedEvent` | `POST /loans/{id}/approve` | `id`, `approvedBy`, `approvedAt`. |
| `LoanRejectedEvent` | `POST /loans/{id}/reject` | `id`, `rejectedBy`, `reason`, `rejectedAt`. |
| `LoanContractedEvent` | `POST /loans/{id}/contract` | `id`, `contractedAt`. |
| `LoanDisbursedEvent` | `POST /loans/{id}/disburse` | `id`, `disbursedAt`. |
| `LoanPaymentMadeEvent` | `POST /loans/{id}/pay` | `id`, `amount`, `remainingBalance`, `paidAt`. |

## Compatibility Checklist
- Bump consumer deserializers and tests when adding fields or events.
- Ensure outbox serialization (`loan-api`) remains backwards compatible; use Jackson `@JsonInclude` for optional fields if needed.
- Document changes here and link from PR descriptions.

## Open Questions / TODOs
- Define schema registry integration or Avro/JSON Schema if stricter contracts required.
- Clarify dead-letter queue handling and reprocessing strategy.
