/**
 * Reports offline sync queue — pure, browser-friendly.
 *
 * The certified-report pipeline must work offline. Reports are signed
 * locally, hashed locally, and stored in IndexedDB / localStorage. When
 * the network is back, the queue flushes the pending writes to Supabase.
 *
 * Failure model:
 *   - Network down: items accumulate in the queue. Never lost.
 *   - Network up but server rejects: items stay in the queue with a
 *     `lastError` so the operator can see what failed.
 *   - Conflict on a SIGNED report: never overwrite. The new draft gets
 *     a new `reportId` and the old report is marked VOIDED locally.
 *
 * This module is the heart of "offline-first" for reports. It does NOT
 * talk to Supabase directly — `lib/reports/api.ts` is the transport
 * adapter. The queue is pure and can be unit-tested.
 */

import { CertifiedReport, ReportEvidence, RepairAction } from './types';

export type SyncOp =
  | { kind: 'insertReport'; report: CertifiedReport }
  | { kind: 'updateReport'; report: CertifiedReport }
  | { kind: 'voidReport'; reportId: string; reason: string }
  | { kind: 'insertEvidence'; reportId: string; evidence: ReportEvidence }
  | { kind: 'insertRepairAction'; reportId: string; action: RepairAction };

export interface QueueItem {
  id: string;
  op: SyncOp;
  attempts: number;
  lastError: string | null;
  enqueuedAt: number;
  lastAttemptAt: number | null;
}

export type SyncListener = (items: QueueItem[]) => void;

const KEY = 'meet.reports.sync.queue.v1';

function load(): QueueItem[] {
  if (typeof localStorage === 'undefined') return [];
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed as QueueItem[];
  } catch {
    return [];
  }
}

function save(items: QueueItem[]): void {
  if (typeof localStorage === 'undefined') return;
  localStorage.setItem(KEY, JSON.stringify(items));
}

let _id = 0;
function newId(prefix: string): string {
  _id += 1;
  return `${prefix}_${Date.now().toString(36)}_${_id.toString(36)}`;
}

const listeners = new Set<SyncListener>();
let _cache: QueueItem[] | null = null;

function getCache(): QueueItem[] {
  if (_cache === null) _cache = load();
  return _cache;
}

function setCache(next: QueueItem[]): void {
  _cache = next;
  save(next);
  for (const l of listeners) l(next);
}

/* -------------------------------------------------------------------------- */
/*                            Enqueue / dequeue                                */
/* -------------------------------------------------------------------------- */

export function enqueue(op: SyncOp): QueueItem {
  const items = getCache().slice();
  const item: QueueItem = {
    id: newId('q'),
    op,
    attempts: 0,
    lastError: null,
    enqueuedAt: Date.now(),
    lastAttemptAt: null,
  };
  items.push(item);
  setCache(items);
  return item;
}

export function listQueue(): QueueItem[] {
  return getCache().slice();
}

export function clearQueue(): void {
  setCache([]);
}

export function removeFromQueue(itemId: string): void {
  setCache(getCache().filter((q) => q.id !== itemId));
}

export function markAttempt(itemId: string, error: string | null): void {
  setCache(
    getCache().map((q) =>
      q.id === itemId
        ? {
            ...q,
            attempts: q.attempts + 1,
            lastAttemptAt: Date.now(),
            lastError: error,
          }
        : q,
    ),
  );
}

export function subscribe(listener: SyncListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/* -------------------------------------------------------------------------- */
/*                          Retry policy + backoff                            */
/* -------------------------------------------------------------------------- */

/** Exponential backoff capped at 5 minutes. Returns ms to wait. */
export function backoffMs(attempts: number): number {
  const base = 1000 * Math.pow(2, Math.min(attempts, 9));
  const jitter = Math.random() * 250;
  return Math.min(base + jitter, 5 * 60 * 1000);
}

export interface FlushSummary {
  attempted: number;
  succeeded: number;
  failed: number;
}

/**
 * Pure pipeline: given a list of items and a transport, returns a summary
 * of what would succeed / fail. The actual transport lives in
 * `lib/reports/api.ts` (Supabase). This shape lets us unit-test the
 * queue + retry + backoff without a network.
 */
export async function dryRunFlush(
  items: QueueItem[],
  transport: (op: SyncOp) => Promise<{ ok: true } | { ok: false; error: string }>,
  options: { maxAttempts?: number; now?: () => number } = {},
): Promise<FlushSummary> {
  const maxAttempts = options.maxAttempts ?? 5;
  const now = options.now ?? Date.now;
  let attempted = 0;
  let succeeded = 0;
  let failed = 0;
  for (const item of items) {
    if (item.attempts >= maxAttempts) {
      failed += 1;
      continue;
    }
    attempted += 1;
    const r = await transport(item.op);
    if (r.ok) {
      succeeded += 1;
    } else {
      failed += 1;
    }
  }
  return { attempted, succeeded, failed };
}
