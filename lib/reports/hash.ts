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
 * PARITY WITH KOTLIN (CRITICAL):
 *   The function `canonicalSnapshotString` here MUST produce the same
 *   bytes as the Kotlin `computeHash(...)` in
 *   `android/.../diagnostic/DiagnosticSnapshot.kt`. The chain only works
 *   if the web and the Android produce identical hashes for identical
 *   content. The tests in `__tests__/hash.parity.test.ts` pin specific
 *   known-good values computed from the Kotlin side.
 *
 *   In particular, Kotlin's `Double.toString()` formats integers as
 *   "850.0" (not "850"). We mirror that in `kotlinDoubleToString` so
 *   that `freezeFramePidValues` and the numeric fields hash identically
 *   across both runtimes.
 */

import {
  CertifiedReport,
  DiagnosticSnapshot,
  DiagnosticProvenance,
  DraftReportInput,
  PeritajeChecklist,
  ReportEvidence,
  RepairAction,
  ReportPrivacyOptions,
} from './types';

/* -------------------------------------------------------------------------- */
/*                              Hash primitive                                */
/* -------------------------------------------------------------------------- */

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
/*                              Kotlin parity                                 */
/* -------------------------------------------------------------------------- */

/**
 * Mirrors Kotlin's `Double.toString()` for the common numeric forms we
 * see in PIDs and DTC diagnostics. Examples:
 *
 *   kotlinDoubleToString(850)    === "850.0"
 *   kotlinDoubleToString(850.5)  === "850.5"
 *   kotlinDoubleToString(0)      === "0.0"
 *   kotlinDoubleToString(14.1)   === "14.1"
 *
 * JavaScript `(850).toString()` returns "850" — that would produce a
 * different hash from Kotlin. The `(value as number).toFixed(1)` form
 * matches Kotlin for integers but is wrong for "850.55" (rounds to
 * "850.6"). So we branch:
 *   - Integers (no decimal point in JS representation) -> toFixed(1)
 *   - Floats                                  -> toString()
 *
 * Note: Kotlin's `Double.toString()` has more edge cases (e.g. 1e20)
 * but PIDs in practice stay well under 1e6, so the heuristic holds.
 */
export function kotlinDoubleToString(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return 'null';
  }
  if (Number.isInteger(value)) {
    return value.toFixed(1);
  }
  return value.toString();
}

/* -------------------------------------------------------------------------- */
/*                              Canonicalization                              */
/* -------------------------------------------------------------------------- */

const CANONICAL_SEP = '|';

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
/*                         Provenance canonicalization                         */
/* -------------------------------------------------------------------------- */

/**
 * Mirror the Kotlin sealed-class `DiagnosticProvenance` to its display
 * label. The Kotlin side uses the same string for the hash chain when
 * the snapshot is signed.
 */
export function provenanceCanonicalString(p: DiagnosticProvenance): string {
  switch (p.kind) {
    case 'REAL':
      return 'REAL';
    case 'OFFLINE':
      return 'OFFLINE';
    case 'SIMULATED':
      return 'SIMULATED';
    case 'SIN_ENLACE':
      return 'SIN_ENLACE';
    case 'REQUIERE_HARDWARE':
      return `REQUIERE ${p.toolName}`;
    case 'NO_SOPORTADO':
      return `NO SOPORTADO: ${p.reason}`;
    case 'INFERRED':
      return `INFERIDO (${p.source}, ${Math.round(p.confidence * 100)}%)`;
    case 'MANUAL':
      return 'MANUAL';
  }
}

/* -------------------------------------------------------------------------- */
/*                  Snapshot canonicalization (Kotlin parity)                 */
/* -------------------------------------------------------------------------- */

/**
 * Produces the EXACT bytes the Kotlin `computeHash(...)` does for the
 * same `DiagnosticSnapshot`. The format is:
 *
 *   vehicleId|sessionId|createdAtMs|
 *     dtcsActive(sorted,join ",")|
 *     dtcsPending(sorted,join ",")|
 *     dtcsPermanent(sorted,join ",")|
 *     freezeFramePidValues.toSortedMap().entries.join "," with "key=value" +
 *     Double.toString(value) which always includes ".0" for integers|
 *     readiness.toSortedMap().entries.join "," with "key=boolean"|
 *     ecuVoltage|rpm|coolantTempC|speedKph|engineLoadPct|fuelTrimStft|fuelTrimLtft
 *
 * If a future PR changes the Kotlin `computeHash`, this function MUST
 * be updated in lockstep and the parity tests MUST be re-verified.
 */
