import axios, { AxiosInstance, AxiosResponse } from 'axios';
import { API_BASE, HTTP_TIMEOUT_MS } from './env';

export type LoanSimulationRequest = {
  amount: number;
  termMonths: number;
  interestRate: number;
  customerId: string;
};

export type LoanCommandResponse = {
  id: string;
  status: string;
  remaining_balance?: string | number;
  remainingBalance?: string | number;
};

export type LoanPaymentRequest = {
  amount: number;
};

export interface LoanClient {
  raw: AxiosInstance;
  simulate(payload: LoanSimulationRequest): Promise<AxiosResponse<LoanCommandResponse>>;
  contract(id: string): Promise<AxiosResponse<LoanCommandResponse>>;
  disburse(id: string): Promise<AxiosResponse<LoanCommandResponse>>;
  approve(id: string, headers?: Record<string, string>): Promise<AxiosResponse<LoanCommandResponse>>;
  reject(id: string, headers?: Record<string, string>): Promise<AxiosResponse<LoanCommandResponse>>;
  pay(id: string, payload: LoanPaymentRequest): Promise<AxiosResponse<LoanCommandResponse>>;
  get(path: string): Promise<AxiosResponse<unknown>>;
}

const defaultHeaders = { 'Content-Type': 'application/json' } as const;

export function createLoanClient(): LoanClient {
  const instance = axios.create({
    baseURL: API_BASE,
    timeout: HTTP_TIMEOUT_MS,
    headers: defaultHeaders,
    validateStatus: () => true
  });

  return {
    raw: instance,
    simulate: (payload) => instance.post('/loans/simulate', payload),
    contract: (id) => instance.post(`/loans/${id}/contract`, {}),
    disburse: (id) => instance.post(`/loans/${id}/disburse`, {}),
    approve: (id, headers) => instance.post(`/loans/${id}/approve`, {}, { headers }),
    reject: (id, headers) => instance.post(`/loans/${id}/reject`, {}, { headers }),
    pay: (id, payload) => instance.post(`/loans/${id}/pay`, payload),
    get: (path) => instance.get(path)
  };
}

export const loanClient = createLoanClient();

