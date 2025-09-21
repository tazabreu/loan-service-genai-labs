let counter = 0;

export function uniqueCustomerId(prefix: string): string {
  counter += 1;
  return `${prefix}-${counter.toString().padStart(4, '0')}`;
}

type LoanDefaults = {
  amount: number;
  termMonths: number;
  interestRate: number;
  customerPrefix: string;
};

export type ScenarioKind =
  | 'autoApproval'
  | 'manualApproval'
  | 'highValue'
  | 'baseline';

const defaults: Record<ScenarioKind, LoanDefaults> = {
  autoApproval: {
    amount: 4000,
    termMonths: 12,
    interestRate: 0.12,
    customerPrefix: 'auto'
  },
  manualApproval: {
    amount: 15000,
    termMonths: 12,
    interestRate: 0.12,
    customerPrefix: 'manual'
  },
  highValue: {
    amount: 25000,
    termMonths: 24,
    interestRate: 0.14,
    customerPrefix: 'high'
  },
  baseline: {
    amount: 2000,
    termMonths: 12,
    interestRate: 0.12,
    customerPrefix: 'base'
  }
};

export function buildLoanSimulation(
  kind: ScenarioKind = 'baseline',
  overrides: Partial<Omit<LoanDefaults, 'customerPrefix'>> & { customerId?: string } = {}
) {
  const base = defaults[kind];
  return {
    amount: overrides.amount ?? base.amount,
    termMonths: overrides.termMonths ?? base.termMonths,
    interestRate: overrides.interestRate ?? base.interestRate,
    customerId: overrides.customerId ?? uniqueCustomerId(base.customerPrefix)
  };
}
