/**
 * lib/reports/generate — builders for report drafts.
 *
 * The draft is a plain JSON object. The DB layer (PR-5+) persists it,
 * but the hash + signature is computed entirely in this lib. The web and
 * the APK compute the same hash for the same input, which is the point.
 *
 * Builders:
 *   - buildPreScanDraft:    PRE_SCAN_REPORT.
 *   - buildPostScanDraft:   POST_SCAN_REPORT (compares pre snapshot).
 *   - buildRepairEvidenceDraft: REPAIR_EVIDENCE_REPORT.
 *   - buildPeritajeDraft:   PRE_PURCHASE_INSPECTION_REPORT.
 *   - buildDvirDraft:       DVIR_REPORT.
 *   - finalizeDraft:        computes all hashes + integrityHash + ids.
 *   - applySignature:       immutably locks the report and records a
 *                           ReportSignature.
 */

import {
  CertifiedReport,
  DiagnosticSnapshot,
  DraftReportInput,
  EvidenceType,
  PeritajeChecklist,
  PeritajeSectionScore,
  ReportDtcSummary,
  ReportEvidence,
  ReportPrivacyOptions,
  ReportSignature,
  ReportStatus,
  ReportType,
  RepairAction,
  summarizeDtcs,
} from './types';
import {
  canonicalSnapshotString,
  hashEvidence,
  hashPeritaje,
  hashRepairAction,
  hashReportDraft,
  hashSignature,
  hashSnapshot,
} from './hash';

/* -------------------------------------------------------------------------- */
/*                            ID helpers                                      */
/* -------------------------------------------------------------------------- */

function nowMs(): number {
  return Date.now();
}

let _idCounter = 0;
/**
 * Monotonic, sortable id. Not cryptographically strong — the integrityHash
 * carries the security. The id is local-uniqueness only; persistence
 * layer applies UUIDs in production.
 */
function localId(prefix: string): string {
  _idCounter += 1;
  return `${prefix}_${nowMs().toString(36)}_${_idCounter.toString(36)}`;
}

/* -------------------------------------------------------------------------- */
/*                          Privacy helpers                                   */
/* -------------------------------------------------------------------------- */

export const DEFAULT_PRIVACY: ReportPrivacyOptions = {
  redactVin: false,
  redactPlate: false,
  redactLocation: false,
  publicShare: false,
};

/**
 * Apply privacy redaction. Returns a copy of the report with the
 * fields redacted according to the toggles.
 */
export function applyPrivacy<T extends { vin?: string | null; plate?: string | null }>(
  draft: T,
  privacy: ReportPrivacyOptions,
): T {
  const out = { ...draft };
  if (privacy.redactVin && out.vin) {
    const v = out.vin;
    // Keep first 3 + last 3 characters for visual confirmation only.
    out.vin = v.length >= 11 ? `${v.slice(0, 3)}…${v.slice(-3)}` : '•••';
  }
  if (privacy.redactPlate && out.plate) {
    out.plate = '•••';
  }
  return out;
}

/* -------------------------------------------------------------------------- */
/*                            Draft builders                                  */
/* -------------------------------------------------------------------------- */

export interface BuildPreScanInput {
  vehicleId: string;
  userId: string;
  odometerKm: number | null;
  vin: string | null;
  plate: string | null;
  snapshot: DiagnosticSnapshot | null;
  notes: string;
  privacy?: Partial<ReportPrivacyOptions>;
}

export function buildPreScanDraft(
  input: BuildPreScanInput,
): DraftReportInput {
  return {
    vehicleId: input.vehicleId,
    userId: input.userId,
    reportType: 'PRE_SCAN_REPORT',
    title: 'Pre-Scan',
    odometerKm: input.odometerKm,
    vin: input.vin,
    plate: input.plate,
    snapshot: input.snapshot,
    evidence: input.snapshot
      ? [
          {
            type: 'OBD_SNAPSHOT' as EvidenceType,
            label: 'Snapshot OBD Pre-Scan',
            description: input.snapshot.notes || 'Capturado por el adaptador',
            uri: '',
            capturedAt: input.snapshot.createdAtMs,
            lat: null,
            lng: null,
          },
        ]
      : [
          {
            type: 'REPAIR_NOTE' as EvidenceType,
            label: 'Snapshot OBD no disponible',
            description:
              'No se capturó evidencia OBD real. Reporte basado en datos manuales / offline.',
            uri: '',
            capturedAt: nowMs(),
            lat: null,
            lng: null,
          },
        ],
    repairActions: [],
    privacy: { ...DEFAULT_PRIVACY, ...(input.privacy ?? {}) },
    peritaje: null,
    notes: input.notes,
  };
}

