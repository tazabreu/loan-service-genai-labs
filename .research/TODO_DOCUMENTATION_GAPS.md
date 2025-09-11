### Documentation Gaps and Enhancements TODO

This is a backlog of documentation improvements for the `loan-service-genai-labs` repository. Use it to plan and track documentation work across modules.

---

## API and Events

- **OpenAPI generated from code (loan-api)**
  - [ ] Generate OpenAPI from Spring annotations using `springdoc-openapi`.
  - [ ] Serve Swagger UI at runtime.
  - [ ] Export spec at build time to `loan-api/src/main/resources/openapi.yaml` (replace the manually maintained file).
  - [ ] Add CI step to fail when generated spec is out of date.
  - [ ] Provide examples and error schemas (Problem Details) in the spec.

- **AsyncAPI for events (Kafka)**
  - [ ] Document topics, payloads, headers, and versioning policy.
  - [ ] Keep source-of-truth in `loan-events` (co-located with shared models).
  - [ ] Choose approach: annotations (e.g., Springwolf) vs. hand-authored AsyncAPI YAML with CI validation.
  - [ ] Publish rendered HTML to docs site (if we add one).

- **JSON Schemas for events**
  - [ ] Generate or hand-author JSON Schema for each event in `loan-events`.
  - [ ] Validate schemas in tests (producer/consumer) and enforce backward-compatibility rules in CI.

- **API usage examples**
  - [ ] Add `curl`/HTTPie examples for common flows.
  - [ ] Document error codes and Problem+JSON format.

---

## Architecture and Domain

- **Sequence diagrams (Mermaid)**
  - [ ] Create diagrams for core flows (e.g., "Apply → Persist → Outbox → Kafka → Notification").
  - [ ] Highlight business rules at each step (eligibility, status transitions, retries).
  - [ ] Store in `docs/diagrams/` and embed in module READMEs.

- **C4 diagrams**
  - [ ] Context, Container, and Component diagrams (C4) for modules.
  - [ ] Automate rendering (PlantUML/Mermaid) and commit SVGs.

- **Domain glossary and business rules**
  - [ ] Define domain terms, statuses, events, and invariants.
  - [ ] Add decision tables for eligibility and limits.

---

## Developer Onboarding

- **Top-level README**
  - [ ] Add purpose, high-level architecture map, quick start, common commands, and module map.
  - [ ] Link to deeper docs per module.

- **Per-module READMEs**
  - [ ] `loan-api`: endpoints, errors, how to run, dependencies, troubleshooting.
  - [ ] `loan-notification-service`: consumed/produced topics, retry/backoff, troubleshooting.
  - [ ] `loan-events`: event models, versioning, JSON schemas.
  - [ ] `e2e-tests`: how to run locally/CI, coverage of flows, env requirements.

- **Standardized dev workflow**
  - [ ] Add `Makefile` targets: `setup`, `run`, `test`, `e2e`, `docs`.
  - [ ] Document environment variables via `.env.example` (no values).
  - [ ] Add `CONTRIBUTING.md` (Conventional Commits + gitmoji, branch strategy, code style).
  - [ ] Introduce ADRs under `docs/adr/` (decisions on outbox, Kafka, module split, test strategy).

---

## Testing and Quality

- **Test strategy**
  - [ ] Document pyramid: unit, integration, e2e; location and conventions.
  - [ ] Data management strategy for tests.

- **Contract testing**
  - [ ] Producer/consumer contracts (Pact or Spring Cloud Contract) for `loan-api` ↔ `loan-notification-service`.
  - [ ] Wire contracts into CI to prevent breaking changes.

- **Coverage reporting**
  - [ ] Enable JaCoCo reports and add a README badge.
  - [ ] Set and enforce minimum thresholds.

- **Spec verification**
  - [ ] Validate OpenAPI against controller responses.
  - [ ] Validate AsyncAPI/JSON Schemas against produced/consumed messages.

---

## Ops, Reliability, and Observability

- **Runbook**
  - [ ] Startup/shutdown, migrations, replaying outbox, handling common failures.

- **Health and readiness**
  - [ ] Document `/healthz` and readiness checks and their meaning.

- **Metrics and logs**
  - [ ] List emitted metrics and log formats; provide sample queries and dashboards.
  - [ ] Link to `dashboards/` and include screenshots.

- **Tracing**
  - [ ] Describe key spans and attributes across services.

- **SLOs and alerts**
  - [ ] Define initial SLIs and propose alert thresholds (e.g., outbox lag, consumer lag).

---

## Data and Persistence

- **DB schema and ER diagram**
  - [ ] Generate ER diagram (e.g., SchemaSpy) from the current schema.
  - [ ] Store outputs in `docs/db/` with a "how to regenerate" note.

- **Migration policy**
  - [ ] Document Flyway naming conventions, idempotency, and release-time migration steps.

---

## Release and Governance

- **Changelog**
  - [ ] `CHANGELOG.md` automated from Conventional Commits and tags.

- **Versioning policy**
  - [ ] Define module and event schema versioning; document compatibility guarantees.

- **Security**
  - [ ] Document dependency update cadence, scanning, and secret handling.

---

## Documentation System and Automation

- **Docs site**
  - [ ] Add `docs/` site (MkDocs or Docusaurus) built in CI and published to Pages.

- **Diagrams-as-code pipeline**
  - [ ] Render Mermaid/PlantUML to SVG/PNG in CI.
  - [ ] Fail CI on broken image links or unresolved includes.

- **Doc drift checks**
  - [ ] CI ensures OpenAPI/AsyncAPI and ER diagram are up to date.

---

## Module-Specific Quick Wins

- **loan-api**
  - [ ] Generate and serve OpenAPI + Swagger UI.
  - [ ] Replace manual `loan-api/src/main/resources/openapi.yaml` with generated spec.
  - [ ] Document error handling and common response shapes.
  - [ ] Add a component diagram.

- **loan-notification-service**
  - [ ] AsyncAPI for consumed/produced topics (payloads, headers, retries, DLT strategy).
  - [ ] Document consumer lag metrics and alerting.

- **loan-events**
  - [ ] Define JSON Schemas for shared event models.
  - [ ] Establish versioning and compatibility guide.

- **e2e-tests**
  - [ ] README with how to run locally/CI and env requirements.
  - [ ] Sequence diagram mirroring the full lifecycle test.

---

## Proposed First PR (suggested starter set)

- [ ] Generate OpenAPI in `loan-api` with CI drift check and Swagger UI.
- [ ] Add AsyncAPI skeletons for topics in `loan-events` with CI validation.
- [ ] Create `docs/diagrams/` with two Mermaid sequence diagrams for core flows.
- [ ] Add per-module READMEs and `.env.example` at root.
- [ ] Introduce a `Makefile` and update the README quick-start with standardized commands.

---

## Notes

- Keep docs close to code and automate generation wherever possible.
- Use diagrams-as-code for easy review in PRs.
- Prefer small, incremental doc PRs to keep review scope manageable.



