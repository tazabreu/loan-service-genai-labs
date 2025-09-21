import { getLoanRow } from '../utils/db';
import { captureTopicSinceNow } from '../utils/kafka';
import { loanClient } from '../utils/http';
import { createDisbursedLoan } from '../utils/harness';

describe('Epic: Partial repayments and overpayment guardrails', () => {
  it('Partial repayment reduces remaining balance while staying DISBURSED and emits payment event', async () => {
    const { id, disbursed } = await createDisbursedLoan('baseline', { amount: 3600, customerId: 'partial-pay' });
    const initialBalance = Number(disbursed.remaining_balance);
    const toPay = initialBalance / 3;

    const probe = await captureTopicSinceNow('loan-events');
    try {
      const payRes = await loanClient.pay(id, { amount: toPay });
      expect(payRes.status).toBeLessThan(300);

      const row = await getLoanRow(id);
      if (!row) {
        throw new Error(`Loan ${id} missing after partial payment`);
      }

      expect(row.status).toBe('DISBURSED');
      expect(Number(row.remaining_balance)).toBeCloseTo(initialBalance - toPay, 2);

      const paymentEvents = await probe.until((v) => v.includes(id) && v.includes('"paidAt"'));
      expect(paymentEvents).toBeGreaterThanOrEqual(1);
    } finally {
      await probe.stop();
    }
  });

  it('Overpayment request is rejected with 400 and leaves balance untouched', async () => {
    const { id, disbursed } = await createDisbursedLoan('baseline', { amount: 2400, customerId: 'overpay-guard' });
    const initialBalance = Number(disbursed.remaining_balance);
    const probe = await captureTopicSinceNow('loan-events');
    try {
      const overpay = await loanClient.pay(id, { amount: initialBalance + 50 });
      expect(overpay.status).toBe(400);

      const row = await getLoanRow(id);
      if (!row) {
        throw new Error(`Loan ${id} missing after overpayment attempt`);
      }
      expect(Number(row.remaining_balance)).toBe(initialBalance);

      const extraEvents = await probe.until((v) => v.includes(id) && v.includes('"paidAt"'), 1500);
      expect(extraEvents).toBe(0);
    } finally {
      await probe.stop();
    }
  });
});