export interface BuildPostScanInput {
  vehicleId: string;
  userId: string;
  odometerKm: number | null;
  vin: string | null;
  plate: string | null;
  preSnapshot: DiagnosticSnapshot | null;
  postSnapshot: DiagnosticSnapshot | null;
  notes: string;
  privacy?: Partial<ReportPrivacyOptions>;
}

export function buildPostScanDraft(
  input: BuildPostScanInput,
): { draft: DraftReportInput; dtcSummary: ReportDtcSummary } {
  const pre = input.preSnapshot?.dtcsActive ?? [];
  const post = input.postSnapshot?.dtcsActive ?? [];
  const summary = summarizeDtcs(pre, post);
  const evidence: DraftReportInput['evidence'] = [];
  if (input.preSnapshot) {
    evidence.push({
      type: 'OBD_SNAPSHOT' as EvidenceType,
      label: 'Snapshot Pre-Reparación',
      description: input.preSnapshot.notes || 'Pre-repair snapshot',
      uri: '',
      capturedAt: input.preSnapshot.createdAtMs,
      lat: null,
      lng: null,
    });
  }
  if (input.postSnapshot) {
    evidence.push({
      type: 'OBD_SNAPSHOT' as EvidenceType,
      label: 'Snapshot Post-Reparación',
      description: input.postSnapshot.notes || 'Post-repair snapshot',
      uri: '',
      capturedAt: input.postSnapshot.createdAtMs,
      lat: null,
      lng: null,
    });
  } else {
    evidence.push({
      type: 'REPAIR_NOTE' as EvidenceType,
      label: 'Snapshot OBD post no disponible',
      description:
        'No se capturó snapshot post-reparación. No se afirma "reparado" sin validación.',
      uri: '',
      capturedAt: nowMs(),
      lat: null,
      lng: null,
    });
  }
  return {
    draft: {
      vehicleId: input.vehicleId,
      userId: input.userId,
      reportType: 'POST_SCAN_REPORT',
      title: 'Post-Scan',
      odometerKm: input.odometerKm,
      vin: input.vin,
      plate: input.plate,
      snapshot: input.postSnapshot,
      evidence,
      repairActions: [],
      privacy: { ...DEFAULT_PRIVACY, ...(input.privacy ?? {}) },
      peritaje: null,
      notes:
        input.notes +
        (input.postSnapshot
          ? ''
          : ' · Post-Scan no se puede afirmar "reparado" sin snapshot post-reparación.'),
    },
    dtcSummary: summary,
  };
}

export interface BuildRepairEvidenceInput {
  vehicleId: string;
  userId: string;
  odometerKm: number | null;
  vin: string | null;
  plate: string | null;
  preSnapshot: DiagnosticSnapshot | null;
  postSnapshot: DiagnosticSnapshot | null;
  notes: string;
  repairActions: Omit<RepairAction, 'id' | 'reportId' | 'createdAt'>[];
  evidenceUris: string[];
  privacy?: Partial<ReportPrivacyOptions>;
}

export function buildRepairEvidenceDraft(
  input: BuildRepairEvidenceInput,
): { draft: DraftReportInput; dtcSummary: ReportDtcSummary } {
  const base = buildPostScanDraft(input);
  return {
    draft: {
      ...base.draft,
      reportType: 'REPAIR_EVIDENCE_REPORT',
      title: 'Reporte de Reparación',
      repairActions: input.repairActions,
      evidence: [
        ...base.draft.evidence,
        ...input.evidenceUris.map((uri, i): DraftReportInput['evidence'][number] => ({
          type: 'PHOTO' as EvidenceType,
          label: `Evidencia fotográfica #${i + 1}`,
          description: '',
          uri,
          capturedAt: nowMs(),
          lat: null,
          lng: null,
        })),
      ],
    },
    dtcSummary: base.dtcSummary,
  };
}

export interface BuildPeritajeInput {
  vehicleId: string;
  userId: string;
  odometerKm: number | null;
  vin: string | null;
  plate: string | null;
  snapshot: DiagnosticSnapshot | null;
  checklist: PeritajeChecklist;
  privacy?: Partial<ReportPrivacyOptions>;
}

