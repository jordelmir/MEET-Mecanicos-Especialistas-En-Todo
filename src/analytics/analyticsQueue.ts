import type { AnalyticsDebugSnapshot, AnalyticsEvent, AnalyticsQueueRecord } from './analyticsTypes';
import { AnalyticsConsentManager } from './analyticsConsent';
import { getAnonymousId, getSessionId } from './analyticsSession';

const DB_NAME = 'meet_analytics';
const DB_VERSION = 1;
const STORE_NAME = 'events';
const FALLBACK_KEY = 'meet_analytics_queue_fallback';
const RECENT_KEY = 'meet_analytics_recent_events';
const ERRORS_KEY = 'meet_analytics_recent_errors';
const LAST_FLUSH_KEY = 'meet_analytics_last_flush_at';
const MAX_LOCAL_EVENTS = 10_000;
const MAX_RECENT_EVENTS = 100;
const MAX_ERRORS = 50;

type UploadFn = (events: AnalyticsEvent[]) => Promise<void>;

function canUseIndexedDb(): boolean {
  return typeof indexedDB !== 'undefined';
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'event.event_id' });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('IndexedDB open failed'));
  });
}

async function withStore<T>(
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest<T> | void
): Promise<T | undefined> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, mode);
    const store = transaction.objectStore(STORE_NAME);
    const request = operation(store);
    let result: T | undefined;
    if (request) {
      request.onsuccess = () => {
        result = request.result;
      };
      request.onerror = () => reject(request.error ?? new Error('IndexedDB request failed'));
    }
    transaction.oncomplete = () => resolve(result);
    transaction.onerror = () => reject(transaction.error ?? new Error('IndexedDB transaction failed'));
  });
}

function readFallbackQueue(): AnalyticsQueueRecord[] {
  if (typeof window === 'undefined') return [];
  try {
    return JSON.parse(window.localStorage.getItem(FALLBACK_KEY) ?? '[]') as AnalyticsQueueRecord[];
  } catch {
    return [];
  }
}

function writeFallbackQueue(records: AnalyticsQueueRecord[]) {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(FALLBACK_KEY, JSON.stringify(records.slice(-MAX_LOCAL_EVENTS)));
}

function pushRecentEvent(event: AnalyticsEvent) {
  if (typeof window === 'undefined') return;
  const recent = getRecentEvents();
  recent.unshift(event);
  window.localStorage.setItem(RECENT_KEY, JSON.stringify(recent.slice(0, MAX_RECENT_EVENTS)));
}

function pushError(message: string) {
  if (typeof window === 'undefined') return;
  const errors = getRecentErrors();
  errors.unshift(`${new Date().toISOString()} ${message}`);
  window.localStorage.setItem(ERRORS_KEY, JSON.stringify(errors.slice(0, MAX_ERRORS)));
}

export function getRecentEvents(): AnalyticsEvent[] {
  if (typeof window === 'undefined') return [];
  try {
    return JSON.parse(window.localStorage.getItem(RECENT_KEY) ?? '[]') as AnalyticsEvent[];
  } catch {
    return [];
  }
}

export function getRecentErrors(): string[] {
  if (typeof window === 'undefined') return [];
  try {
    return JSON.parse(window.localStorage.getItem(ERRORS_KEY) ?? '[]') as string[];
  } catch {
    return [];
  }
}

async function countIndexedDbEvents(): Promise<number> {
  if (!canUseIndexedDb()) return readFallbackQueue().length;
  try {
    const count = await withStore<number>('readonly', store => store.count());
    return count ?? 0;
  } catch {
    return readFallbackQueue().length;
  }
}

async function readIndexedDbBatch(limit: number): Promise<AnalyticsQueueRecord[]> {
  const records = await withStore<AnalyticsQueueRecord[]>('readonly', store => store.getAll(undefined, limit));
  return records ?? [];
}

async function deleteIndexedDbEvents(ids: string[]) {
  await withStore('readwrite', store => {
    ids.forEach(id => store.delete(id));
  });
}

async function putIndexedDbRecord(record: AnalyticsQueueRecord) {
  await withStore('readwrite', store => store.put(record));
}

export const analyticsQueue = {
  async enqueue(event: AnalyticsEvent) {
    pushRecentEvent(event);
    const record: AnalyticsQueueRecord = {
      event,
      attempts: 0,
      nextAttemptAt: 0,
    };

    if (canUseIndexedDb()) {
      try {
        await putIndexedDbRecord(record);
        const count = await countIndexedDbEvents();
        if (count > MAX_LOCAL_EVENTS) {
          const overflow = await readIndexedDbBatch(count - MAX_LOCAL_EVENTS);
          await deleteIndexedDbEvents(overflow.map(item => item.event.event_id));
        }
        return;
      } catch (error) {
        pushError(`IndexedDB enqueue failed: ${String(error)}`);
      }
    }

    const records = readFallbackQueue().filter(item => item.event.event_id !== event.event_id);
    records.push(record);
    writeFallbackQueue(records);
  },

  async flush(upload: UploadFn, batchSize = 50) {
    const now = Date.now();
    let records: AnalyticsQueueRecord[] = [];
    let useFallback = false;

    if (canUseIndexedDb()) {
      try {
        records = (await readIndexedDbBatch(batchSize)).filter(item => item.nextAttemptAt <= now);
      } catch (error) {
        useFallback = true;
        pushError(`IndexedDB flush read failed: ${String(error)}`);
      }
    } else {
      useFallback = true;
    }

    if (useFallback) {
      records = readFallbackQueue().filter(item => item.nextAttemptAt <= now).slice(0, batchSize);
    }

    if (records.length === 0) return;

    try {
      await upload(records.map(record => record.event));
      window.localStorage.setItem(LAST_FLUSH_KEY, new Date().toISOString());
      if (useFallback) {
        const uploadedIds = new Set(records.map(record => record.event.event_id));
        writeFallbackQueue(readFallbackQueue().filter(record => !uploadedIds.has(record.event.event_id)));
      } else {
        await deleteIndexedDbEvents(records.map(record => record.event.event_id));
      }
    } catch (error) {
      const message = String(error);
      pushError(`Flush failed: ${message}`);
      const updated = records.map(record => ({
        ...record,
        attempts: record.attempts + 1,
        nextAttemptAt: Date.now() + Math.min(300_000, 2 ** record.attempts * 1_000),
        lastError: message,
      }));
      if (useFallback) {
        const byId = new Map(readFallbackQueue().map(record => [record.event.event_id, record]));
        updated.forEach(record => byId.set(record.event.event_id, record));
        writeFallbackQueue([...byId.values()]);
      } else {
        await Promise.all(updated.map(record => putIndexedDbRecord(record)));
      }
    }
  },

  async retryFailed(upload: UploadFn) {
    await this.flush(upload, 50);
  },

  async snapshot(): Promise<AnalyticsDebugSnapshot> {
    return {
      anonymousId: getAnonymousId(),
      sessionId: getSessionId(),
      pendingEvents: await countIndexedDbEvents(),
      recentEvents: getRecentEvents(),
      recentErrors: getRecentErrors(),
      lastFlushAt: typeof window === 'undefined' ? null : window.localStorage.getItem(LAST_FLUSH_KEY),
      consent: AnalyticsConsentManager.getConsent(),
    };
  },
};

