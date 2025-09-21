#!/usr/bin/env node
import { createInterface } from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';
import { randomInt } from 'node:crypto';

const rl = createInterface({ input, output });

const DEFAULT_API_BASE = process.env.API_BASE || 'http://localhost:8081';

function banner(title) {
  const line = '-'.repeat(title.length + 4);
  console.log(`\n${line}\n| ${title} |\n${line}`);
}

async function prompt(message, defaultValue) {
  const suffix = defaultValue !== undefined ? ` [${defaultValue}]` : '';
  const answer = (await rl.question(`${message}${suffix}: `)).trim();
  return answer === '' && defaultValue !== undefined ? defaultValue : answer;
}

async function confirm(message, defaultYes = true) {
  const hint = defaultYes ? 'Y/n' : 'y/N';
  const answer = (await rl.question(`${message} (${hint}) `)).trim().toLowerCase();
  if (!answer) return defaultYes;
  return ['y', 'yes'].includes(answer);
}

async function pause(label = 'Press Enter to continue') {
  await rl.question(`\n${label}…`);
}

async function postJSON(apiBase, path, body, headers = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Request to ${path} failed (${response.status} ${response.statusText}): ${text}`);
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return await response.json();
  }
  return await response.text();
}

function printLoan(loan, label = 'Loan snapshot') {
  console.log(`\n${label}:`);
  console.log(`  id:                ${loan.id}`);
  console.log(`  status:            ${loan.status}`);
  console.log(`  amount:            ${loan.amount}`);
  console.log(`  remaining balance: ${loan.remainingBalance}`);
  console.log(`  term (months):     ${loan.termMonths}`);
  console.log(`  customer:          ${loan.customerId}`);
}

async function main() {
  banner('Loan Lifecycle Interactive Walkthrough');
  console.log('This guide exercises simulate → approve → contract → disburse → pay against the running API.');
  console.log('Bring the stack up first with "make up" or "pnpm run e2e:local:keep".');

  const apiBase = await prompt('API base URL', DEFAULT_API_BASE);
  const defaultAmount = randomInt(5_000, 15_000);
  const amount = Number(await prompt('Loan amount', defaultAmount));
  const termMonths = Number(await prompt('Term (months)', 12));
  const interestRate = Number(await prompt('Interest rate (decimal)', 0.12));
  const paymentAmount = Number(await prompt('Repayment amount', Math.min(1200, amount)));
  const customerId = await prompt('Customer id', `manual-cli-${Date.now()}`);

  await pause('Ready to simulate the loan? Press Enter');

  banner('Step 1 • Simulate');
  console.log(`POST ${apiBase}/loans/simulate`);
  const simulated = await postJSON(apiBase, '/loans/simulate', {
    amount,
    termMonths,
    interestRate,
    customerId,
  });
  printLoan(simulated, 'Simulation response');

  const loanId = simulated.id;
  let currentLoan = simulated;

  if (simulated.status === 'PENDING_APPROVAL') {
    if (await confirm('Loan requires manual approval. Approve now?', true)) {
      await pause('Press Enter to approve the loan');
      banner('Step 2 • Approve');
      currentLoan = await postJSON(apiBase, `/loans/${loanId}/approve`, {});
      printLoan(currentLoan, 'Approval response');
    } else {
      console.log('Skipping approval step. You can rerun this script later when ready.');
      return;
    }
  } else {
    console.log('\nLoan auto-approved during simulation.');
  }

  await pause('Press Enter to contract the loan');
  banner('Step 3 • Contract');
  currentLoan = await postJSON(apiBase, `/loans/${loanId}/contract`);
  printLoan(currentLoan, 'Contract response');

  await pause('Press Enter to disburse funds');
  banner('Step 4 • Disburse');
  currentLoan = await postJSON(apiBase, `/loans/${loanId}/disburse`);
  printLoan(currentLoan, 'Disburse response');

  // Repayment phase: support single, iterative, or auto-full repayment (liquidação)
  await pause('Press Enter to choose repayment mode');
  banner('Step 5 • Repay');

  // Choose repayment style
  const autoFull = await confirm('Auto-pay to full liquidation now?', true);
  if (autoFull) {
    let iteration = 1;
    while ((currentLoan.remainingBalance ?? 0) > 0 && currentLoan.status !== 'PAID') {
      const next = Math.min(Number(paymentAmount), Number(currentLoan.remainingBalance));
      console.log(`\n[Auto ${iteration}] Paying ${next} towards remaining ${currentLoan.remainingBalance}`);
      currentLoan = await postJSON(apiBase, `/loans/${loanId}/pay`, { amount: next });
      printLoan(currentLoan, `Repayment response • Iteration ${iteration}`);
      iteration += 1;
    }
  } else {
    const iterative = await confirm('Iterate payments interactively until fully paid?', true);
    if (iterative) {
      let iteration = 1;
      while ((currentLoan.remainingBalance ?? 0) > 0 && currentLoan.status !== 'PAID') {
        await pause(`Press Enter to make payment #${iteration}`);
        const next = Math.min(Number(paymentAmount), Number(currentLoan.remainingBalance));
        console.log(`\n[Iter ${iteration}] Paying ${next} towards remaining ${currentLoan.remainingBalance}`);
        currentLoan = await postJSON(apiBase, `/loans/${loanId}/pay`, { amount: next });
        printLoan(currentLoan, `Repayment response • Iteration ${iteration}`);
        iteration += 1;
      }
    } else {
      // Single payment (original behavior)
      currentLoan = await postJSON(apiBase, `/loans/${loanId}/pay`, { amount: paymentAmount });
      printLoan(currentLoan, 'Repayment response');
    }
  }

  if (currentLoan.remainingBalance === 0 || currentLoan.status === 'PAID') {
    console.log('\n✅ Loan fully repaid (liquidação completa)!');
  } else {
    console.log(`\nRemaining balance after payment(s): ${currentLoan.remainingBalance}`);
  }

  await pause('Optional: check API health (Enter to send)');
  banner('Bonus • Health Check');
  const health = await fetch(`${apiBase}/actuator/health`).then(async (res) => {
    if (!res.ok) throw new Error(`Health check failed: ${res.status}`);
    return res.json();
  });
  console.log('Health endpoint response:', health);

  console.log('\nAll steps completed. Feel free to rerun with different inputs or inspect downstream services (Kafka, Postgres, Grafana).');
}

main()
  .catch((err) => {
    console.error(`\n❌ ${err.message}`);
    process.exitCode = 1;
  })
  .finally(() => rl.close());