export function buildPeritajeDraft(
  input: BuildPeritajeInput,
): DraftReportInput {
  return {
    vehicleId: input.vehicleId,
    userId: input.userId,
    reportType: 'PRE_PURCHASE_INSPECTION_REPORT',
    title: 'Peritaje de Vehículo Usado',
    odometerKm: input.odometerKm,
    vin: input.vin,
    plate: input.plate,
    snapshot: input.snapshot,
    evidence: input.checklist.sectionScores.flatMap((s: PeritajeSectionScore) =>
      s.evidenceUris.map((uri, i) => ({
        type: 'PHOTO' as EvidenceType,
        label: `${s.section} #${i + 1}`,
        description: s.notes,
        uri,
        capturedAt: nowMs(),
        lat: null,
        lng: null,
      })),
    ),
    repairActions: [],
    privacy: { ...DEFAULT_PRIVACY, ...(input.privacy ?? {}) },
    peritaje: input.checklist,
    notes: input.checklist.recommendation,
  };
}

export interface BuildDvirInput {
  vehicleId: string;
  userId: string;
  odometerKm: number | null;
  vin: string | null;
  plate: string | null;
  operator: string;
  criticalNotes: string;
  brakesOk: boolean;
  lightsOk: boolean;
  tiresOk: boolean;
  fluidsOk: boolean;
  privacy?: Partial<ReportPrivacyOptions>;
}

export function buildDvirDraft(input: BuildDvirInput): DraftReportInput {
  return {
    vehicleId: input.vehicleId,
    userId: input.userId,
    reportType: 'DVIR_REPORT',
    title: `DVIR ${input.operator}`,
    odometerKm: input.odometerKm,
    vin: input.vin,
    plate: input.plate,
    snapshot: null,
    evidence: [
      {
        type: 'REPAIR_NOTE' as EvidenceType,
        label: 'DVIR checklist',
        description: [
          `operator=${input.operator}`,
          `brakes=${input.brakesOk ? 'ok' : 'FAIL'}`,
          `lights=${input.lightsOk ? 'ok' : 'FAIL'}`,
          `tires=${input.tiresOk ? 'ok' : 'FAIL'}`,
          `fluids=${input.fluidsOk ? 'ok' : 'FAIL'}`,
        ].join('; '),
        uri: '',
        capturedAt: nowMs(),
        lat: null,
        lng: null,
      },
    ],
    repairActions: [],
    privacy: { ...DEFAULT_PRIVACY, ...(input.privacy ?? {}) },
    peritaje: null,
    notes: input.criticalNotes,
  };
}

/* -------------------------------------------------------------------------- */
/*                              Finalization                                  */
/* -------------------------------------------------------------------------- */

export interface FinalizeInput {
  draft: DraftReportInput;
  previousHash: string | null;
}

export interface FinalizedReport {
  report: CertifiedReport;
  evidence: ReportEvidence[];
  repairActions: RepairAction[];
  snapshotHash: string | null;
  evidenceHashes: string[];
  repairActionHashes: string[];
  peritajeHash: string | null;
}

/**
 * Compute all hashes for a draft and produce a CertifiedReport skeleton
 * with status='DRAFT'. Sign with `applySignature` to make it SIGNED.
 */
