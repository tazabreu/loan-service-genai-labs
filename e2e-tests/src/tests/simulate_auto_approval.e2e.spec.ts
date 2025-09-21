import { waitForLoanStatus } from '../utils/db';
import { captureTopicSinceNow } from '../utils/kafka';
import { getServiceLogs, logsContainLoanEvent } from '../utils/logs';
import { loanClient } from '../utils/http';
import { buildLoanSimulation } from '../utils/scenario';

describe('Epic: Simulate (auto-approval path)', () => {
  it('Given simulate below threshold, Then loan is APPROVED and events/logs exist', async () => {
    const probe = await captureTopicSinceNow('loan-events');
    try {
      const simulation = buildLoanSimulation('autoApproval');
      const res = await loanClient.simulate(simulation);
      const amount = simulation.amount;
      expect(res.status).toBeLessThan(300);
      const id = typeof res.data?.id === 'string' ? res.data.id : null;
      if (!id) {
        throw new Error('Loan ID missing from simulate response');
      }

      const row = await waitForLoanStatus(id, 'APPROVED');
      expect(row.status).toBe('APPROVED');
      expect(Number(row.remaining_balance)).toBe(amount);

      const simulated = await probe.until((v) => v.includes(id) && v.includes('"simulatedAt"'));
      expect(simulated).toBeGreaterThanOrEqual(1);
      const approved = await probe.until((v) => v.includes(id) && v.includes('"approvedAt"'));
      expect(approved).toBeGreaterThanOrEqual(1);

      const logs = await getServiceLogs('loan-notification-service', { since: '10m' });
      const hasSimulatedEvent = logsContainLoanEvent(logs, id, 'simulatedAt');
      const hasApprovedEvent = logsContainLoanEvent(logs, id, 'approvedAt');
      expect(hasSimulatedEvent || hasApprovedEvent).toBe(true);
    } finally {
      await probe.stop();
    }
  });
});
