/**
 * lib/reports/hash — SHA-256 + canonical hashing for the report chain.
 *
 * The integrity chain works like this:
 *
 *   1. For each report we compute a canonical JSON of the *content* the
 *      report attests to (vehicle, snapshot, evidence, repair actions,
 *      privacy, etc.).
 *   2. SHA-256 hex of that canonical string is `integrityHash`.
 *   3. Each report also carries `previousHash` = the integrityHash of the
 *      prior signed report for the same vehicle. This produces a hash
 *      chain that any tampering will break.
 *   4. After the report is signed, the integrityHash is **frozen**. Any
 *      edit re-computes the hash AND bumps a version. The previous
 *      version is marked VOIDED in the persistence layer.
 *
 * Why a custom canonicalization: the goal is byte-exact reproducibility
 * across web (TS), Android (Kotlin), and any future server. We avoid
 * `JSON.stringify` directly because:
 *
 *   - Object key order is not guaranteed across engines.
 *   - Whitespace and Unicode escapes are engine-specific.
 *   - Floats serialize with different precision in some runtimes.
 *
 * So we canonicalize to a sorted-keys / no-whitespace / numeric-strings
 * shape and pipe that into SHA-256. The canonical form itself is
 * documented in `canonicalize()` below.
 */

import {
  CertifiedReport,
  DiagnosticSnapshot,
  DraftReportInput,
  ReportEvidence,
  RepairAction,
  ReportPrivacyOptions,
  ReportStatus,
  ReportType,
  PeritajeChecklist,
} from './types';

/* -------------------------------------------------------------------------- */
/*                              Hash primitive                                */
/* -------------------------------------------------------------------------- */

/**
 * Async SHA-256 hex digest. Works in browsers (Web Crypto) and in Node 20+
 * (globalThis.crypto.subtle). The canonicalization pre-step is fully
 * deterministic, so the result is reproducible across environments.
 */
export async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await globalThis.crypto.subtle.digest('SHA-256', data);
  const bytes = new Uint8Array(digest);
  let out = '';
  for (let i = 0; i < bytes.length; i++) {
    out += bytes[i].toString(16).padStart(2, '0');
  }
  return out;
}

/* -------------------------------------------------------------------------- */
/*                              Canonicalization                              */
/* -------------------------------------------------------------------------- */

const CANONICAL_SEP = '|';

/**
 * Stable stringification: deterministic key order, no whitespace, types
 * coerced explicitly. We intentionally keep this small and explicit so a
 * reader can audit the format without jumping through the standard.
 */
function canonicalize(value: unknown): string {
  if (value === null) return 'null';
  if (value === undefined) return 'undefined';
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return 'null';
    return JSON.stringify(value);
  }
  if (typeof value === 'string') return JSON.stringify(value);
  if (Array.isArray(value)) {
    const parts = value.map(canonicalize);
    return '[' + parts.join(CANONICAL_SEP) + ']';
  }
  if (typeof value === 'object') {
    const obj = value as Record<string, unknown>;
    const keys = Object.keys(obj).sort();
    const parts = keys.map((k) => JSON.stringify(k) + ':' + canonicalize(obj[k]));
    return '{' + parts.join(CANONICAL_SEP) + '}';
  }
  return JSON.stringify(String(value));
}

/* -------------------------------------------------------------------------- */
/*                         Hash for a DiagnosticSnapshot                      */
/* -------------------------------------------------------------------------- */

/**
 * Mirrors the Kotlin `computeHash` in DiagnosticSnapshot.kt. The list of
 * fields and the `|`-separated concatenation are part of the contract;
 * any change here MUST ship with a paired change in the Android side.
 */
export function canonicalSnapshotString(snap: DiagnosticSnapshot): string {
  return [
    snap.vehicleId,
    snap.sessionId ?? '',
    snap.createdAtMs.toString(),
    snap.dtcsActive.slice().sort().join(','),
    snap.dtcsPending.slice().sort().join(','),
    snap.dtcsPermanent.slice().sort().join(','),
    JSON.stringify(
      Object.fromEntries(
        Object.entries(snap.freezeFramePidValues).sort(([a], [b]) =>
          a.localeCompare(b),
        ),
      ),
    ),
    JSON.stringify(
      Object.fromEntries(
        Object.entries(snap.readiness).sort(([a], [b]) => a.localeCompare(b)),
      ),
    ),
    snap.ecuVoltage?.toString() ?? '',
    snap.rpm?.toString() ?? '',
    snap.coolantTempC?.toString() ?? '',
    snap.speedKph?.toString() ?? '',
    snap.engineLoadPct?.toString() ?? '',
    snap.fuelTrimStft?.toString() ?? '',
    snap.fuelTrimLtft?.toString() ?? '',
  ].join('|');
}

export async function hashSnapshot(snap: DiagnosticSnapshot): Promise<string> {
  return sha256Hex(canonicalSnapshotString(snap));
}

/* -------------------------------------------------------------------------- */
/*                          Evidence hashing                                  */
/* -------------------------------------------------------------------------- */

