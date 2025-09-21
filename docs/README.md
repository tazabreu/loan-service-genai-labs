# Documentation Index

Curated references for architecture, event schemas, and operational playbooks. Expand these documents as the system evolves.

## Contents
- `architecture.md` – Service topology, component diagram, and data flow walkthrough.
- `events.md` – Kafka payload schemas, versioning decisions, and compatibility matrix.
- `operations.md` – Runbooks for common tasks (deployment, incident response, scaling). [TODO]

## Contribution Guidelines
- Keep diagrams (PlantUML, Mermaid, or image exports) under this directory; reference them from the root `README.md`.
- When changing service behaviour, update the relevant section here and cross-link from module-specific READMEs.
- Prefer smaller documents per topic rather than a single monolithic file.