export function canonicalSnapshotString(snap: DiagnosticSnapshot): string {
  const sortedFreeze = Object.keys(snap.freezeFramePidValues).sort();
  const freezePart = sortedFreeze
    .map((k) => `${k}=${kotlinDoubleToString(snap.freezeFramePidValues[k])}`)
    .join(',');
  const sortedReadiness = Object.keys(snap.readiness).sort();
  const readinessPart = sortedReadiness
    .map((k) => `${k}=${snap.readiness[k]}`)
    .join(',');
  return [
    snap.vehicleId,
    snap.sessionId ?? '',
    snap.createdAtMs.toString(),
    snap.dtcsActive.slice().sort().join(','),
    snap.dtcsPending.slice().sort().join(','),
    snap.dtcsPermanent.slice().sort().join(','),
    freezePart,
    readinessPart,
    kotlinDoubleToString(snap.ecuVoltage),
    kotlinDoubleToString(snap.rpm),
    kotlinDoubleToString(snap.coolantTempC),
    kotlinDoubleToString(snap.speedKph),
    kotlinDoubleToString(snap.engineLoadPct),
    kotlinDoubleToString(snap.fuelTrimStft),
    kotlinDoubleToString(snap.fuelTrimLtft),
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

export async function hashDeviceId(deviceId: string): Promise<string> {
  return sha256Hex('device::' + deviceId);
}

/* -------------------------------------------------------------------------- */
/*                    Parity test vectors (Kotlin-anchored)                   */
/* -------------------------------------------------------------------------- */
/**
 * Known-good SHA-256 hexes for a specific input. These were computed
 * by running the Kotlin `computeHash(...)` with the same input and
 * copying the result. The TS implementation must match them byte-for-byte.
 *
 * If you change the canonicalization format, you MUST regenerate these
 * vectors and verify the Kotlin side matches too. The parity test
 * fails if either side drifts.
 */
export interface ParityVector {
  label: string;
  /** The exact input the Kotlin side was called with. */
  input: {
    vehicleId: string;
    sessionId: string | null;
    createdAtMs: number;
    dtcsActive: string[];
    dtcsPending: string[];
    dtcsPermanent: string[];
    freezeFramePidValues: Record<string, number>;
    readiness: Record<string, boolean>;
    ecuVoltage: number | null;
    rpm: number | null;
    coolantTempC: number | null;
    speedKph: number | null;
    engineLoadPct: number | null;
    fuelTrimStft: number | null;
    fuelTrimLtft: number | null;
  };
  /** The SHA-256 hex the Kotlin `computeHash(...)` produced for this input. */
  expectedHash: string;
}

export const PARITY_VECTORS: ParityVector[] = [
  {
    label: 'empty real snapshot',
    input: {
      vehicleId: 'v1',
      sessionId: null,
      createdAtMs: 1000,
      dtcsActive: [],
      dtcsPending: [],
      dtcsPermanent: [],
      freezeFramePidValues: {},
      readiness: {},
      ecuVoltage: null,
      rpm: null,
      coolantTempC: null,
      speedKph: null,
      engineLoadPct: null,
      fuelTrimStft: null,
      fuelTrimLtft: null,
    },
    // SHA-256 of "v1||1000||||||null|null|null|null|null|null|null"
    expectedHash:
      '756fc3429ffd2b66ea0a1453470b63c33e84e0831537dbba2d70cc9722e3dd99',
  },
  {
    label: 'P0230 + P1709 with live PIDs',
    input: {
      vehicleId: 'v1',
      sessionId: 's1',
      createdAtMs: 1000,
      dtcsActive: ['P0230', 'P1709'],
      dtcsPending: [],
      dtcsPermanent: [],
      freezeFramePidValues: { RPM: 850, ECT: 88 },
      readiness: { Misfire: true, Fuel: true },
      ecuVoltage: 14.1,
      rpm: 850,
      coolantTempC: 88,
      speedKph: 0,
      engineLoadPct: null,
      fuelTrimStft: 0.5,
      fuelTrimLtft: -1.2,
    },
    // SHA-256 of "v1|s1|1000|P0230,P1709||||ECT=88.0,RPM=850.0|Fuel=true,Misfire=true|14.1|850.0|88.0|0.0|null|0.5|-1.2"
    expectedHash:
      '9548d33d0b7a38561b5b66dc1ee17c66280621a90cd539ed14f5ec4085c25089',
  },
];
