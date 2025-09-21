import { getLoanRow, waitForLoanStatus } from '../utils/db';
import { captureTopicSinceNow } from '../utils/kafka';
import { getServiceLogs, logsContainLoanEvent } from '../utils/logs';
import { loanClient } from '../utils/http';
import { createApprovedLoan, createPendingApprovalLoan } from '../utils/harness';

describe('Epic: Manual rejection & idempotency safeguards', () => {
  it('Given pending approval, When rejected, Then status/events/logs reflect rejection and approval is blocked', async () => {
    const { id } = await createPendingApprovalLoan({ customerId: 'manual-reject' });
    const probe = await captureTopicSinceNow('loan-events');
    try {
      const reject = await loanClient.reject(id, { 'X-User': 'e2e-reviewer' });
      expect(reject.status).toBeLessThan(300);

      const rejected = await waitForLoanStatus(id, 'REJECTED');
      expect(rejected.status).toBe('REJECTED');

      const rejectedEvents = await probe.until((v) => v.includes(id) && v.includes('"rejectedAt"'));
      expect(rejectedEvents).toBeGreaterThanOrEqual(1);

      const logs = await getServiceLogs('loan-notification-service', { since: '10m' });
      const hasRejectedEvent = logsContainLoanEvent(logs, id, 'rejectedAt');
      expect(hasRejectedEvent).toBe(true);

      const approveAfter = await loanClient.approve(id, { 'X-User': 'e2e-approver' });
      expect(approveAfter.status).toBe(409);
    } finally {
      await probe.stop();
    }
  });

  it('Duplicate contract command returns conflict and does not re-emit events', async () => {
    const { id } = await createApprovedLoan('autoApproval', { customerId: 'contract-idempotent' });
    const probe = await captureTopicSinceNow('loan-events');
    try {
      const first = await loanClient.contract(id);
      expect(first.status).toBeLessThan(300);
      await waitForLoanStatus(id, 'CONTRACTED');

      const firstEvents = await probe.until((v) => v.includes(id) && v.includes('"contractedAt"'));
      expect(firstEvents).toBeGreaterThanOrEqual(1);

      const duplicate = await loanClient.contract(id);
      expect(duplicate.status).toBe(409);

      const extraEvents = await probe.until((v) => v.includes(id) && v.includes('"contractedAt"'), 1500);
      expect(extraEvents).toBe(0);

      const row = await getLoanRow(id);
      if (!row) {
        throw new Error(`Loan ${id} missing after duplicate contract attempt`);
      }
      expect(row.status).toBe('CONTRACTED');
    } finally {
      await probe.stop();
    }
  });
});
