import { ANALYTICS_EVENTS } from './analyticsEvents';
import type { AnalyticsEventName } from './analyticsTypes';

const ANONYMOUS_ID_KEY = 'meet_analytics_anonymous_id';
const FIRST_OPEN_KEY = 'meet_analytics_first_open_at';
const LAST_OPEN_KEY = 'meet_analytics_last_open_at';
const OPEN_COUNT_KEY = 'meet_analytics_open_count';
const SESSION_STORAGE_KEY = 'meet_analytics_session_id';
const RETENTION_DAYS = [1, 3, 7, 14, 30] as const;

type RetentionDay = typeof RETENTION_DAYS[number];

export interface RetentionSignal {
  eventName: AnalyticsEventName;
  daysSinceFirstOpen: number;
  firstOpenAt: string;
  openCount: number;
}

function generateId(prefix: string): string {
  const cryptoId = typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}_${cryptoId}`;
}

function storageNumber(key: string, fallback: number): number {
  const raw = window.localStorage.getItem(key);
  const value = raw ? Number(raw) : NaN;
  return Number.isFinite(value) ? value : fallback;
}

export function getAnonymousId(): string {
  if (typeof window === 'undefined') return generateId('anon');
  const existing = window.localStorage.getItem(ANONYMOUS_ID_KEY);
  if (existing) return existing;
  const created = generateId('anon');
  window.localStorage.setItem(ANONYMOUS_ID_KEY, created);
  return created;
}

export function getSessionId(): string {
  if (typeof window === 'undefined') return generateId('sess');
  const existing = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
  if (existing) return existing;
  const created = generateId('sess');
  window.sessionStorage.setItem(SESSION_STORAGE_KEY, created);
  return created;
}

export function markAppOpen(): RetentionSignal[] {
  if (typeof window === 'undefined') return [];
  const now = Date.now();
  const firstOpen = storageNumber(FIRST_OPEN_KEY, now);
  const openCount = storageNumber(OPEN_COUNT_KEY, 0) + 1;
  window.localStorage.setItem(FIRST_OPEN_KEY, String(firstOpen));
  window.localStorage.setItem(LAST_OPEN_KEY, String(now));
  window.localStorage.setItem(OPEN_COUNT_KEY, String(openCount));

  const daysSinceFirstOpen = Math.floor((now - firstOpen) / 86_400_000);
  return RETENTION_DAYS.flatMap((day: RetentionDay) => {
    const key = `meet_analytics_retention_d${day}_sent`;
    if (daysSinceFirstOpen < day || window.localStorage.getItem(key) === 'true') return [];
    window.localStorage.setItem(key, 'true');
    const eventName = ANALYTICS_EVENTS[`RETENTION_D${day}_RETURNED` as Uppercase<AnalyticsEventName>];
    return [{
      eventName,
      daysSinceFirstOpen,
      firstOpenAt: new Date(firstOpen).toISOString(),
      openCount,
    }];
  });
}

export function getOpenStats() {
  if (typeof window === 'undefined') {
    return { firstOpenAt: null, lastOpenAt: null, openCount: 0 };
  }
  const firstOpen = window.localStorage.getItem(FIRST_OPEN_KEY);
  const lastOpen = window.localStorage.getItem(LAST_OPEN_KEY);
  return {
    firstOpenAt: firstOpen ? new Date(Number(firstOpen)).toISOString() : null,
    lastOpenAt: lastOpen ? new Date(Number(lastOpen)).toISOString() : null,
    openCount: storageNumber(OPEN_COUNT_KEY, 0),
  };
}