export async function hashEvidence(ev: ReportEvidence): Promise<string> {
  return sha256Hex(
    canonicalize({
      type: ev.type,
      label: ev.label,
      description: ev.description,
      uri: ev.uri,
      hash: ev.hash ?? '',
      capturedAt: ev.capturedAt,
    }),
  );
}

/* -------------------------------------------------------------------------- */
/*                      Hash for a DraftReport (the report itself)             */
/* -------------------------------------------------------------------------- */

export function canonicalReportString(
  draft: DraftReportInput,
  snapshotHash: string | null,
  evidenceHashes: string[],
  repairActionHashes: string[],
  peritajeHash: string | null,
  previousHash: string | null,
): string {
  const privacy: ReportPrivacyOptions = draft.privacy;
  return [
    draft.vehicleId,
    draft.userId,
    draft.reportType,
    draft.title,
    draft.odometerKm?.toString() ?? '',
    draft.vin ?? '',
    draft.plate ?? '',
    privacy.redactVin ? '1' : '0',
    privacy.redactPlate ? '1' : '0',
    privacy.redactLocation ? '1' : '0',
    privacy.publicShare ? '1' : '0',
    snapshotHash ?? 'NO_SNAPSHOT',
    evidenceHashes.join(','),
    repairActionHashes.join(','),
    peritajeHash ?? 'NO_PERITAJE',
    previousHash ?? 'GENESIS',
    draft.notes,
  ].join('|');
}

export async function hashReportDraft(
  draft: DraftReportInput,
  snapshotHash: string | null,
  evidenceHashes: string[],
  repairActionHashes: string[],
  peritajeHash: string | null,
  previousHash: string | null,
): Promise<string> {
  return sha256Hex(
    canonicalReportString(
      draft,
      snapshotHash,
      evidenceHashes,
      repairActionHashes,
      peritajeHash,
      previousHash,
    ),
  );
}

/* -------------------------------------------------------------------------- */
/*                          Hash for a RepairAction                           */
/* -------------------------------------------------------------------------- */

export async function hashRepairAction(action: RepairAction): Promise<string> {
  return sha256Hex(
    canonicalize({
      actionType: action.actionType,
      component: action.component,
      dtcRelated: action.dtcRelated ?? '',
      description: action.description,
      partUsed: action.partUsed ?? '',
      supplier: action.supplier ?? '',
      mechanic: action.mechanic ?? '',
      cost: action.cost,
      currency: action.currency,
      warrantyDays: action.warrantyDays,
    }),
  );
}

/* -------------------------------------------------------------------------- */
/*                          Hash for PeritajeChecklist                        */
/* -------------------------------------------------------------------------- */

export async function hashPeritaje(p: PeritajeChecklist): Promise<string> {
  return sha256Hex(
    canonicalize({
      overallScore: p.overallScore,
      verdict: p.verdict,
      confidence: p.confidence,
      criticalAlerts: p.criticalAlerts.slice().sort(),
      recommendation: p.recommendation,
      sectionScores: p.sectionScores
        .slice()
        .sort((a, b) => a.section.localeCompare(b.section))
        .map((s) => ({
          section: s.section,
          score: s.score,
          notes: s.notes,
          evidenceCount: s.evidenceUris.length,
        })),
    }),
  );
}

/* -------------------------------------------------------------------------- */
/*                          Hash for a signature                              */
/* -------------------------------------------------------------------------- */

export async function hashSignature(
  reportHash: string,
  signerName: string,
  signerRole: string,
  signedAt: number,
  deviceIdHash: string,
): Promise<string> {
  return sha256Hex(
    canonicalize({
      reportHash,
      signerName,
      signerRole,
      signedAt,
      deviceIdHash,
    }),
  );
}

/* -------------------------------------------------------------------------- */
/*                            Chain helpers                                   */
/* -------------------------------------------------------------------------- */

/**
 * Verifies that the candidate's previousHash matches the previous report's
 * integrityHash. Returns true iff the chain is intact for the supplied
 * reports sorted by generatedAt.
 */
export function verifyChain(
  reports: Pick<CertifiedReport, 'id' | 'vehicleId' | 'generatedAt' | 'integrityHash' | 'previousHash'>[],
): { ok: boolean; brokenAt: string | null } {
  const sorted = reports
    .slice()
    .sort((a, b) => a.generatedAt - b.generatedAt);
  let prev: string | null = null;
  for (const r of sorted) {
    if (r.previousHash !== prev) {
      return { ok: false, brokenAt: r.id };
    }
    prev = r.integrityHash;
  }
  return { ok: true, brokenAt: null };
}

/**
 * Build a dummy device id hash for tests. In production this is supplied
 * by the platform (Android's Settings.Secure.ANDROID_ID hashed with
 * HKDF-SHA-256, or a per-install UUID in the web).
 */
export async function hashDeviceId(deviceId: string): Promise<string> {
  return sha256Hex('device::' + deviceId);
}
