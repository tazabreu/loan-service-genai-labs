import axios from 'axios';
import { API_BASE } from '../utils/env';
import { getLoanRow, pool } from '../utils/db';
import { waitForKafkaMatch } from '../utils/kafka';
import { getServiceLogs, logsContainLoanEvent } from '../utils/logs';

describe('Epic: Contract → Disburse → Pay', () => {
  afterAll(async () => { await pool.end(); });

  it('Given an APPROVED loan, When contract → disburse → pay(full), Then DB/Kafka/logs align', async () => {
    // Create an APPROVED loan
    const amount = 2000;
    const res = await axios.post(`${API_BASE}/loans/simulate`, {
      amount,
      termMonths: 12,
      interestRate: 0.12,
      customerId: 'e2e-contract'
    }, { validateStatus: () => true });
    expect(res.status).toBeLessThan(300);
    const id = res.data?.id as string;

    // contract
    const c1 = await axios.post(`${API_BASE}/loans/${id}/contract`, {}, { validateStatus: () => true });
    expect(c1.status).toBeLessThan(300);
    let row = await getLoanRow(id);
    expect(row!.status).toBe('CONTRACTED');
    const contracted = await waitForKafkaMatch('loan-events', v => v.includes(id) && v.includes('"contractedAt"'));
    expect(contracted).toBeGreaterThanOrEqual(1);

    // disburse
    const c2 = await axios.post(`${API_BASE}/loans/${id}/disburse`, {}, { validateStatus: () => true });
    expect(c2.status).toBeLessThan(300);
    row = await getLoanRow(id);
    expect(row!.status).toBe('DISBURSED');
    const disbursed = await waitForKafkaMatch('loan-events', v => v.includes(id) && v.includes('"disbursedAt"'));
    expect(disbursed).toBeGreaterThanOrEqual(1);

    // pay full amount
    const c3 = await axios.post(`${API_BASE}/loans/${id}/pay`, { amount }, { validateStatus: () => true });
    expect(c3.status).toBeLessThan(300);
    row = await getLoanRow(id);
    expect(row!.status).toBe('PAID');
    expect(Number(row!.remaining_balance)).toBe(0);
    const paid = await waitForKafkaMatch('loan-events', v => v.includes(id) && v.includes('"paidAt"'));
    expect(paid).toBeGreaterThanOrEqual(1);

    const logs = await getServiceLogs('loan-notification-service', { since: '5m' });
    expect(
      logsContainLoanEvent(logs, id, 'contractedAt') ||
      logsContainLoanEvent(logs, id, 'disbursedAt') ||
      logsContainLoanEvent(logs, id, 'paidAt')
    ).toBe(true);
  });
});

