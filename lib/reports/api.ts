/**
 * Reports API — Supabase transport.
 *
 * Thin adapter over `supabase.from(...)` that:
 *   - sends the offline queue when the network is up
 *   - surfaces typed errors instead of throwing raw supabase errors
 *   - never overwrites a SIGNED report (the queue marks them VOIDED
 *     locally when an edit is required)
 *
 * The queue logic lives in `lib/reports/sync.ts`. This file only knows
 * about the wire format.
 */

import { supabase } from '../supabase';
import {
  CertifiedReport,
  ReportEvidence,
  RepairAction,
} from './types';
import { SyncOp, QueueItem, markAttempt, removeFromQueue, listQueue } from './sync';

export type ApiResult<T> = { ok: true; data: T } | { ok: false; error: string };

async function sendOp(op: SyncOp): Promise<{ ok: true } | { ok: false; error: string }> {
  try {
    if (op.kind === 'insertReport') {
      const r = await supabase
        .from('certified_reports')
        .insert(reportToRow(op.report));
      if (r.error) return { ok: false, error: r.error.message };
      return { ok: true };
    }
    if (op.kind === 'updateReport') {
      const r = await supabase
        .from('certified_reports')
        .update(reportToRow(op.report))
        .eq('reportId', op.report.id);
      if (r.error) return { ok: false, error: r.error.message };
      return { ok: true };
    }
    if (op.kind === 'voidReport') {
      const r = await supabase
        .from('certified_reports')
        .update({ status: 'VOIDED' })
        .eq('reportId', op.reportId);
      if (r.error) return { ok: false, error: r.error.message };
      return { ok: true };
    }
    if (op.kind === 'insertEvidence') {
      const r = await supabase
        .from('report_evidence')
        .insert(evidenceToRow(op.reportId, op.evidence));
      if (r.error) return { ok: false, error: r.error.message };
      return { ok: true };
    }
    if (op.kind === 'insertRepairAction') {
      const r = await supabase
        .from('repair_actions')
        .insert(actionToRow(op.reportId, op.action));
      if (r.error) return { ok: false, error: r.error.message };
      return { ok: true };
    }
    return { ok: false, error: `unknown op kind: ${(op as SyncOp).kind}` };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

function reportToRow(r: CertifiedReport): Record<string, unknown> {
  return {
    reportId: r.id,
    vehicleId: r.vehicleId,
    userId: r.userId,
    reportType: r.reportType,
    title: r.title,
    status: r.status,
    odometerKm: r.odometerKm,
    vin: r.vin,
    plate: r.plate,
    generatedAt: r.generatedAt,
    signedAt: r.signedAt,
    pdfUri: r.pdfUri,
    qrVerificationUrl: r.qrVerificationUrl,
    integrityHash: r.integrityHash,
    previousHash: r.previousHash,
    createdAt: r.createdAt,
    updatedAt: r.updatedAt,
  };
}

function evidenceToRow(reportId: string, e: ReportEvidence): Record<string, unknown> {
  return {
    evidenceId: e.id,
    reportId,
    type: e.type,
    label: e.label,
    description: e.description,
    uri: e.uri,
    hash: e.hash,
    capturedAt: e.capturedAt,
    lat: e.lat,
    lng: e.lng,
  };
}

function actionToRow(reportId: string, a: RepairAction): Record<string, unknown> {
  return {
    actionId: a.id,
    reportId,
    actionType: a.actionType,
    component: a.component,
    dtcRelated: a.dtcRelated,
    description: a.description,
    partUsed: a.partUsed,
    supplier: a.supplier,
    mechanic: a.mechanic,
    cost: a.cost,
    currency: a.currency,
    warrantyDays: a.warrantyDays,
    createdAt: a.createdAt,
  };
}

/* -------------------------------------------------------------------------- */
/*                            Verifier (read-only)                            */
/* -------------------------------------------------------------------------- */

export interface VerificationResult {
  ok: boolean;
  reportId: string;
  integrityHash: string;
  expectedHash: string | null;
  status: string | null;
  generatedAt: number | null;
  signedAt: number | null;
  vehicleId: string | null;
  reportType: string | null;
  reason?: string;
}

/**
 * Looks up a report by `reportId` and compares its stored
 * `integrityHash` against the one encoded in the QR. Returns a
 * structured result so the UI can render the verdict cleanly.
 */
export async function verifyReport(
  reportId: string,
  integrityHash: string,
): Promise<ApiResult<VerificationResult>> {
  try {
    const { data, error } = await supabase
      .from('certified_reports')
      .select(
        'reportId, integrityHash, status, generatedAt, signedAt, vehicleId, reportType',
      )
      .eq('reportId', reportId)
      .maybeSingle();
    if (error) return { ok: false, error: error.message };
    if (!data) {
      return {
        ok: true,
        data: {
          ok: false,
          reportId,
          integrityHash,
          expectedHash: null,
          status: null,
          generatedAt: null,
          signedAt: null,
          vehicleId: null,
          reportType: null,
          reason: 'Report not found in the verification database.',
        },
      };
    }
    const ok = data.integrityHash === integrityHash;
    return {
      ok: true,
      data: {
        ok,
        reportId,
        integrityHash,
        expectedHash: data.integrityHash,
        status: data.status,
        generatedAt: data.generatedAt,
        signedAt: data.signedAt,
        vehicleId: data.vehicleId,
        reportType: data.reportType,
        reason: ok ? undefined : 'integrityHash mismatch — content was modified after signing.',
      },
    };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/* -------------------------------------------------------------------------- */
/*                              Queue flush                                   */
/* -------------------------------------------------------------------------- */

export interface FlushOutcome {
  attempted: number;
  succeeded: number;
  failed: number;
  remaining: number;
}

/**
 * Drains the offline queue, attempting each item. Items that fail
 * stay in the queue with an updated `attempts` / `lastError` so the
 * next flush can retry.
 */
export async function flushQueue(): Promise<ApiResult<FlushOutcome>> {
  let attempted = 0;
  let succeeded = 0;
  let failed = 0;
  for (const item of listQueue()) {
    const r: { ok: true } | { ok: false; error: string } = await sendOp(item.op);
    attempted += 1;
    if (r.ok === true) {
      succeeded += 1;
      removeFromQueue(item.id);
    } else {
      failed += 1;
      markAttempt(item.id, r.error);
    }
  }
  return {
    ok: true,
    data: { attempted, succeeded, failed, remaining: listQueue().length },
  };
}
