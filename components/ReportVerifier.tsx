/**
 * Report Verifier UI.
 *
 * Drop-in component that takes a QR payload (the URL the QR points to,
 * or the raw `meet://verify?reportId=...&hash=...` payload) and
 * verifies it against the Supabase database. Renders a clean verdict:
 *   - OK   : the hash matches. The report is genuine and unmodified.
 *   - FAIL : the hash differs. The content was modified post-sign.
 *   - MISS : the report is not in the database (offline / not yet synced).
 *
 * The component is data-only: it does not own the queue or the
 * transport. It calls `verifyReport(...)` from `lib/reports/api.ts`.
 */

import React, { useEffect, useState } from 'react';
import { ShieldCheck, ShieldAlert, AlertCircle, Search } from 'lucide-react';

import { verifyReport, VerificationResult, ApiResult } from '../lib/reports/api';

export interface ReportVerifierProps {
  /** Optional initial payload, e.g. parsed from a QR scanner. */
  initialPayload?: string;
  /** Allow the user to paste a payload manually when no QR scanner is present. */
  allowManualEntry?: boolean;
}

type Phase = 'idle' | 'verifying' | 'ok' | 'fail' | 'miss' | 'error';

function parsePayload(raw: string): { reportId: string; integrityHash: string } | null {
  // Accept either `meet://verify?reportId=...&hash=...` or a bare
  // `reportId:hash` pair.
  if (!raw) return null;
  try {
    if (raw.startsWith('meet://')) {
      const url = new URL(raw);
      const reportId = url.searchParams.get('reportId') ?? '';
      const hash = url.searchParams.get('hash') ?? '';
      if (!reportId || !hash) return null;
      return { reportId, integrityHash: hash };
    }
    if (raw.startsWith('http')) {
      const url = new URL(raw);
      const reportId = url.searchParams.get('reportId') ?? '';
      const hash = url.searchParams.get('hash') ?? '';
      if (!reportId || !hash) return null;
      return { reportId, integrityHash: hash };
    }
    const [rid, hash] = raw.split(':');
    if (rid && hash) return { reportId: rid, integrityHash: hash };
    return null;
  } catch {
    return null;
  }
}

export function ReportVerifier({
  initialPayload,
  allowManualEntry = true,
}: ReportVerifierProps) {
  const [payload, setPayload] = useState<string>(initialPayload ?? '');
  const [phase, setPhase] = useState<Phase>('idle');
  const [result, setResult] = useState<VerificationResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!initialPayload) return;
    void runVerify(initialPayload);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialPayload]);

  const runVerify = async (raw: string) => {
    const parsed = parsePayload(raw);
    if (!parsed) {
      setPhase('error');
      setError('No se pudo parsear el QR / payload.');
      return;
    }
    setPhase('verifying');
    setError(null);
    const r: ApiResult<VerificationResult> = await verifyReport(
      parsed.reportId,
      parsed.integrityHash,
    );
    if (r.ok === false) {
      setPhase('error');
      setError(r.error);
      return;
    }
    setResult(r.data);
    if (r.data.ok) {
      setPhase('ok');
    } else if (r.data.reason && r.data.reason.includes('not found')) {
      setPhase('miss');
    } else {
      setPhase('fail');
    }
  };

  return (
    <div className="p-5 max-w-2xl" data-testid="report-verifier">
      <div className="flex items-center gap-2 mb-4">
        <ShieldCheck size={20} className="text-forge-500" />
        <h2 className="text-lg font-bold text-white">Verificar Reporte Certificado</h2>
      </div>

      {allowManualEntry && (
        <div className="space-y-2 mb-4">
          <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
            Payload (QR o URL)
          </label>
          <div className="flex gap-2">
            <input
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              placeholder="meet://verify?reportId=...&hash=..."
              className="flex-1 bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none"
              data-testid="verifier-payload-input"
            />
            <button
              onClick={() => void runVerify(payload)}
              disabled={!payload || phase === 'verifying'}
              className="flex items-center gap-1 px-4 py-2 rounded-lg text-xs font-mono font-bold bg-forge-500 text-black disabled:opacity-30 disabled:cursor-not-allowed hover:bg-forge-600 transition-all"
              data-testid="verifier-submit"
            >
              <Search size={14} /> Verificar
            </button>
          </div>
        </div>
      )}

      {phase === 'verifying' && (
        <div className="rounded-lg border border-cyan-500/30 bg-cyan-500/5 p-4 text-xs text-cyan-200">
          Verificando hash contra la base de datos…
        </div>
      )}

      {phase === 'ok' && result && (
        <div
          className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-4 text-xs text-emerald-200 space-y-1"
          data-testid="verifier-result-ok"
        >
          <div className="flex items-center gap-2 font-mono font-bold uppercase tracking-wide">
            <ShieldCheck size={16} /> Reporte verificado
          </div>
          <div>Estado: {result.status}</div>
          <div>Tipo: {result.reportType}</div>
          <div>Vehículo: {result.vehicleId}</div>
          <div className="font-mono text-[10px] text-emerald-300/80 break-all">
            Hash: {result.integrityHash}
          </div>
        </div>
      )}

      {phase === 'fail' && result && (
        <div
          className="rounded-lg border border-red-500/40 bg-red-500/10 p-4 text-xs text-red-200 space-y-1"
          data-testid="verifier-result-fail"
        >
          <div className="flex items-center gap-2 font-mono font-bold uppercase tracking-wide">
            <ShieldAlert size={16} /> Hash no coincide
          </div>
          <div>{result.reason ?? 'Reporte modificado tras firma.'}</div>
          <div className="font-mono text-[10px] break-all">
            Esperado: {result.expectedHash}
          </div>
          <div className="font-mono text-[10px] break-all">
            Recibido: {result.integrityHash}
          </div>
        </div>
      )}

      {phase === 'miss' && result && (
        <div
          className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-4 text-xs text-amber-200 space-y-1"
          data-testid="verifier-result-miss"
        >
          <div className="flex items-center gap-2 font-mono font-bold uppercase tracking-wide">
            <AlertCircle size={16} /> Reporte no encontrado
          </div>
          <div>
            El reporte con id <span className="font-mono">{result.reportId}</span>{' '}
            no aparece en la base de datos de verificación. Puede que aún no
            haya sincronizado o que el QR sea de otra marca.
          </div>
        </div>
      )}

      {phase === 'error' && error && (
        <div
          className="rounded-lg border border-red-500/40 bg-red-500/10 p-4 text-xs text-red-200"
          data-testid="verifier-result-error"
        >
          <div className="flex items-center gap-2 font-mono font-bold uppercase tracking-wide">
            <AlertCircle size={16} /> Error
          </div>
          <div className="font-mono text-[10px] break-all">{error}</div>
        </div>
      )}
    </div>
  );
}
