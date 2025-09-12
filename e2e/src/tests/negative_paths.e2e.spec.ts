import axios from 'axios';
import { API_BASE } from '../utils/env';
import { pool } from '../utils/db';

describe('Epic: Negative paths and validation', () => {
  afterAll(async () => { await pool.end(); });

  it('Rejects disburse before contract with 409', async () => {
    const amount = 1200;
    const res = await axios.post(`${API_BASE}/loans/simulate`, {
      amount, termMonths: 12, interestRate: 0.12, customerId: 'e2e-neg'
    });
    const id = res.data?.id as string;
    const disb = await axios.post(`${API_BASE}/loans/${id}/disburse`, {}, { validateStatus: () => true });
    expect(disb.status).toBe(409);
  });

  it('Rejects negative payment with 400', async () => {
    const res = await axios.post(`${API_BASE}/loans/simulate`, {
      amount: 1000, termMonths: 12, interestRate: 0.12, customerId: 'e2e-neg'
    });
    const id = res.data?.id as string;
    // Try negative amount
    const pay = await axios.post(`${API_BASE}/loans/${id}/pay`, { amount: -1 }, { validateStatus: () => true });
    expect(pay.status).toBe(400);
  });
});

