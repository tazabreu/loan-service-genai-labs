import { Kafka, Consumer, EachMessagePayload, logLevel } from 'kafkajs';
import { DEFAULT_WAIT_TIMEOUT_MS, KAFKA_BROKERS } from './env';
import { randomUUID } from 'crypto';

const activeProbeStops = new Set<() => Promise<unknown>>();
const kafka = new Kafka({ brokers: KAFKA_BROKERS, logLevel: logLevel.NOTHING });

async function connectWithRetry(consumer: Consumer, attempts = 3, baseDelayMs = 250): Promise<void> {
  let lastError: unknown;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      await consumer.connect();
      return;
    } catch (error) {
      lastError = error;
      if (attempt === attempts) {
        throw error;
      }
      const delayMs = baseDelayMs * attempt;
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
  throw lastError instanceof Error ? lastError : new Error('Failed to connect Kafka consumer');
}

async function safeShutdown(target: Consumer): Promise<void> {
  try {
    await target.stop();
  } catch (error) {
    // ignore: consumer already stopped
  }
  try {
    await target.disconnect();
  } catch (error) {
    // ignore: consumer already disconnected
  }
}

export async function stopAllProbes(): Promise<void> {
  const stops = Array.from(activeProbeStops.values());
  activeProbeStops.clear();
  await Promise.all(stops.map(async (stop) => {
    try {
      await stop();
    } catch (error) {
      // ignore: best-effort cleanup
    }
  }));
}

export function createConsumer(groupId?: string): Consumer {
  return kafka.consumer({ groupId: groupId || `e2e-${randomUUID()}` });
}

export async function waitForKafkaMatch(
  topic: string,
  predicate: (value: string) => boolean,
  { timeoutMs = 30000, fromBeginning = true }: { timeoutMs?: number; fromBeginning?: boolean } = {}
): Promise<number> {
  const consumer = createConsumer();
  await connectWithRetry(consumer);
  await consumer.subscribe({ topic, fromBeginning });

  let count = 0;
  let resolved = false;
  const done = new Promise<number>((resolve) => {
    const t = setTimeout(async () => {
      await safeShutdown(consumer);
      if (!resolved) {
        resolved = true;
        resolve(count);
      }
    }, timeoutMs);

    consumer.run({
      eachMessage: async ({ message }: EachMessagePayload) => {
        const val = message.value?.toString() || '';
        if (predicate(val)) {
          count++;
          // Once we match at least once, we can stop
          clearTimeout(t);
          await safeShutdown(consumer);
          if (!resolved) {
            resolved = true;
            resolve(count);
          }
        }
      }
    }).catch(async () => {
      await safeShutdown(consumer);
      if (!resolved) {
        resolved = true;
        resolve(count);
      }
    });
  });

  return done;
}

// Probe that captures messages from now on and lets tests await specific matches.
export async function captureTopicSinceNow(topic: string) {
  const consumer = createConsumer(`e2e-probe-${randomUUID()}`);
  await connectWithRetry(consumer);
  await consumer.subscribe({ topic, fromBeginning: false });

  const buffer: string[] = [];
  let running = true;

  const runPromise = consumer.run({
    eachMessage: ({ message }) => {
      if (!running) return Promise.resolve();
      const val = message.value?.toString() || '';
      buffer.push(val);
      return Promise.resolve();
    }
  });

  async function until(predicate: (value: string) => boolean, timeoutMs = DEFAULT_WAIT_TIMEOUT_MS): Promise<number> {
    const startLen = buffer.length;
    const sliceMatches = () => buffer.slice(startLen).filter(predicate).length;

    const immediate = sliceMatches();
    if (immediate > 0) {
      return immediate;
    }

    return await new Promise<number>((resolve) => {
      let resolved = false;
      let interval: NodeJS.Timeout | undefined;
      const timeout = setTimeout(() => {
        if (!resolved) {
          resolved = true;
          if (interval) {
            clearInterval(interval);
          }
          resolve(sliceMatches());
        }
      }, timeoutMs);

      interval = setInterval(() => {
        const matches = sliceMatches();
        if (matches > 0 && !resolved) {
          resolved = true;
          clearTimeout(timeout);
          if (interval) {
            clearInterval(interval);
          }
          resolve(matches);
        }
      }, 200);
    });
  }

  async function stop() {
    running = false;
    await safeShutdown(consumer);
    await runPromise.catch(() => undefined);
  }
  const trackedStop = async () => {
    activeProbeStops.delete(trackedStop);
    await stop();
  };

  activeProbeStops.add(trackedStop);

  return { until, stop: trackedStop, messages: buffer } as const;
}
