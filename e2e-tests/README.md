# End-to-End Tests

TypeScript + Jest black-box tests that exercise the full loan lifecycle against the dockerized stack.

## Structure
| Path | Purpose |
| --- | --- |
| `src/tests` | BDD-style specs spanning lifecycle happy path, manual rejection, idempotency, observability, and negative/partial flows. |
| `src/utils/http.ts` | Typed Axios client plus convenience wrappers for core loan commands. |
| `src/utils/db.ts` | Postgres pool lifecycle + polling helpers with descriptive timeouts. |
| `src/utils/kafka.ts` | Kafka consumers/probes with automatic teardown guards. |
| `src/utils/scenario.ts` | Deterministic fixture builders for simulations and customer IDs. |
| `src/utils/harness.ts` | High-level helpers to bootstrap approved/disbursed/pending loans. |
| `jest.config.ts` | Jest configuration (ts-jest, node environment, coverage thresholds). |
| `.env.example` | Sample configuration consumed by test harness. |

## Prerequisites
- Node.js 18+
- pnpm (`corepack enable pnpm`)
- Docker & Docker Compose
- Access to repo root `docker/docker-compose.yml`

Install dependencies:
```bash
pnpm install --frozen-lockfile
```

## Running Tests
```bash
# Spin up stack, wait for readiness, run tests, tear down
pnpm run e2e:local

# Keep stack running after tests for debugging
pnpm run e2e:local:keep

# From repo root using Makefile wrappers
make e2e
make e2e-ts-keep

# Quality gates
pnpm lint
pnpm run test:coverage
```

`pnpm run e2e:wait` depends on `wait-on` to verify API (`/actuator/health`), Postgres, and Kafka readiness. Logs stream to stdout; use `make logs` in another terminal for live diagnostics.

> `pnpm run e2e:local` finishes with `docker compose ... down -v`, which drops Postgres/Kafka volumes.

## Configuration
Duplicate `.env.example` to `.env` and adjust as needed:
- `E2E_API_BASE` – API base URL (default `http://localhost:8081`).
- `E2E_PG_URL` – Postgres connection string for assertions.
- `E2E_KAFKA_BROKERS` – Kafka bootstrap servers (`localhost:19092`).
- `E2E_COMPOSE_FILE` – Path to Docker Compose manifest (defaults to `../docker/docker-compose.yml`).
- `E2E_HTTP_TIMEOUT_MS` – Axios timeout for API calls (defaults to `15000`).
- `E2E_WAIT_TIMEOUT_MS` – Default polling timeout for DB/Kafka assertions (defaults to `30000`).
- `E2E_DB_POLL_INTERVAL_MS` – Poll interval for DB checks (defaults to `250`).

Override variables per run, for example: `E2E_API_BASE=http://localhost:18081 pnpm run e2e:local`.

## Adding New Scenarios
1. Start from the existing describe blocks under `src/tests` (e.g., `contract_disburse_pay.e2e.spec.ts`).
2. Bootstrap data via the harness helpers (`createApprovedLoan`, `createDisbursedLoan`, `createPendingApprovalLoan`) to keep setup deterministic.
3. Use `loanClient` for API calls, `captureTopicSinceNow` for Kafka expectations, and `waitForLoanStatus` / `waitForRemainingBalance` for DB assertions.
4. Focus on user-observable behaviour—assert HTTP status, DB state, Kafka payloads, and service logs rather than implementation details.
5. Extend observability checks as dashboards evolve; the suite now exercises health, metrics, manual rejection, idempotency, and partial/overpayment guardrails.

## Troubleshooting
- **Stack fails to start**: run `docker compose -f ../docker/docker-compose.yml logs -f` to inspect services.
- **Kafka assertions fail**: ensure topic `loan-events` exists and Redpanda container is healthy.
- **Postgres connection refused**: confirm ports 5432/19092 free on host or adjust `.env` overrides.
- **Tests fail instantly**: start the Docker stack via `pnpm run e2e:up` (or `make e2e-up`) before running the suite.
- **Coverage threshold tripped**: run `pnpm run test:coverage` for a local report; global thresholds (75% statements/lines, 65% branches) apply when `CI=true` or `E2E_COVERAGE=true`.
