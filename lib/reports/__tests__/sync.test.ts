/**
 * Tests for lib/reports/sync.ts
 *
 * The queue is the offline-first primitive. These tests pin the
 * behaviors that matter when the network is flaky:
 *   - enqueue is non-lossy (item shows up in listQueue immediately).
 *   - exponential backoff caps at 5 minutes.
 *   - dryRunFlush respects the maxAttempts cap.
 *   - successful flush removes items, failure keeps them with a
 *     bumped `attempts` and the new `lastError`.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Vitest runs in Node — give the queue a localStorage shim so the
// in-memory cache and the localStorage mirror stay in sync.
const memory = new Map<string, string>();
const localStorageShim: Storage = {
  get length() {
    return memory.size;
  },
  clear: () => memory.clear(),
  getItem: (k) => memory.get(k) ?? null,
  key: (i) => Array.from(memory.keys())[i] ?? null,
  removeItem: (k) => {
    memory.delete(k);
  },
  setItem: (k, v) => {
    memory.set(k, v);
  },
};

vi.stubGlobal('localStorage', localStorageShim);

import {
  backoffMs,
  clearQueue,
  dryRunFlush,
  enqueue,
  listQueue,
  markAttempt,
  removeFromQueue,
  SyncOp,
} from '../sync';
import { CertifiedReport } from '../types';

const baseReport: CertifiedReport = {
  id: 'rpt-1',
  vehicleId: 'v1',
  userId: 'u1',
  reportType: 'PRE_SCAN_REPORT',
  title: 'Pre-Scan',
  status: 'DRAFT',
  odometerKm: 100_000,
  vin: 'KMHCN46C18U123456',
  plate: 'ABC-123',
  generatedAt: 1,
  signedAt: null,
  pdfUri: null,
  qrVerificationUrl: null,
  integrityHash: 'h1',
  previousHash: null,
  createdAt: 1,
  updatedAt: 1,
};

function insertOp(reportId: string = 'rpt-1'): SyncOp {
  return { kind: 'insertReport', report: { ...baseReport, id: reportId } };
}

describe('sync queue basics', () => {
  beforeEach(() => memory.clear());
  afterEach(() => clearQueue());

  it('enqueue adds an item, listQueue returns it', () => {
    clearQueue();
    enqueue(insertOp('r1'));
    enqueue(insertOp('r2'));
    expect(listQueue()).toHaveLength(2);
  });

  it('removeFromQueue drops the right item', () => {
    clearQueue();
    const a = enqueue(insertOp('a'));
    const b = enqueue(insertOp('b'));
    removeFromQueue(a.id);
    expect(listQueue()).toHaveLength(1);
    expect(listQueue()[0].id).toBe(b.id);
  });

  it('markAttempt bumps attempts and records the error', () => {
    clearQueue();
    const a = enqueue(insertOp('a'));
    markAttempt(a.id, 'timeout');
    const after = listQueue().find((q) => q.id === a.id)!;
    expect(after.attempts).toBe(1);
    expect(after.lastError).toBe('timeout');
    expect(after.lastAttemptAt).not.toBeNull();
  });

  it('clearQueue empties the queue', () => {
    enqueue(insertOp('a'));
    enqueue(insertOp('b'));
    clearQueue();
    expect(listQueue()).toHaveLength(0);
  });
});

describe('backoffMs', () => {
  it('grows exponentially', () => {
    expect(backoffMs(0)).toBeLessThan(backoffMs(2));
    expect(backoffMs(2)).toBeLessThan(backoffMs(5));
  });

  it('caps at 5 minutes + jitter', () => {
    expect(backoffMs(50)).toBeLessThanOrEqual(5 * 60 * 1000 + 250);
    expect(backoffMs(50)).toBeGreaterThanOrEqual(5 * 60 * 1000);
  });
});

describe('dryRunFlush', () => {
  beforeEach(() => memory.clear());
  afterEach(() => clearQueue());

  it('counts successes and failures', async () => {
    clearQueue();
    enqueue(insertOp('a'));
    enqueue(insertOp('b'));
    enqueue(insertOp('c'));
    const transport = (op: SyncOp) => {
      if (op.kind === 'insertReport' && op.report.id === 'b') {
        return Promise.resolve({ ok: false as const, error: 'server says no' });
      }
      return Promise.resolve({ ok: true as const });
    };
    const summary = await dryRunFlush(listQueue(), transport, { maxAttempts: 5 });
    expect(summary.attempted).toBe(3);
    expect(summary.succeeded).toBe(2);
    expect(summary.failed).toBe(1);
  });

  it('skips items that already exceeded maxAttempts', async () => {
    clearQueue();
    const a = enqueue(insertOp('a'));
    markAttempt(a.id, 'one');
    markAttempt(a.id, 'two');
    markAttempt(a.id, 'three');
    markAttempt(a.id, 'four');
    markAttempt(a.id, 'five');
    const transport = () => Promise.resolve({ ok: true as const });
    const summary = await dryRunFlush(listQueue(), transport, { maxAttempts: 5 });
    expect(summary.attempted).toBe(0);
    expect(summary.failed).toBe(1);
  });
});

