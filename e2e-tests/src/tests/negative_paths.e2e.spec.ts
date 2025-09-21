import { loanClient } from '../utils/http';
import { buildLoanSimulation } from '../utils/scenario';

describe('Epic: Negative paths and validation', () => {
  it('Rejects disburse before contract with 409', async () => {
    const res = await loanClient.simulate(buildLoanSimulation('baseline', { amount: 1200, customerId: 'neg-disburse' }));
    const id = typeof res.data?.id === 'string' ? res.data.id : null;
    if (!id) {
      throw new Error('Loan ID missing for negative disburse flow');
    }
    const disb = await loanClient.disburse(id);
    expect(disb.status).toBe(409);
  });

  it('Rejects negative payment with 400', async () => {
    const res = await loanClient.simulate(buildLoanSimulation('baseline', { amount: 1000, customerId: 'neg-payment' }));
    const id = typeof res.data?.id === 'string' ? res.data.id : null;
    if (!id) {
      throw new Error('Loan ID missing for negative payment flow');
    }
    // Try negative amount
    const pay = await loanClient.pay(id, { amount: -1 });
    expect(pay.status).toBe(400);
  });
});
