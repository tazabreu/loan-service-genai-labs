import { Kafka, Consumer, EachMessagePayload } from 'kafkajs';
import { KAFKA_BROKERS } from './env';
import { randomUUID } from 'crypto';

export function createConsumer(groupId?: string): Consumer {
  const kafka = new Kafka({ brokers: KAFKA_BROKERS });
  return kafka.consumer({ groupId: groupId || `e2e-${randomUUID()}` });
}

export async function waitForKafkaMatch(
  topic: string,
  predicate: (value: string) => boolean,
  { timeoutMs = 30000, fromBeginning = true }: { timeoutMs?: number; fromBeginning?: boolean } = {}
): Promise<number> {
  const consumer = createConsumer();
  await consumer.connect();
  await consumer.subscribe({ topic, fromBeginning });

  let count = 0;
  let resolved = false;
  const done = new Promise<number>((resolve) => {
    const t = setTimeout(async () => {
      try { await consumer.stop(); await consumer.disconnect(); } catch {}
      if (!resolved) { resolved = true; resolve(count); }
    }, timeoutMs);

    consumer.run({
      eachMessage: async ({ message }: EachMessagePayload) => {
        const val = message.value?.toString() || '';
        if (predicate(val)) {
          count++;
          // Once we match at least once, we can stop
          clearTimeout(t);
          try { await consumer.stop(); await consumer.disconnect(); } catch {}
          if (!resolved) { resolved = true; resolve(count); }
        }
      }
    }).catch(async () => {
      try { await consumer.stop(); await consumer.disconnect(); } catch {}
      if (!resolved) { resolved = true; resolve(count); }
    });
  });

  return done;
}

