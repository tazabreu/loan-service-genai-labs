import { getLoanRow, waitForLoanStatus, waitForRemainingBalance } from '../utils/db';
import { captureTopicSinceNow } from '../utils/kafka';
import { getServiceLogs, logsContainLoanEvent } from '../utils/logs';
import { loanClient } from '../utils/http';
import { buildLoanSimulation } from '../utils/scenario';

describe('Epic: Contract → Disburse → Pay', () => {
  it('Given an APPROVED loan, When contract → disburse → pay (liquidação completa), Then DB/Kafka/logs align', async () => {
    const probe = await captureTopicSinceNow('loan-events');
    try {
      // Create an APPROVED loan with deterministic amount
      const simulation = buildLoanSimulation('baseline', { amount: 2400, customerId: 'contract-flow' });
      const res = await loanClient.simulate(simulation);
      const amount = simulation.amount;
      expect(res.status).toBeLessThan(300);
      const id = typeof res.data?.id === 'string' ? res.data.id : null;
      if (!id) {
        throw new Error('Loan ID missing from simulate response');
      }

      // Ensure APPROVED
      await waitForLoanStatus(id, 'APPROVED');

      // contract
      const c1 = await loanClient.contract(id);
      expect(c1.status).toBeLessThan(300);
      let row = await waitForLoanStatus(id, 'CONTRACTED');
      expect(row.status).toBe('CONTRACTED');
      const contracted = await probe.until((v) => v.includes(id) && v.includes('"contractedAt"'));
      expect(contracted).toBeGreaterThanOrEqual(1);

      // disburse
      const c2 = await loanClient.disburse(id);
      expect(c2.status).toBeLessThan(300);
      row = await waitForLoanStatus(id, 'DISBURSED');
      expect(row.status).toBe('DISBURSED');
      const disbursed = await probe.until((v) => v.includes(id) && v.includes('"disbursedAt"'));
      expect(disbursed).toBeGreaterThanOrEqual(1);

      // pay to full using iterative payments (simulate liquidation)
      let balance = Number(row.remaining_balance);
      const payment = Math.min(1200, amount);
      let iteration = 0;
      while (balance > 0 && iteration < 24) {
        const toPay = Math.min(payment, balance);
        const c3 = await loanClient.pay(id, { amount: toPay });
        expect(c3.status).toBeLessThan(300);
        const updated = await getLoanRow(id);
        if (!updated) {
          throw new Error(`Loan ${id} not found while attempting to pay remaining balance`);
        }
        balance = Number(updated.remaining_balance);
        iteration += 1;
      }
      row = await waitForRemainingBalance(id, 0);
      expect(row.status).toBe('PAID');
      expect(Number(row.remaining_balance)).toBe(0);
      const paid = await probe.until((v) => v.includes(id) && v.includes('"paidAt"'));
      expect(paid).toBeGreaterThanOrEqual(1);

      const logs = await getServiceLogs('loan-notification-service', { since: '10m' });
      const hasContractedEvent = logsContainLoanEvent(logs, id, 'contractedAt');
      const hasDisbursedEvent = logsContainLoanEvent(logs, id, 'disbursedAt');
      const hasPaidEvent = logsContainLoanEvent(logs, id, 'paidAt');
      expect(hasContractedEvent || hasDisbursedEvent || hasPaidEvent).toBe(true);
    } finally {
      await probe.stop();
    }
  });
});
