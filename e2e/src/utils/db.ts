import { Pool } from 'pg';
import { PG_URL } from './env';

export const pool = new Pool({ connectionString: PG_URL });

export type LoanRow = {
  id: string;
  status: string;
  remaining_balance: string | number;
};

export async function getLoanRow(id: string): Promise<LoanRow | null> {
  const { rows } = await pool.query<LoanRow>(
    'SELECT id, status, remaining_balance FROM loan WHERE id = $1',
    [id]
  );
  return rows[0] || null;
}

