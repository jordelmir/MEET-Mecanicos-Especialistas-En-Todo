/**
 * lib/reports — shared types for the certified-report pipeline.
 *
 * The types here mirror the SQL schema in
 * `supabase/migrations/20260704001000_reports_foundations.sql` and the
 * Kotlin types in `android/.../diagnostic/DiagnosticSnapshot.kt`. The
 * goal is byte-level contract parity: a web report and an Android
 * report with the same content produce the same `integrityHash` so
 * they can share the per-vehicle chain.
 *
 * If the Kotlin side evolves, this file follows in the same PR.
 */

/* -------------------------------------------------------------------------- */
/*                            DiagnosticProvenance                            */
/* -------------------------------------------------------------------------- */
/**
 * Mirrors the sealed class `DiagnosticProvenance` in
 * `android/.../diagnostic/DiagnosticProvenance.kt`. Each variant is a
 * tagged object so the UI can render a clear "REAL / OFFLINE / SIMULATED
 * / SIN ENLACE / REQUIERE HARDWARE / NO SOPORTADO / INFERIDO / MANUAL"
 * badge and refuse to surface unreliable data as real.
 *
 * Web code is encouraged to surface the provenance on every UI surface
 * that displays a numeric diagnostic value.
 */
export type DiagnosticProvenance =
  | { kind: 'REAL' }
  | { kind: 'OFFLINE' }
  | { kind: 'SIMULATED' }
  | { kind: 'SIN_ENLACE' }
  | { kind: 'REQUIERE_HARDWARE'; toolName: string }
  | { kind: 'NO_SOPORTADO'; reason: string }
  | { kind: 'INFERRED'; source: string; confidence: number }
  | { kind: 'MANUAL'; authorId: string };

export const PROVENANCE_REAL: DiagnosticProvenance = { kind: 'REAL' };
export const PROVENANCE_OFFLINE: DiagnosticProvenance = { kind: 'OFFLINE' };
export const PROVENANCE_SIMULATED: DiagnosticProvenance = { kind: 'SIMULATED' };
export const PROVENANCE_SIN_ENLACE: DiagnosticProvenance = { kind: 'SIN_ENLACE' };

export function provenanceLabel(p: DiagnosticProvenance): string {
  switch (p.kind) {
    case 'REAL':
      return 'REAL';
    case 'OFFLINE':
      return 'OFFLINE';
    case 'SIMULATED':
      return 'SIMULADO';
    case 'SIN_ENLACE':
      return 'SIN ENLACE';
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

export function isReliableProvenance(p: DiagnosticProvenance): boolean {
  return p.kind === 'REAL' || p.kind === 'OFFLINE';
}

/* -------------------------------------------------------------------------- */
/*                              DiagnosticValue                              */
/* -------------------------------------------------------------------------- */
/**
 * Mirrors the Kotlin `DiagnosticValue<T>`. The rule: every diagnostic
 * numeric value MUST be wrapped in a DiagnosticValue so the UI can
 * surface the provenance and refuse to show unreliable data as real.
 */
export interface DiagnosticValue<T> {
  value: T;
  provenance: DiagnosticProvenance;
  timestampMs: number;
  source?: string;
}

export function realValue<T>(value: T, timestampMs: number): DiagnosticValue<T> {
  return { value, provenance: PROVENANCE_REAL, timestampMs };
}

export function offlineValue<T>(value: T, timestampMs: number): DiagnosticValue<T> {
  return { value, provenance: PROVENANCE_OFFLINE, timestampMs };
}

export function sinEnlaceValue<T>(timestampMs: number): DiagnosticValue<T | null> {
  return { value: null, provenance: PROVENANCE_SIN_ENLACE, timestampMs };
}

export function noSoportadoValue<T>(reason: string, timestampMs: number): DiagnosticValue<T | null> {
  return { value: null, provenance: { kind: 'NO_SOPORTADO', reason }, timestampMs };
}

export function simulatedValue<T>(value: T, timestampMs: number): DiagnosticValue<T> {
  return { value, provenance: PROVENANCE_SIMULATED, timestampMs };
}

/* -------------------------------------------------------------------------- */
/*                              Core entities                                 */
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

/**
 * 13 evidence types. The first 9 mirror the spec from Jor; the
 * remaining 4 (BEFORE_PHOTO, AFTER_PHOTO, MULTIMETER_READING,
 * FUEL_PRESSURE_READING, PART_REPLACED, RECEIPT, CUSTOMER_SIGNATURE,
 * PROVIDER_NOTE, TEST_DRIVE_RESULT, PDF_REPORT) come from the
 * existing Kotlin `EvidenceType` in `core/marketplace/ServiceCatalog.kt`.
 * Total: 13. Both sides produce the same enum names so a UI that
 * distinguishes BEFORE vs AFTER photos is possible.
 */
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
  'BEFORE_PHOTO',
  'AFTER_PHOTO',
  'MULTIMETER_READING',
  'FUEL_PRESSURE_READING',
  'PART_REPLACED',
  'RECEIPT',
  'CUSTOMER_SIGNATURE',
  'PROVIDER_NOTE',
  'TEST_DRIVE_RESULT',
  'PDF_REPORT',
] as const;
export type EvidenceType = typeof EVIDENCE_TYPES[number];

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
  generatedAt: number;
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
 * Mirrors the Kotlin DiagnosticSnapshot exactly. Two contract guarantees:
 *  1. Field names are identical.
 *  2. The canonicalization that feeds SHA-256 produces the same bytes
 *     for the same content on both sides (see lib/reports/hash.ts).
 *
 * If a future PR evolves the Kotlin side, the canonicalization +
 * this contract must follow in the same PR.
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
  notes: string;
  provenance: DiagnosticProvenance;
}

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
  score: number;
  notes: string;
  evidenceUris: string[];
}

export interface PeritajeChecklist {
  overallScore: number;
  verdict: PeritajeVerdict;
  sectionScores: PeritajeSectionScore[];
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

export const COMPATIBILITY_CONFIDENCES = [
  'EXACT',
  'HIGH',
  'MEDIUM',
  'LOW',
  'UNKNOWN',
] as const;
export type CompatibilityConfidence = typeof COMPATIBILITY_CONFIDENCES[number];

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

export function reportConfidence(
  dtcs: string[],
  snapshot: DiagnosticSnapshot | null,
  peritaje: PeritajeChecklist | null,
): CompatibilityConfidence {
  if (!snapshot || !isReliableProvenance(snapshot.provenance)) {
    if (peritaje) {
      if (peritaje.sectionScores.length >= 5) return 'MEDIUM';
      return 'LOW';
    }
    return 'UNKNOWN';
  }
  if (dtcs.length === 0) return 'EXACT';
  if (dtcs.length <= 3) return 'HIGH';
  return 'MEDIUM';
}
