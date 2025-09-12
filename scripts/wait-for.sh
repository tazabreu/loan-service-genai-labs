#!/usr/bin/env bash
# wait-for.sh — Wait for HTTP or TCP endpoints to become ready.
# Usage:
#   wait-for.sh http://localhost:8081/loans/healthz --timeout 60 --status 200
#   wait-for.sh tcp://localhost:5432 --timeout 60

set -euo pipefail

URL="${1:-}"
if [[ -z "$URL" ]]; then
  echo "Usage: $0 <http|tcp>://host[:port][/path] [--timeout N] [--status CODE]" >&2
  exit 2
fi

TIMEOUT=60
EXPECT_STATUS=""

shift || true
while [[ $# -gt 0 ]]; then
  case "$1" in
    --timeout)
      TIMEOUT="${2:-60}"; shift 2;;
    --status)
      EXPECT_STATUS="${2:-}"; shift 2;;
    *) echo "Unknown arg: $1" >&2; exit 2;;
  esac
done

scheme="${URL%%://*}"
rest="${URL#*://}"

deadline=$(( $(date +%s) + TIMEOUT ))

if [[ "$scheme" == "http" || "$scheme" == "https" ]]; then
  while :; do
    code=$(curl -s -o /dev/null -w "%{http_code}" "$URL" || true)
    if [[ -n "$EXPECT_STATUS" ]]; then
      if [[ "$code" == "$EXPECT_STATUS" ]]; then exit 0; fi
    else
      if [[ "$code" =~ ^2 ]]; then exit 0; fi
    fi
    if [[ $(date +%s) -ge $deadline ]]; then
      echo "Timeout waiting for $URL (last status: $code)" >&2; exit 1
    fi
    sleep 1
  done
elif [[ "$scheme" == "tcp" ]]; then
  host="${rest%%:*}"
  port="${rest##*:}"
  while :; do
    if (echo > /dev/tcp/$host/$port) >/dev/null 2>&1; then exit 0; fi
    if [[ $(date +%s) -ge $deadline ]]; then
      echo "Timeout waiting for tcp://$host:$port" >&2; exit 1
    fi
    sleep 1
  done
else
  echo "Unsupported scheme: $scheme (use http(s):// or tcp://)" >&2
  exit 2
fi

