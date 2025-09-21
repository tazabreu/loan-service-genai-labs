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
  { since = '10m' }: { since?: string } = {}
): Promise<string> {
  // Make path explicit relative to e2e-tests/
  const composePath = COMPOSE_FILE;
  const { stdout } = await execCmd(
    `docker compose -f ${composePath} logs --no-color --since ${since} ${service}`,
    process.cwd()
  );
  return stdout || '';
}

// Enhanced version with retry logic for better reliability
export async function getServiceLogsWithRetry(
  service: string,
  loanId: string,
  uniqueField: string,
  { since = '10m', maxRetries = 3, retryDelay = 1000 }: { since?: string; maxRetries?: number; retryDelay?: number } = {}
): Promise<string> {
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    const logs = await getServiceLogs(service, { since });
    if (logsContainLoanEvent(logs, loanId, uniqueField)) {
      return logs;
    }
    if (attempt < maxRetries) {
      await new Promise(resolve => setTimeout(resolve, retryDelay));
    }
  }
  return await getServiceLogs(service, { since });
}

export function logsContainLoanEvent(logs: string, loanId: string, uniqueField?: string, debug = false): boolean {
  const lines = logs.split('\n');
  const fieldNeedles = uniqueField
    ? [`"${uniqueField}":`, `\\"${uniqueField}\\"`]
    : [];

  if (debug) {
    console.log(`[DEBUG] Searching for loanId: ${loanId}, field: ${uniqueField || 'any'}`);
    console.log(`[DEBUG] Total log lines: ${lines.length}`);
    console.log(`[DEBUG] Lines with loan_event: ${lines.filter(l => l.includes('loan_event=')).length}`);
  }

  return lines.some((originalLine) => {
    const line = originalLine.trim();
    if (line.length === 0) return false;

    let parsed: unknown;
    let message = line;
    
    // Handle LogstashEncoder JSON format
    if (line.startsWith('{')) {
      try {
        parsed = JSON.parse(line);
        if (parsed && typeof (parsed as { message?: unknown }).message === 'string') {
          message = (parsed as { message: string }).message;
        } else if (parsed && typeof (parsed as { log?: unknown }).log === 'string') {
          message = (parsed as { log: string }).log;
        }
      } catch (error) {
        if (debug) {
          console.log(`[DEBUG] JSON parse error for line: ${line.substring(0, 100)}`);
        }
        // ignore JSON parse failures and fall back to raw line
      }
    }

    if (!message.includes('loan_event=')) return false;

    const payloadStart = message.indexOf('loan_event=') + 'loan_event='.length;
    const payloadRaw = message.slice(payloadStart).trim();
    
    // More robust payload extraction - handle cases where there might be text after the JSON
    let payloadText = payloadRaw;
    if (payloadText.startsWith('{')) {
      // Find the matching closing brace
      let braceCount = 0;
      let endIndex = 0;
      for (let i = 0; i < payloadText.length; i++) {
        if (payloadText[i] === '{') braceCount++;
        if (payloadText[i] === '}') braceCount--;
        if (braceCount === 0) {
          endIndex = i + 1;
          break;
        }
      }
      if (endIndex > 0) {
        payloadText = payloadText.substring(0, endIndex);
      }
    } else {
      // Fallback to old logic for non-JSON payloads
      payloadText = payloadRaw.split(/\s+/)[0] ?? '';
    }

    let payload: unknown;
    if (payloadText.startsWith('{')) {
      try {
        payload = JSON.parse(payloadText);
      } catch (error) {
        if (debug) {
          console.log(`[DEBUG] Payload parse error: ${payloadText.substring(0, 100)}`);
        }
        // ignore: we'll fall back to string matching
      }
    }

    // Check loan ID match
    const payloadLoanId = typeof payload === 'object' && payload !== null && 'id' in payload ? (payload as { id?: string }).id : undefined;
    const matchesLoanId = payloadLoanId === loanId || message.includes(loanId) || line.includes(loanId);
    
    if (debug && (payloadLoanId === loanId || message.includes(loanId))) {
      console.log(`[DEBUG] Found matching loan ID: ${loanId}`);
      console.log(`[DEBUG] Payload: ${JSON.stringify(payload)}`);
      console.log(`[DEBUG] Looking for field: ${uniqueField}`);
    }

    if (!matchesLoanId) return false;

    if (!uniqueField) {
      return true;
    }

    // Check for the specific field
    if (payload && typeof payload === 'object' && uniqueField in (payload as Record<string, unknown>)) {
      const value = (payload as Record<string, unknown>)[uniqueField];
      const hasField = value !== undefined && value !== null;
      if (debug) {
        console.log(`[DEBUG] Field ${uniqueField} found: ${hasField}, value: ${value}`);
      }
      return hasField;
    }

    // Fallback to string matching
    const stringMatch = fieldNeedles.some((needle) => message.includes(needle) || line.includes(needle));
    if (debug && stringMatch) {
      console.log(`[DEBUG] Field found via string matching: ${uniqueField}`);
    }
    return stringMatch;
  });
}
