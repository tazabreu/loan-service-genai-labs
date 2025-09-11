export const API_BASE = process.env.E2E_API_BASE || 'http://localhost:8081';
export const PG_URL = process.env.E2E_PG_URL || 'postgres://postgres:postgres@localhost:5432/loans';
export const KAFKA_BROKERS = (process.env.E2E_KAFKA_BROKERS || 'localhost:19092').split(',');
// Path to the docker compose file relative to e2e/ (used for reading service logs)
export const COMPOSE_FILE = process.env.E2E_COMPOSE_FILE || '../docker/docker-compose.yml';

