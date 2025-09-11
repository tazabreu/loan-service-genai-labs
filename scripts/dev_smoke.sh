#!/usr/bin/env bash
# dev_smoke.sh — Bring up local stack with Docker Compose, exercise API, and show DB + logs.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker/docker-compose.yml"
WAIT_FOR="$ROOT_DIR/scripts/wait-for.sh"

have_cmd() { command -v "$1" >/dev/null 2>&1; }

if have_cmd docker && docker compose version >/dev/null 2>&1; then
  DC=(docker compose -f "$COMPOSE_FILE")
elif have_cmd docker-compose; then
  DC=(docker-compose -f "$COMPOSE_FILE")
else
  echo "Docker Compose not found." >&2
  exit 1
fi

echo "[dev] Bringing up services with Docker Compose…"
"${DC[@]}" up --build -d

echo "[dev] Waiting for dependencies (Postgres, Redpanda, API)…"
"$WAIT_FOR" tcp://localhost:5432 --timeout 60 || true
"$WAIT_FOR" tcp://localhost:9092 --timeout 60 || true
"$WAIT_FOR" http://localhost:8081/loans/healthz --timeout 120 --status 200

API_BASE="http://localhost:8081"

random_amount=$(( ( RANDOM % 9000 ) + 1000 ))
payload=$(cat <<JSON
{ "amount": $random_amount, "termMonths": 12, "interestRate": 0.12, "customerId": "local-smoke" }
JSON
)

echo "[dev] Simulating loan (amount=$random_amount)…"
resp=$(curl -s -X POST "$API_BASE/loans/simulate" -H 'Content-Type: application/json' -d "$payload")

loan_id=$(echo "$resp" | sed -n 's/.*"id"\s*:\s*"\([^"]\+\)".*/\1/p')
if [[ -z "${loan_id:-}" ]]; then
  echo "[dev] ERROR: Could not extract loan id from response: $resp" >&2
  exit 1
fi
echo "[dev] Loan id: $loan_id"

echo "[dev] Contracting loan…"
curl -s -X POST "$API_BASE/loans/$loan_id/contract" >/dev/null
echo "[dev] Disbursing loan…"
curl -s -X POST "$API_BASE/loans/$loan_id/disburse" >/dev/null
echo "[dev] Paying loan (amount=$random_amount)…"
curl -s -X POST "$API_BASE/loans/$loan_id/pay" -H 'Content-Type: application/json' -d "{\"amount\":$random_amount}" >/dev/null

sleep 2

echo "[dev] Querying Postgres for loan state…"
"${DC[@]}" exec -T postgres psql -U postgres -d loans -c "SELECT id, amount, remaining_balance, status FROM loan WHERE id = '$loan_id';"

echo "[dev] Outbox stats…"
"${DC[@]}" exec -T postgres psql -U postgres -d loans -c "SELECT COUNT(*) AS total, SUM(CASE WHEN sent THEN 1 ELSE 0 END) AS sent, SUM(CASE WHEN NOT sent THEN 1 ELSE 0 END) AS pending FROM outbox_event;"

echo "[dev] Notification service logs containing loan id…"
"${DC[@]}" logs --tail 200 loan-notification-service | grep -F "$loan_id" || true

echo "[dev] Done. Tear down with: ${DC[*]} down -v"

