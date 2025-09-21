import { loanClient } from '../utils/http';
import { createApprovedLoan } from '../utils/harness';
import { getServiceLogs, logsContainLoanEvent } from '../utils/logs';

type MetricTag = { tag: string; values?: string[] };
type MetricMeasurement = { statistic: string; value: number };

const toStringArray = (value: unknown): string[] => Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];

const toMetricTags = (value: unknown): MetricTag[] => {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is MetricTag => Boolean(item && typeof item === 'object' && 'tag' in item && typeof (item as { tag: unknown }).tag === 'string'));
};

const toMeasurements = (value: unknown): MetricMeasurement[] => {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is MetricMeasurement => {
    if (!item || typeof item !== 'object') return false;
    const candidate = item as { statistic?: unknown; value?: unknown };
    return typeof candidate.statistic === 'string' && typeof candidate.value === 'number';
  });
};

describe('Epic: Observability and Readiness', () => {
  it('Health endpoint is UP', async () => {
    const res = await loanClient.get('/actuator/health');
    expect(res.status).toBe(200);
    expect(res.data).toBeTruthy();
    // Spring Boot health JSON typically has { status: 'UP' }
    const status = typeof (res.data as { status?: unknown } | null)?.status === 'string'
      ? (res.data as { status: string }).status
      : 'UP';
    expect(status).toBe('UP');
  });

  it('Metrics endpoint exposes loan metrics', async () => {
    // Ensure at least one approved loan exists so metrics have data
    await createApprovedLoan('autoApproval', { customerId: 'obs-metrics' });

    const list = await loanClient.get('/actuator/metrics');
    expect(list.status).toBe(200);
    const names = toStringArray((list.data as { names?: unknown } | undefined)?.names);
    expect(names).toEqual(expect.arrayContaining(['loan_status_transitions_total']));

    const metric = await loanClient.get('/actuator/metrics/loan_status_transitions_total');
    expect(metric.status).toBe(200);
    const tags = toMetricTags((metric.data as { availableTags?: unknown } | undefined)?.availableTags);
    const statusTag = tags.find((tag) => tag.tag === 'status');
    expect(statusTag).toBeDefined();

    const measurements = toMeasurements((metric.data as { measurements?: unknown } | undefined)?.measurements);
    const count = measurements.find((m) => m.statistic === 'COUNT');
    if (!count) {
      throw new Error('metrics endpoint did not return COUNT measurement');
    }
    expect(count.value).toBeGreaterThan(0);
  });

  it('Notification service logs include structured loan_event entries after lifecycle activity', async () => {
    const { id } = await createApprovedLoan('autoApproval', { customerId: 'obs-logs' });
    const logs = await getServiceLogs('loan-notification-service', { since: '5m' });
    expect(logsContainLoanEvent(logs, id)).toBe(true);
  });
});
