# Repository Guidelines

## Project Structure & Module Organization
- `src/` – service code (domain, API, adapters).
- `tests/` – unit/integration tests mirror `src/` paths.
- `scripts/` – one-off dev/ops scripts (idempotent, documented headers).
- `infra/` – IaC/compose files if used.
- `.specstory/` – working notes/history currently in this repo; keep non-code artifacts here.

## Build, Test, and Development Commands
Use what matches the toolchain present in the repo:
- Python: `python -m venv .venv && source .venv/bin/activate`, `pip install -e .[dev]`, `pytest -q`.
- Node/TS: `npm ci`, `npm run build`, `npm test --silent`, `npm run dev`.
- Make (if `Makefile` exists): `make setup`, `make test`, `make run`.
Prefer adding thin `make` targets to standardize local workflows.

## Coding Style & Naming Conventions
- Languages: prefer TypeScript or Python for new modules.
- Style: Python uses Black (88 cols) + isort + Ruff; TS uses ESLint (typescript-eslint) + Prettier.
- Naming: directories and files `kebab-case` (TS) or `snake_case` (Py); classes `PascalCase`; functions/vars `camelCase` (TS) or `snake_case` (Py).
- Keep modules small; one responsibility per file.

## Testing Guidelines
- Frameworks: `pytest` for Python, `jest`/`vitest` for TS.
- Structure: tests mirror `src/` with `test_*.py` or `*.spec.ts`.
- Coverage: target ≥ 80%; add regression tests for fixed bugs.
- Run: `pytest -q` or `npm test`; add `:watch` scripts for fast feedback.

## Commit & Pull Request Guidelines
- Commits: use Conventional Commits (e.g., `feat: add loan scoring rules`).
- Scope small, message body explains why and how; reference issues (`Closes #123`).
- PRs: clear description, screenshots or logs when UX/CLI changes, checklist of impacts (tests, docs, config). Ensure CI green and no lint errors.

### Commit Format with Gitmoji
- Use Conventional Commits + gitmoji prefix. Examples:
  - `✨ feat(api): add loan simulation endpoint`
  - `🐛 fix(outbox): handle advisory lock contention`
  - `🧪 test(e2e): full lifecycle with Testcontainers`
  - `📝 docs: add OpenAPI spec and README sections`
  - `🏗️ build(ci): add Maven + PITest workflow`
- Keep subject ≤72 chars; body explains rationale and impacts.

### Git Identity
- Use this repo-local identity for commits:
  - `git config user.name "Tassio Abreu (Codex Agent)"`
  - `git config user.email "tazabreu@gmail.com"`

## 12-Factor App Guidelines
- Config via env vars: no secrets in repo. Ship `.env.example` (no values). Read `PORT`, `DATABASE_URL`, `REDIS_URL`; use `dotenv` only in dev.
- Dependencies declared/isolated: Python uses `pyproject.toml` + lock (e.g., pip-tools/uv); Node uses `package-lock.json`. Install with `pip install -e .[prod]` or `npm ci`.
- Backing services as attached resources: treat DB/cache/queue as URLs; swap via env without code changes.
- Stateless processes: no local disk writes (except ephemeral `/tmp`). Persist to DB/object storage; scale via process concurrency.
- Port binding: web services listen on `PORT` and self-advertise readiness in logs.
- Logs as event streams: write structured (JSON) logs to stdout/stderr; do not write log files.
- Disposability: fast start/stop; handle `SIGTERM` gracefully; expose `/healthz` and `/readyz` where applicable.
- Dev/prod parity & releases: one Dockerfile; `make build` produces the same artifact locally/CI. Separate build/release/run; run migrations in release (`make migrate`/`npm run migrate`).

## Agent-Specific Instructions
- Stay within this repo’s scope; follow these conventions for any files you touch.
- Prefer minimal, focused changes; update docs/tests alongside code.
- Do not add licenses or new tooling without request. Use `rg` for search and `apply_patch` for edits.
