import { exec } from 'child_process';
import { COMPOSE_FILE } from './env';

function execCmd(cmd: string, cwd = process.cwd()): Promise<{ stdout: string; stderr: string }> {
  return new Promise((resolve, reject) => {
    exec(cmd, { cwd, maxBuffer: 10 * 1024 * 1024 }, (err, stdout, stderr) => {
      if (err) return reject(err);
      resolve({ stdout, stderr });
    });
  });
}

// Reads logs for a compose service (e.g., loan-notification-service)
export async function getServiceLogs(
  service: string,
  { since = '5m' }: { since?: string } = {}
): Promise<string> {
  // Make path explicit relative to e2e/
  const composePath = COMPOSE_FILE;
  const { stdout } = await execCmd(
    `docker compose -f ${composePath} logs --no-color --since ${since} ${service}`,
    process.cwd()
  );
  return stdout || '';
}

export function logsContainLoanEvent(logs: string, loanId: string, uniqueField?: string): boolean {
  // Lines look like: "loan-notification-service-1  | 2024-.. INFO ... loan_event={...}"
  // We just check that the JSON blob contains the loanId and (optionally) a unique field by event type
  const needle = loanId;
  const fieldNeedle = uniqueField ? `"${uniqueField}":` : null;
  return logs.split('\n').some((line) => {
    if (!line.includes('loan_event=')) return false;
    if (!line.includes(needle)) return false;
    if (fieldNeedle && !line.includes(fieldNeedle)) return false;
    return true;
  });
}

