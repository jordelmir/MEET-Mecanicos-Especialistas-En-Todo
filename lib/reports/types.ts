/**
 * lib/reports — shared types for the certified-report pipeline.
 *
 * These types mirror the SQL schema in
 * `supabase/migrations/20260704000000_reports_foundations.sql` and the
 * Kotlin types in `android/.../diagnostic/DiagnosticSnapshot.kt`. We keep
 * the field names camelCase-quoted in the DB (Android-friendly) and
 * use the same names here on the TypeScript side.
 *
 * The pipeline is, in order:
 *
 *   1. Build a DRAFT report (lib/reports/generate.ts).
 *   2. Capture evidence (PHOTO, OBD_SNAPSHOT, MEASUREMENT, ...).
 *   3. Compute SHA-256 over canonical JSON (lib/reports/hash.ts).
 *   4. Sign the report (ReportSignature). After signing the report is
 *      immutable; any later change requires a new version + VOID of the
 *      previous one.
 *   5. Generate PDF (lib/reports/pdf.ts, PR-5).
 *   6. Persist to Supabase (PR-5+).
 *
 * The hash chain is per-vehicle. Each report embeds the previous report's
 * integrityHash as `previousHash` so any tampering breaks the chain.
 */

export const COMPATIBILITY_CONFIDENCES = [
  'EXACT',
  'HIGH',
  'MEDIUM',
  'LOW',
  'UNKNOWN',
] as const;
export type CompatibilityConfidence = typeof COMPATIBILITY_CONFIDENCES[number];

/* -------------------------------------------------------------------------- */
/*                              Enumerations                                  */
/* -------------------------------------------------------------------------- */

export const REPORT_TYPES = [
  'PRE_SCAN_REPORT',
  'POST_SCAN_REPORT',
  'REPAIR_EVIDENCE_REPORT',
  'PRE_PURCHASE_INSPECTION_REPORT',
  'DVIR_REPORT',
] as const;
export type ReportType = typeof REPORT_TYPES[number];

export const REPORT_STATUSES = [
  'DRAFT',
  'READY',
  'SIGNED',
  'EXPORTED',
  'SHARED',
  'VOIDED',
] as const;
export type ReportStatus = typeof REPORT_STATUSES[number];

export const EVIDENCE_TYPES = [
  'PHOTO',
  'VIDEO',
  'OBD_SNAPSHOT',
  'FREEZE_FRAME',
  'SENSOR_GRAPH',
  'SIGNATURE',
  'MEASUREMENT',
  'PART_INVOICE',
  'REPAIR_NOTE',
] as const;
export type EvidenceType = typeof EVIDENCE_TYPES[number];

/* -------------------------------------------------------------------------- */
/*                              Core entities                                 */
/* -------------------------------------------------------------------------- */

