import { Pool } from 'pg';
import { DB_POLL_INTERVAL_MS, DEFAULT_WAIT_TIMEOUT_MS, PG_URL } from './env';

let pool: Pool | null = null;

export function getPool(): Pool {
  if (!pool) {
    pool = new Pool({ connectionString: PG_URL });
  }
  return pool;
}

export async function closePool(): Promise<void> {
  if (!pool) return;
  const existing = pool;
  pool = null;
  await existing.end();
}

export type LoanRow = {
  id: string;
  status: string;
  remaining_balance: string | number;
};

export async function getLoanRow(id: string): Promise<LoanRow | null> {
  const { rows } = await getPool().query<LoanRow>(
    'SELECT id, status, remaining_balance FROM loan WHERE id = $1',
    [id]
  );
  return rows[0] || null;
}

export async function waitForLoanStatus(
  id: string,
  expected: string,
  timeoutMs = DEFAULT_WAIT_TIMEOUT_MS
): Promise<LoanRow> {
  const start = Date.now();
  let last: LoanRow | null = null;
  while (Date.now() - start < timeoutMs) {
    const row = await getLoanRow(id);
    if (row && row.status === expected) return row;
    last = row ?? last;
    await new Promise((r) => setTimeout(r, DB_POLL_INTERVAL_MS));
  }
  const finalRow = await getLoanRow(id);
  const snapshot = finalRow ?? last;
  const status = snapshot?.status ?? 'UNKNOWN';
  throw new Error(`Loan ${id} did not reach status ${expected} within ${timeoutMs}ms (last status: ${status})`);
}

export async function waitForRemainingBalance(
  id: string,
  expected: number,
  timeoutMs = DEFAULT_WAIT_TIMEOUT_MS
): Promise<LoanRow> {
  const start = Date.now();
  let last: LoanRow | null = null;
  while (Date.now() - start < timeoutMs) {
    const row = await getLoanRow(id);
    if (row && Number(row.remaining_balance) === expected) return row;
    last = row ?? last;
    await new Promise((r) => setTimeout(r, DB_POLL_INTERVAL_MS));
  }
  const snapshot = last ?? null;
  const balance = snapshot ? Number(snapshot.remaining_balance) : 'UNKNOWN';
  throw new Error(`Loan ${id} did not reach remaining balance ${expected} within ${timeoutMs}ms (last balance: ${balance})`);
}
