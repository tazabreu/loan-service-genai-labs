import axios from 'axios';
import { API_BASE } from '../utils/env';
import { getLoanRow, pool } from '../utils/db';
import { waitForKafkaMatch } from '../utils/kafka';
import { getServiceLogs, logsContainLoanEvent } from '../utils/logs';

describe('Epic: Simulate (auto-approval path)', () => {
  afterAll(async () => { await pool.end(); });

  it('Given simulate below threshold, Then loan is APPROVED and events/logs exist', async () => {
    const amount = Math.floor(Math.random() * 4000) + 1000; // 1000..4999
    const res = await axios.post(`${API_BASE}/loans/simulate`, {
      amount,
      termMonths: 12,
      interestRate: 0.12,
      customerId: 'e2e-ts'
    }, { validateStatus: () => true });
    expect(res.status).toBeLessThan(300);
    const id = res.data?.id as string;
    expect(id).toBeTruthy();

    const row = await getLoanRow(id);
    expect(row).not.toBeNull();
    expect(row!.status).toBe('APPROVED');
    expect(Number(row!.remaining_balance)).toBe(amount);

    const simulated = await waitForKafkaMatch('loan-events', v => v.includes(id) && v.includes('"simulatedAt"'));
    expect(simulated).toBeGreaterThanOrEqual(1);
    const approved = await waitForKafkaMatch('loan-events', v => v.includes(id) && v.includes('"approvedAt"'));
    expect(approved).toBeGreaterThanOrEqual(1);

    const logs = await getServiceLogs('loan-notification-service', { since: '5m' });
    const ok = logsContainLoanEvent(logs, id, 'simulatedAt') || logsContainLoanEvent(logs, id, 'approvedAt');
    expect(ok).toBe(true);
  });
});

