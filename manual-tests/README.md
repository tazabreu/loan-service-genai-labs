# Manual HTTP Checks

Curated `.http` files that let you exercise the loan system manually using the VS Code REST Client extension, IntelliJ HTTP client, or any editor that understands the HTTP file format.

## Usage
1. Install the [REST Client extension](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) or open the files with an IDE that supports `.http` requests.
2. Open one of the files in this directory and click **Send Request** (VS Code) or use the IDE runner.
3. Ensure the Docker stack is running (`make up`) or the service binary is alive before firing the requests.
4. Modify the variables at the top of the file (e.g., `@apiBase`) to match non-default ports if needed.

## Available Tools
- `loan-lifecycle.http` – end-to-end flow covering simulate → approve → contract → disburse → pay. Captures the loan identifier from the first request and reuses it in subsequent calls.
- `platform-health.http` – readiness checks for API, notification service, Kafka, and Prometheus.
- `loan-lifecycle-interactive.mjs` – guided CLI walkthrough that fires the same sequence with friendly prompts, carries the loan id between steps, and pauses between actions. Now supports iterative and automatic full repayment (liquidação completa) to simulate loan exhaustion.

### Running the interactive walkthrough
```bash
node manual-tests/loan-lifecycle-interactive.mjs
```

You can override the API endpoint by exporting `API_BASE=http://your-host:port` before running the script. The CLI will prompt for loan parameters, confirm each step, and print human-readable snapshots after every call.

During the repayment phase you can choose:
- Single payment (one-off)
- Iterative payments (press Enter between payments) until balance is zero
- Automatic liquidation (auto-pay in loops until fully paid)

Feel free to add more `.http` scenarios that aid debugging (e.g., DLQ inspection, feature flag overrides) and update this README accordingly.