export interface CertifiedReport {
  id: string;
  vehicleId: string;
  userId: string;
  reportType: ReportType;
  title: string;
  status: ReportStatus;
  odometerKm: number | null;
  vin: string | null;
  plate: string | null;
  generatedAt: number; // unix ms
  signedAt: number | null;
  pdfUri: string | null;
  qrVerificationUrl: string | null;
  /** SHA-256 hex of the canonical content. */
  integrityHash: string;
  /** SHA-256 of the previous report for the same vehicle. */
  previousHash: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface ReportEvidence {
  id: string;
  reportId: string;
  type: EvidenceType;
  label: string;
  description: string;
  uri: string;
  /** Optional SHA-256 hex of the underlying file (when known). */
  hash: string | null;
  capturedAt: number;
  lat: number | null;
  lng: number | null;
}

/* -------------------------------------------------------------------------- */
/*                         OBD DiagnosticSnapshot                             */
/* -------------------------------------------------------------------------- */

/**
 * Mirrors the Kotlin DiagnosticSnapshot exactly so the web and Android
 * produce interchangeable JSON. If a future PR evolves the Kotlin side,
 * keep the two contracts in lockstep.
 */
export interface DiagnosticSnapshot {
  id: string;
  vehicleId: string;
  sessionId: string | null;
  createdAtMs: number;
  dtcsActive: string[];
  dtcsPending: string[];
  dtcsPermanent: string[];
  freezeFramePidValues: Record<string, number>;
  livePids: Record<string, DiagnosticValue<number>>;
  readiness: Record<string, boolean>;
  ecuVoltage: number | null;
  rpm: number | null;
  coolantTempC: number | null;
  speedKph: number | null;
  engineLoadPct: number | null;
  fuelTrimStft: number | null;
  fuelTrimLtft: number | null;
  rawFrames: string[];
  /** Free-form note from the operator. */
  notes: string;
  /** True iff the values come from a real OBD adapter, not a manual entry. */
  liveFromAdapter: boolean;
  /** Adaptive Provenance of the source. */
  provenance: DiagnosticProvenance;
}

export interface DiagnosticValue<T> {
  value: T;
  unit: string;
  capturedAtMs: number;
}

export type DiagnosticProvenance =
  | 'LIVE_OBD'
  | 'CACHED_OBD'
  | 'MANUAL'
  | 'OFFLINE_FIXTURE';

/* -------------------------------------------------------------------------- */
/*                              Repair action                                 */
/* -------------------------------------------------------------------------- */

export interface RepairAction {
  id: string;
  reportId: string;
  actionType: string;
  component: string;
  dtcRelated: string | null;
  description: string;
  partUsed: string | null;
  supplier: string | null;
  mechanic: string | null;
  cost: number | null;
  currency: string;
  warrantyDays: number | null;
  createdAt: number;
}

/* -------------------------------------------------------------------------- */
/*                              Signature                                     */
/* -------------------------------------------------------------------------- */

export interface ReportSignature {
  id: string;
  reportId: string;
  signerName: string;
  signerRole: string;
  signatureImageUri: string;
  signedAt: number;
  /** Hashed device id (NEVER the raw id). */
  deviceIdHash: string;
  integrityHash: string;
}

/* -------------------------------------------------------------------------- */
/*                          Per-purchase / peritaje                           */
/* -------------------------------------------------------------------------- */

export const PERITAJE_VERDICTS = [
  'APROBADO',
  'APROBADO_CON_OBSERVACIONES',
  'NEGOCIAR_PRECIO',
  'NO_RECOMENDADO',
  'RIESGO_ALTO',
] as const;
export type PeritajeVerdict = typeof PERITAJE_VERDICTS[number];

export const PERITAJE_SECTIONS = [
  'MOTOR',
  'TRANSMISION',
  'FRENOS',
  'SUSPENSION',
  'DIRECCION',
  'ELECTRICO',
  'EMISIONES',
  'CARROCERIA',
  'INTERIOR',
  'PRUEBA_MANEJO',
  'OBD',
  'FUGAS',
  'RUIDOS',
  'TEMPERATURA',
  'VOLTAJE',
  'HISTORIAL_DTC',
] as const;
export type PeritajeSection = typeof PERITAJE_SECTIONS[number];

export interface PeritajeSectionScore {
  section: PeritajeSection;
  /** 0..10 score for this section. */
  score: number;
  notes: string;
  evidenceUris: string[];
}

export interface PeritajeChecklist {
  overallScore: number; // 0..100
  verdict: PeritajeVerdict;
  sectionScores: PeritajeSectionScore[];
  /** Confidence: 0..1, lowered when no OBD evidence is captured. */
  confidence: number;
  criticalAlerts: string[];
  recommendation: string;
}

/* -------------------------------------------------------------------------- */
/*                            Privacy toggles                                 */
/* -------------------------------------------------------------------------- */

export interface ReportPrivacyOptions {
  redactVin: boolean;
  redactPlate: boolean;
  redactLocation: boolean;
  publicShare: boolean;
}

/* -------------------------------------------------------------------------- */
/*                              Drafts                                        */
/* -------------------------------------------------------------------------- */

export interface DraftReportInput {
  vehicleId: string;
  userId: string;
  reportType: ReportType;
  title: string;
  odometerKm: number | null;
  vin: string | null;
  plate: string | null;
  snapshot: DiagnosticSnapshot | null;
  evidence: Omit<ReportEvidence, 'id' | 'reportId' | 'hash'>[];
  repairActions: Omit<RepairAction, 'id' | 'reportId' | 'createdAt'>[];
  privacy: ReportPrivacyOptions;
  peritaje: PeritajeChecklist | null;
  notes: string;
}

/* -------------------------------------------------------------------------- */
/*                          Cross-type convenience                            */
/* -------------------------------------------------------------------------- */

export interface ReportDtcSummary {
  before: string[];
  after: string[];
  cleared: string[];
  persistent: string[];
}

export function summarizeDtcs(
  before: string[],
  after: string[],
): ReportDtcSummary {
  const b = new Set(before);
  const a = new Set(after);
  const cleared = [...b].filter((c) => !a.has(c));
  const persistent = [...b].filter((c) => a.has(c));
  return { before, after, cleared, persistent };
}

/**
 * Composite confidence for the report header. We use the worst observed
 * compat-confidence of the DTCs and the OBD-snapshot presence.
 *
 * This is a helper for the UI only — the integrityHash is what is
 * actually signed.
 */
export function reportConfidence(
  dtcs: string[],
  snapshot: DiagnosticSnapshot | null,
  peritaje: PeritajeChecklist | null,
): CompatibilityConfidence {
  if (!snapshot || !snapshot.liveFromAdapter) {
    if (peritaje) {
      // Peritaje can still have a meaningful confidence if it has at least
      // 5 sections scored. Otherwise we surface UNKNOWN.
      if (peritaje.sectionScores.length >= 5) return 'MEDIUM';
      return 'LOW';
    }
    return 'UNKNOWN';
  }
  if (dtcs.length === 0) return 'EXACT';
  if (dtcs.length <= 3) return 'HIGH';
  return 'MEDIUM';
}
