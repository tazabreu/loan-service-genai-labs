import axios from 'axios';
import { API_BASE } from '../utils/env';
import { getLoanRow, pool } from '../utils/db';
import { waitForKafkaMatch } from '../utils/kafka';
import { getServiceLogs, logsContainLoanEvent } from '../utils/logs';

describe('Epic: Loan lifecycle (simulate → contract → disburse → pay)', () => {
  afterAll(async () => {
    await pool.end();
  });

  const state: { id?: string; amount?: number } = {};

  describe('Scenario: simulate a loan (auto-approval path)', () => {
    it('Given: we call POST /loans/simulate, Then API returns 201 with an id', async () => {
      const amount = Math.floor(Math.random() * 9000) + 1000; // 1000..9999 (below approval threshold)
      const res = await axios.post(`${API_BASE}/loans/simulate`, {
        amount,
        termMonths: 12,
        interestRate: 0.12,
        customerId: 'e2e-ts'
      }, { validateStatus: () => true });

      expect(res.status).toBeLessThan(300);
      expect(res.data?.id).toBeTruthy();
      state.id = res.data.id;
      state.amount = amount;
    });

    it('Then: database reflects APPROVED with full remaining balance', async () => {
      const row = await getLoanRow(state.id!);
      expect(row).not.toBeNull();
      expect(row!.status).toBe('APPROVED');
      expect(Number(row!.remaining_balance)).toBe(state.amount);
    });

    it('Then: Kafka has LoanSimulatedEvent and LoanApprovedEvent for this id', async () => {
      const id = state.id!;
      const simulatedCount = await waitForKafkaMatch('loan-events', (v) => v.includes(id) && v.includes('"simulatedAt"'));
      expect(simulatedCount).toBeGreaterThanOrEqual(1);
      const approvedCount = await waitForKafkaMatch('loan-events', (v) => v.includes(id) && v.includes('"approvedAt"'));
      expect(approvedCount).toBeGreaterThanOrEqual(1);
    });

    it('Then: notification service logs include loan_event with id (simulated or approved)', async () => {
      const logs = await getServiceLogs('loan-notification-service', { since: '5m' });
      const id = state.id!;
      const found = logsContainLoanEvent(logs, id, 'simulatedAt') || logsContainLoanEvent(logs, id, 'approvedAt');
      expect(found).toBe(true);
    });
  });

  describe('Scenario: contract the loan', () => {
    it('When: we call POST /loans/{id}/contract', async () => {
      const res = await axios.post(`${API_BASE}/loans/${state.id}/contract`, {}, { validateStatus: () => true });
      expect(res.status).toBeLessThan(300);
    });

    it('Then: database status becomes CONTRACTED', async () => {
      const row = await getLoanRow(state.id!);
      expect(row).not.toBeNull();
      expect(row!.status).toBe('CONTRACTED');
    });

    it('Then: Kafka has LoanContractedEvent and logs include it', async () => {
      const id = state.id!;
      const matched = await waitForKafkaMatch('loan-events', (v) => v.includes(id) && v.includes('"contractedAt"'));
      expect(matched).toBeGreaterThanOrEqual(1);

      const logs = await getServiceLogs('loan-notification-service', { since: '5m' });
      expect(logsContainLoanEvent(logs, id, 'contractedAt')).toBe(true);
    });
  });

  describe('Scenario: disburse the loan', () => {
    it('When: we call POST /loans/{id}/disburse', async () => {
      const res = await axios.post(`${API_BASE}/loans/${state.id}/disburse`, {}, { validateStatus: () => true });
      expect(res.status).toBeLessThan(300);
    });

    it('Then: database status becomes DISBURSED', async () => {
      const row = await getLoanRow(state.id!);
      expect(row).not.toBeNull();
      expect(row!.status).toBe('DISBURSED');
    });

    it('Then: Kafka has LoanDisbursedEvent and logs include it', async () => {
      const id = state.id!;
      const matched = await waitForKafkaMatch('loan-events', (v) => v.includes(id) && v.includes('"disbursedAt"'));
      expect(matched).toBeGreaterThanOrEqual(1);
      const logs = await getServiceLogs('loan-notification-service', { since: '5m' });
      expect(logsContainLoanEvent(logs, id, 'disbursedAt')).toBe(true);
    });
  });

  describe('Scenario: pay the loan in full', () => {
    it('When: we call POST /loans/{id}/pay with the full amount', async () => {
      const res = await axios.post(`${API_BASE}/loans/${state.id}/pay`, { amount: state.amount }, { validateStatus: () => true });
      expect(res.status).toBeLessThan(300);
    });

    it('Then: database status becomes PAID and remaining balance is 0', async () => {
      const row = await getLoanRow(state.id!);
      expect(row).not.toBeNull();
      expect(row!.status).toBe('PAID');
      expect(Number(row!.remaining_balance)).toBe(0);
    });

    it('Then: Kafka has LoanPaymentMadeEvent and logs include it', async () => {
      const id = state.id!;
      const matched = await waitForKafkaMatch('loan-events', (v) => v.includes(id) && v.includes('"paidAt"'));
      expect(matched).toBeGreaterThanOrEqual(1);
      const logs = await getServiceLogs('loan-notification-service', { since: '5m' });
      expect(logsContainLoanEvent(logs, id, 'paidAt')).toBe(true);
    });
  });
});
