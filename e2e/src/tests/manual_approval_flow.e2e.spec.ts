import axios from 'axios';
import { API_BASE } from '../utils/env';
import { getLoanRow, pool } from '../utils/db';
import { waitForKafkaMatch } from '../utils/kafka';
import { getServiceLogs, logsContainLoanEvent } from '../utils/logs';

describe('Epic: Manual approval flow (Flagd enabled, amount > threshold)', () => {
  afterAll(async () => { await pool.end(); });

  it('Given simulate above threshold, Then loan is PENDING_APPROVAL, When approve → Then APPROVED with events/logs', async () => {
    const amount = 15000; // above default threshold 10000
    const res = await axios.post(`${API_BASE}/loans/simulate`, {
      amount,
      termMonths: 12,
      interestRate: 0.12,
      customerId: 'e2e-pending'
    }, { validateStatus: () => true });
    expect(res.status).toBeLessThan(300);
    const id = res.data?.id as string;
    expect(id).toBeTruthy();

    let row = await getLoanRow(id);
    expect(row).not.toBeNull();
    expect(row!.status).toBe('PENDING_APPROVAL');

    const pending = await waitForKafkaMatch('loan-events', v => v.includes(id) && v.includes('"at"') && !v.includes('"approvedAt"') && !v.includes('"rejectedAt"'));
    expect(pending).toBeGreaterThanOrEqual(1);

    // Approve
    const approve = await axios.post(`${API_BASE}/loans/${id}/approve`, {}, { validateStatus: () => true, headers: { 'X-User': 'e2e-approver' } });
    expect(approve.status).toBeLessThan(300);

    row = await getLoanRow(id);
    expect(row).not.toBeNull();
    expect(row!.status).toBe('APPROVED');

    const approved = await waitForKafkaMatch('loan-events', v => v.includes(id) && v.includes('"approvedAt"'));
    expect(approved).toBeGreaterThanOrEqual(1);

    const logs = await getServiceLogs('loan-notification-service', { since: '5m' });
    const ok = logsContainLoanEvent(logs, id, 'at') || logsContainLoanEvent(logs, id, 'approvedAt');
    expect(ok).toBe(true);
  });
});

