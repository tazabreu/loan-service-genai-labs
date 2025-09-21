# Grafana Dashboards

Provisioned dashboards and panels for observing the loan system. Grafana mounts this directory via Docker Compose and auto-imports dashboards.

## Available Dashboards
| File | Description |
| --- | --- |
| `loan-system-overview.json` | End-to-end view of API latency, Kafka throughput, Postgres metrics, and key loan lifecycle counters. |

Dashboards are referenced by Grafana provisioning files in `docker/grafana/provisioning/dashboards`.

## Usage
1. Start the stack (`make up`).
2. Open Grafana at http://localhost:3000 (admin/admin).
3. Navigate to **Dashboards → Loan System Overview**.
4. Filter by `loan_id` or `customerId` using dashboard variables to inspect a specific flow.

## Editing Workflow
- Modify the dashboard in Grafana UI.
- Export JSON (Dashboard settings → JSON model → Download).
- Overwrite the corresponding file in this directory.
- Commit changes with a descriptive message (see `AGENTS.md` for commit guidelines).

## Creating Additional Dashboards
- Follow naming convention `kebab-case.json`.
- Update provisioning config (`docker/grafana/provisioning/dashboards/dashboards.yml`) if new dashboard should auto-import.
- Document new dashboards here with scope, metrics, and intended audience.
