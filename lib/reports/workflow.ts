/**
 * Closed-Loop Forensic Repair Workflow Coordinator — TypeScript Mirror.
 *
 * Speaks the same deterministic business logic and cryptographic protocol as
 * Kotlin's `ForensicRepairWorkflowCoordinator.kt`.
 *
 * Implements the core closed-loop workflow:
 *   Pre-Scan → Compatible Parts → Quote → Repair → Post-Scan Prompt → Certified Report + QR.
 */

import { sha256Hex, hashSnapshot } from './hash';
import { DiagnosticSnapshot, EvidenceType, ReportStatus, ReportType } from './types';
import { suggestParts, PartSuggestion, SuggestionSource } from '../parts/part-suggestion';

export type WorkflowState =
  | 'INITIAL_INTAKE'
  | 'PRE_SCAN_CAPTURED'
  | 'COMPATIBLE_PARTS_PROPOSED'
  | 'QUOTE_ACCEPTED'
  | 'WORK_IN_PROGRESS'
  | 'POST_SCAN_REQUIRED'
  | 'POST_SCAN_CAPTURED'
  | 'CERTIFIED_CLOSED'
  | 'DISPUTED'
  | 'VOIDED';

export interface ForensicEvidenceItem {
  id: string;
  evidenceType: EvidenceType;
  uri: string;
  sha256Hash: string;
  recordedAtMs: number;
  caption?: string;
}

export interface DtcResolutionAudit {
  originalDtcs: string[];
  resolvedDtcs: string[];
  residualDtcs: string[];
  newDtcs: string[];
  isFullyResolved: boolean;
  clearedCodesRatio: number;
}

export interface QrWorkflowPayload {
  reportId: string;
  integrityHash: string;
  vehicleId: string;
  generatedAt: number;
  reportType: ReportType;
  verifierUrl: string | null;
}

export interface ForensicRepairCertificate {
  certificateId: string;
  vehicleId: string;
  serviceId: string;
  preScanHash: string;
  postScanHash: string;
  evidenceMerkleRoot: string;
  certifiedIntegrityHash: string;
  qrPayload: QrWorkflowPayload;
  issuedAtMs: number;
  status: ReportStatus;
}

export interface ForensicRepairSession {
  sessionId: string;
  serviceId: string;
  vehicleId: string;
  mechanicId: string;
  state: WorkflowState;
  preScanSnapshot: DiagnosticSnapshot;
  proposedParts: PartSuggestion[];
  acceptedQuoteId: string | null;
  installedParts: string[];
  evidenceList: ForensicEvidenceItem[];
  postScanSnapshot: DiagnosticSnapshot | null;
  dtcAudit: DtcResolutionAudit | null;
  certificate: ForensicRepairCertificate | null;
  createdAtMs: number;
  updatedAtMs: number;
}

export function startForensicRepairSession(
  sessionId: string,
  serviceId: string,
  vehicleId: string,
  mechanicId: string,
  preScanSnapshot: DiagnosticSnapshot,
  timestampMs: number = Date.now(),
): ForensicRepairSession {
  if (preScanSnapshot.vehicleId !== vehicleId) {
    throw new Error(
      `Pre-Scan vehicleId (${preScanSnapshot.vehicleId}) does not match session vehicleId (${vehicleId})`,
    );
  }

  return {
    sessionId,
    serviceId,
    vehicleId,
    mechanicId,
    state: 'PRE_SCAN_CAPTURED',
    preScanSnapshot,
    proposedParts: [],
    acceptedQuoteId: null,
    installedParts: [],
    evidenceList: [],
    postScanSnapshot: null,
    dtcAudit: null,
    certificate: null,
    createdAtMs: timestampMs,
    updatedAtMs: timestampMs,
  };
}

export function proposePartsForSession(
  session: ForensicRepairSession,
  overrideDtcs?: string[],
  timestampMs: number = Date.now(),
): ForensicRepairSession {
  if (
    session.state !== 'PRE_SCAN_CAPTURED' &&
    session.state !== 'COMPATIBLE_PARTS_PROPOSED'
  ) {
    throw new Error(`Cannot propose parts in state: ${session.state}`);
  }

  const dtcs = overrideDtcs ?? session.preScanSnapshot.dtcsActive;
  const suggestions = suggestParts({
    source: 'DTC' as SuggestionSource,
    dtcCodes: dtcs,
  });

  return {
    ...session,
    state: 'COMPATIBLE_PARTS_PROPOSED',
    proposedParts: suggestions,
    updatedAtMs: timestampMs,
  };
}

export function acceptQuoteAndStartWork(
  session: ForensicRepairSession,
  quoteId: string,
  installedParts: string[],
  timestampMs: number = Date.now(),
): ForensicRepairSession {
  if (
    session.state !== 'COMPATIBLE_PARTS_PROPOSED' &&
    session.state !== 'PRE_SCAN_CAPTURED'
  ) {
    throw new Error(`Cannot accept quote in state: ${session.state}`);
  }

  return {
    ...session,
    state: 'WORK_IN_PROGRESS',
    acceptedQuoteId: quoteId,
    installedParts,
    updatedAtMs: timestampMs,
  };
}