export async function finalizeDraft(
  input: FinalizeInput,
): Promise<FinalizedReport> {
  const { draft, previousHash } = input;

  // 1) Hash the snapshot, if present.
  const snapshotHash = draft.snapshot ? await hashSnapshot(draft.snapshot) : null;

  // 2) Hash each evidence item + assign id.
  const evidenceWithHashes: ReportEvidence[] = await Promise.all(
    draft.evidence.map(async (e) => {
      const withId: ReportEvidence = {
        ...e,
        id: localId('evi'),
        reportId: '', // filled in once the report id is known
        hash: null,
      };
      const hash = await hashEvidence(withId);
      return { ...withId, hash };
    }),
  );

  // 3) Hash each repair action + assign id.
  const repairActions: RepairAction[] = await Promise.all(
    draft.repairActions.map(async (a) => {
      const withId: RepairAction = {
        ...a,
        id: localId('act'),
        reportId: '',
        createdAt: nowMs(),
      };
      const hash = await hashRepairAction(withId);
      return withId;
    }),
  );

  // 4) Hash the peritaje.
  const peritajeHash = draft.peritaje ? await hashPeritaje(draft.peritaje) : null;

  // 5) Hash each repair action (single pass, reused below).
  const repairActionHashes: string[] = await Promise.all(
    repairActions.map((a) => hashRepairAction(a)),
  );

  // 6) Compute the integrity hash.
  const integrityHash = await hashReportDraft(
    draft,
    snapshotHash,
    evidenceWithHashes.map((e) => e.hash ?? ''),
    repairActionHashes,
    peritajeHash,
    previousHash,
  );

  const id = localId('rpt');
  const now = nowMs();
  const report: CertifiedReport = {
    id,
    vehicleId: draft.vehicleId,
    userId: draft.userId,
    reportType: draft.reportType,
    title: draft.title,
    status: 'DRAFT' as ReportStatus,
    odometerKm: draft.odometerKm,
    vin: draft.vin,
    plate: draft.plate,
    generatedAt: now,
    signedAt: null,
    pdfUri: null,
    qrVerificationUrl: null,
    integrityHash,
    previousHash,
    createdAt: now,
    updatedAt: now,
  };

  return {
    report,
    evidence: evidenceWithHashes.map((e) => ({ ...e, reportId: id })),
    repairActions: repairActions.map((a) => ({ ...a, reportId: id })),
    snapshotHash,
    evidenceHashes: evidenceWithHashes.map((e) => e.hash ?? ''),
    repairActionHashes,
    peritajeHash,
  };
}

/* -------------------------------------------------------------------------- */
/*                              Signing                                       */
/* -------------------------------------------------------------------------- */

export interface SignInput {
  finalized: FinalizedReport;
  signerName: string;
  signerRole: string;
  signatureImageUri: string;
  deviceIdHash: string;
}

export interface SignedReport {
  report: CertifiedReport;
  signature: ReportSignature;
}

/**
 * Apply a signature to a finalized report. The report moves to status
 * SIGNED and is now frozen. Any later edit MUST go through a new draft +
 * a "supersede" flag (PR-5 wires the persistence side).
 */
export async function applySignature(input: SignInput): Promise<SignedReport> {
  const sigHash = await hashSignature(
    input.finalized.report.integrityHash,
    input.signerName,
    input.signerRole,
    input.finalized.report.generatedAt,
    input.deviceIdHash,
  );
  const signature: ReportSignature = {
    id: localId('sig'),
    reportId: input.finalized.report.id,
    signerName: input.signerName,
    signerRole: input.signerRole,
    signatureImageUri: input.signatureImageUri,
    signedAt: nowMs(),
    deviceIdHash: input.deviceIdHash,
    integrityHash: sigHash,
  };
  const report: CertifiedReport = {
    ...input.finalized.report,
    status: 'SIGNED' as ReportStatus,
    signedAt: signature.signedAt,
    updatedAt: signature.signedAt,
  };
  return { report, signature };
}

/* -------------------------------------------------------------------------- */
/*                            Validation rules                                */
/* -------------------------------------------------------------------------- */

export interface ValidationIssue {
  code: string;
  message: string;
  severity: 'BLOCK' | 'WARN';
}

export function validateDraftForSign(
  draft: DraftReportInput,
): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  if (!draft.vehicleId) {
    issues.push({ code: 'NO_VEHICLE', severity: 'BLOCK', message: 'No hay vehículo activo.' });
  }
  if (draft.reportType === 'PRE_PURCHASE_INSPECTION_REPORT' && !draft.peritaje) {
    issues.push({
      code: 'NO_PERITAJE',
      severity: 'BLOCK',
      message: 'Falta el checklist de peritaje.',
    });
  }
  if (draft.reportType === 'POST_SCAN_REPORT' && !draft.snapshot) {
    issues.push({
      code: 'POST_NO_SNAPSHOT',
      severity: 'WARN',
      message: 'No hay snapshot post-reparación. El reporte no podrá afirmar "reparado".',
    });
  }
  if (draft.snapshot && draft.snapshot.provenance.kind !== 'REAL') {
    issues.push({
      code: 'OBD_NOT_LIVE',
      severity: 'WARN',
      message: 'Snapshot OBD no proviene del adaptador. Confianza limitada.',
    });
  }
  return issues;
}
