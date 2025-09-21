type Numberish = string | number | undefined | null;

function readEnv(name: string, fallback?: string): string {
  const value = process.env[name];
  if (value && value.trim().length > 0) {
    return value.trim();
  }
  if (fallback === undefined) {
    throw new Error(`Missing required env var ${name}`);
  }
  return fallback;
}

function readNumber(name: string, fallback: Numberish): number {
  const raw = process.env[name];
  const value = raw !== undefined ? Number(raw) : Number(fallback ?? NaN);
  if (Number.isFinite(value) && value > 0) {
    return value;
  }
  if (fallback && Number(fallback) > 0) {
    return Number(fallback);
  }
  throw new Error(`Env var ${name} must be a positive number`);
}

export const API_BASE = readEnv('E2E_API_BASE', 'http://localhost:8081');
export const PG_URL = readEnv('E2E_PG_URL', 'postgres://postgres:postgres@localhost:5432/loans');
export const KAFKA_BROKERS = readEnv('E2E_KAFKA_BROKERS', 'localhost:19092').split(',').map((broker) => broker.trim());
// Path to the docker compose file relative to e2e-tests/ (used for reading service logs)
export const COMPOSE_FILE = readEnv('E2E_COMPOSE_FILE', '../docker/docker-compose.yml');

export const HTTP_TIMEOUT_MS = readNumber('E2E_HTTP_TIMEOUT_MS', 15000);
export const DEFAULT_WAIT_TIMEOUT_MS = readNumber('E2E_WAIT_TIMEOUT_MS', 30000);
export const DB_POLL_INTERVAL_MS = readNumber('E2E_DB_POLL_INTERVAL_MS', 250);
