import { loanClient } from './http';
import { buildLoanSimulation, ScenarioKind } from './scenario';
import { waitForLoanStatus } from './db';

type SimulationOverrides = Parameters<typeof buildLoanSimulation>[1];

async function simulate(kind: ScenarioKind, overrides?: SimulationOverrides) {
  const simulation = buildLoanSimulation(kind, overrides ?? {});
  const res = await loanClient.simulate(simulation);
  const id = typeof res.data?.id === 'string' ? res.data.id : null;
  if (res.status >= 300 || !id) {
    throw new Error(`Loan simulation failed for scenario ${kind} (${res.status})`);
  }
  return { id, simulation };
}

export async function createPendingApprovalLoan(overrides?: SimulationOverrides) {
  const { id, simulation } = await simulate('manualApproval', overrides);
  await waitForLoanStatus(id, 'PENDING_APPROVAL');
  return { id, simulation } as const;
}

export async function createApprovedLoan(kind: ScenarioKind = 'autoApproval', overrides?: SimulationOverrides) {
  const { id, simulation } = await simulate(kind, overrides);
  await waitForLoanStatus(id, 'APPROVED');
  return { id, simulation } as const;
}

export async function createDisbursedLoan(kind: ScenarioKind = 'baseline', overrides?: SimulationOverrides) {
  const { id, simulation } = await createApprovedLoan(kind, overrides);
  await loanClient.contract(id);
  await waitForLoanStatus(id, 'CONTRACTED');
  await loanClient.disburse(id);
  const disbursed = await waitForLoanStatus(id, 'DISBURSED');
  return { id, simulation, disbursed } as const;
}
