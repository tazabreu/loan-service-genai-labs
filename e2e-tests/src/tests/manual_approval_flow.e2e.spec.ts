import { waitForLoanStatus } from '../utils/db';
import { captureTopicSinceNow } from '../utils/kafka';
import { getServiceLogs, logsContainLoanEvent } from '../utils/logs';
import { loanClient } from '../utils/http';
import { buildLoanSimulation } from '../utils/scenario';

describe('Epic: Manual approval flow (Flagd enabled, amount > threshold)', () => {
  it('Given simulate above threshold, Then loan is PENDING_APPROVAL, When approve → Then APPROVED with events/logs', async () => {
    const probe = await captureTopicSinceNow('loan-events');
    try {
      const simulation = buildLoanSimulation('manualApproval', { customerId: 'manual-flow' });
      const res = await loanClient.simulate(simulation);
      expect(res.status).toBeLessThan(300);
      const id = typeof res.data?.id === 'string' ? res.data.id : null;
      if (!id) {
        throw new Error('Loan ID missing from simulate response');
      }

      let row = await waitForLoanStatus(id, 'PENDING_APPROVAL');
      expect(row.status).toBe('PENDING_APPROVAL');

      const pending = await probe.until((v) => v.includes(id) && v.includes('"at"') && !v.includes('"approvedAt"') && !v.includes('"rejectedAt"'));
      expect(pending).toBeGreaterThanOrEqual(1);

      // Approve
      const approve = await loanClient.approve(id, { 'X-User': 'e2e-approver' });
      expect(approve.status).toBeLessThan(300);

      row = await waitForLoanStatus(id, 'APPROVED');
      expect(row.status).toBe('APPROVED');

      const approved = await probe.until((v) => v.includes(id) && v.includes('"approvedAt"'));
      expect(approved).toBeGreaterThanOrEqual(1);

      const logs = await getServiceLogs('loan-notification-service', { since: '5m' });
      const ok = logsContainLoanEvent(logs, id, 'at') || logsContainLoanEvent(logs, id, 'approvedAt');
      expect(ok).toBe(true);
    } finally {
      await probe.stop();
    }
  });
});