export function attachSessionEvidence(
  session: ForensicRepairSession,
  evidence: ForensicEvidenceItem,
  timestampMs: number = Date.now(),
): ForensicRepairSession {
  if (session.state === 'CERTIFIED_CLOSED' || session.state === 'VOIDED') {
    throw new Error('Cannot attach evidence to a closed or voided session');
  }

  return {
    ...session,
    evidenceList: [...session.evidenceList, evidence],
    updatedAtMs: timestampMs,
  };
}

export function completeRepairWork(
  session: ForensicRepairSession,
  timestampMs: number = Date.now(),
): ForensicRepairSession {
  if (session.state !== 'WORK_IN_PROGRESS') {
    throw new Error(`Cannot complete repair work when state is: ${session.state}`);
  }

  return {
    ...session,
    state: 'POST_SCAN_REQUIRED',
    updatedAtMs: timestampMs,
  };
}

export function auditDtcResolution(
  preScan: DiagnosticSnapshot,
  postScan: DiagnosticSnapshot,
): DtcResolutionAudit {
  const original = Array.from(
    new Set([...preScan.dtcsActive, ...preScan.dtcsPending]),
  ).sort();
  const current = Array.from(
    new Set([...postScan.dtcsActive, ...postScan.dtcsPending]),
  ).sort();

  const currentSet = new Set(current);
  const originalSet = new Set(original);

  const resolved = original.filter((c) => !currentSet.has(c));
  const residual = original.filter((c) => currentSet.has(c));
  const newlyIntroduced = current.filter((c) => !originalSet.has(c));

  const ratio = original.length === 0 ? 1.0 : resolved.length / original.length;

  return {
    originalDtcs: original,
    resolvedDtcs: resolved,
    residualDtcs: residual,
    newDtcs: newlyIntroduced,
    isFullyResolved: residual.length === 0 && newlyIntroduced.length === 0,
    clearedCodesRatio: ratio,
  };
}

export async function computeSessionEvidenceMerkleRoot(
  evidenceList: ForensicEvidenceItem[],
): Promise<string> {
  if (evidenceList.length === 0) return 'NO_EVIDENCE_HASHES';
  const sorted = evidenceList.map((e) => e.sha256Hash).sort();
  return sha256Hex(sorted.join('::'));
}

export async function certifyRepairSession(
  session: ForensicRepairSession,
  postScanSnapshot: DiagnosticSnapshot,
  verifierBaseUrl: string = 'https://meet.elysium.app/verify',
  timestampMs: number = Date.now(),
): Promise<ForensicRepairSession> {
  if (
    session.state !== 'POST_SCAN_REQUIRED' &&
    session.state !== 'POST_SCAN_CAPTURED'
  ) {
    throw new Error(
      `Cannot certify repair in state: ${session.state}. Post-scan must be required first.`,
    );
  }

  if (postScanSnapshot.vehicleId !== session.vehicleId) {
    throw new Error(
      `Post-Scan vehicleId (${postScanSnapshot.vehicleId}) must match session vehicleId (${session.vehicleId})`,
    );
  }

  const preHash =
    (session.preScanSnapshot as unknown as { hashSha256?: string }).hashSha256 ??
    (await hashSnapshot(session.preScanSnapshot));
  const postHash =
    (postScanSnapshot as unknown as { hashSha256?: string }).hashSha256 ??
    (await hashSnapshot(postScanSnapshot));

  if (preHash === postHash) {
    throw new Error('Post-Scan snapshot must be distinct from Pre-Scan snapshot');
  }

  const audit = auditDtcResolution(session.preScanSnapshot, postScanSnapshot);
  const evidenceRoot = await computeSessionEvidenceMerkleRoot(session.evidenceList);

  const canonicalPayload = [
    session.sessionId,
    session.serviceId,
    session.vehicleId,
    preHash,
    postHash,
    evidenceRoot,
    audit.resolvedDtcs.join(','),
    audit.residualDtcs.join(','),
    timestampMs.toString(),
  ].join('|');

  const certifiedHash = await sha256Hex(canonicalPayload);
  const certificateId = `CERT-${session.serviceId.slice(-8)}-${timestampMs % 100000}`;

  const qrPayload: QrWorkflowPayload = {
    reportId: certificateId,
    integrityHash: certifiedHash,
    vehicleId: session.vehicleId,
    generatedAt: timestampMs,
    reportType: 'POST_SCAN_REPORT',
    verifierUrl: `${verifierBaseUrl}/${certificateId}`,
  };

  const certificate: ForensicRepairCertificate = {
    certificateId,
    vehicleId: session.vehicleId,
    serviceId: session.serviceId,
    preScanHash: preHash,
    postScanHash: postHash,
    evidenceMerkleRoot: evidenceRoot,
    certifiedIntegrityHash: certifiedHash,
    qrPayload,
    issuedAtMs: timestampMs,
    status: 'SIGNED',
  };

  return {
    ...session,
    state: 'CERTIFIED_CLOSED',
    postScanSnapshot,
    dtcAudit: audit,
    certificate,
    updatedAtMs: timestampMs,
  };
}
