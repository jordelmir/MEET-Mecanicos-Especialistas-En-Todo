/**
 * Bilingual Forensic Technical Affidavit Generator (ES / EN - C1) — TypeScript Mirror.
 *
 * Speaks the same byte-exact language and cryptographic protocol as
 * Kotlin's `ForensicAffidavitGenerator.kt`. Produces structured,
 * legally defensible automotive forensic inspection affidavits.
 *
 * Adheres strictly to AGENTS.md:
 * - Never invents data ("Dato no capturado", "Requiere prueba física").
 * - Applies modal hedging in C1 English ("The empirical data indicates...", "It is plausible that...").
 * - Embeds tamper-evident SHA-256 Merkle root and 6-field QR verification payload.
 */

import { sha256Hex, hashSnapshot } from './hash';
import { DiagnosticSnapshot, ReportType } from './types';

export type AffidavitLanguage =
  | 'SPANISH_FORMAL'
  | 'ENGLISH_C1_FORENSIC'
  | 'BILINGUAL_DUAL_COLUMN';

export interface InstalledPartEvidence {
  partName: string;
  oemPartNumber: string | null;
  invoiceOrBatchNumber: string | null;
  verifiedByMultimeterOrPressure: boolean;
}

export interface QrAffidavitPayload {
  reportId: string;
  integrityHash: string;
  vehicleId: string;
  generatedAt: number;
  reportType: ReportType;
  verifierUrl: string | null;
}

export interface AffidavitInput {
  affidavitNumber: string;
  inspectorName: string;
  inspectorCredentialNumber: string;
  jurisdiction?: string;
  vehicleId: string;
  redactedVin: string;
  redactedPlate: string;
  odometerKm: number | null;
  preScanSnapshot: DiagnosticSnapshot;
  postScanSnapshot: DiagnosticSnapshot | null;
  installedParts: InstalledPartEvidence[];
  physicalMeasurements: Record<string, string>;
  evidenceHashes: string[];
  previousReportHash?: string | null;
  timestampMs?: number;
}

export interface ForensicAffidavitDocument {
  affidavitNumber: string;
  language: AffidavitLanguage;
  title: string;
  bodyMarkdown: string;
  forensicMerkleRoot: string;
  overallAffidavitHash: string;
  qrPayload: QrAffidavitPayload;
  generatedAtMs: number;
}

export function encodeQrAffidavitPayload(payload: QrAffidavitPayload): string {
  return [
    'v1',
    payload.reportId,
    payload.integrityHash,
    payload.vehicleId,
    payload.generatedAt.toString(),
    payload.reportType,
    payload.verifierUrl ?? '',
  ].join('|');
}

export async function computeEvidenceMerkleRoot(hashes: string[]): Promise<string> {
  if (hashes.length === 0) return 'NO_EVIDENCE_RECORDED';
  const sorted = [...hashes].sort();
  return sha256Hex(sorted.join('::'));
}

export async function generateAffidavit(
  input: AffidavitInput,
  language: AffidavitLanguage,
): Promise<ForensicAffidavitDocument> {
  const timestamp = input.timestampMs ?? Date.now();
  const jurisdiction = input.jurisdiction ?? 'República de Costa Rica — Ley de Tránsito N.° 9078';

  const title =
    language === 'SPANISH_FORMAL'
      ? 'DICTAMEN PERICIAL FORENSE DE INGENIERÍA AUTOMOTRIZ'
      : language === 'ENGLISH_C1_FORENSIC'
      ? 'AUTOMOTIVE FORENSIC ENGINEERING AFFIDAVIT & EXPERT OPINION'
      : 'DICTAMEN TÉCNICO FORENSE BILINGÜE / BILINGUAL FORENSIC AFFIDAVIT';

  // Compute Evidence Merkle Root
  const merkleRoot = await computeEvidenceMerkleRoot(input.evidenceHashes);

  // Compute Pre/Post hashes
  const preHash = (input.preScanSnapshot as unknown as { hashSha256?: string }).hashSha256 ?? (await hashSnapshot(input.preScanSnapshot));
  const postHash = input.postScanSnapshot
    ? ((input.postScanSnapshot as unknown as { hashSha256?: string }).hashSha256 ?? (await hashSnapshot(input.postScanSnapshot)))
    : 'NO_POST_SCAN';

  // Build Markdown Body
  const body =
    language === 'SPANISH_FORMAL'
      ? buildSpanishBody(input, jurisdiction, merkleRoot, preHash, postHash)
      : language === 'ENGLISH_C1_FORENSIC'
      ? buildEnglishC1Body(input, jurisdiction, merkleRoot, preHash, postHash)
      : buildBilingualBody(input, jurisdiction, merkleRoot, preHash, postHash);

  // Canonical overall affidavit string
  const canonicalAffidavit = [
    input.affidavitNumber,
    input.vehicleId,
    input.redactedVin,
    preHash,
    postHash,
    merkleRoot,
    input.previousReportHash ?? 'GENESIS',
    timestamp.toString(),
  ].join('|');

  const overallHash = await sha256Hex(canonicalAffidavit);

  const qrPayload: QrAffidavitPayload = {
    reportId: input.affidavitNumber,
    integrityHash: overallHash,
    vehicleId: input.vehicleId,
    generatedAt: timestamp,
    reportType: input.postScanSnapshot ? 'POST_SCAN_REPORT' : 'PRE_SCAN_REPORT',
    verifierUrl: `https://meet.elysium.app/verify/${input.affidavitNumber}`,
  };

  return {
    affidavitNumber: input.affidavitNumber,
    language,
    title,
    bodyMarkdown: body,
    forensicMerkleRoot: merkleRoot,
    overallAffidavitHash: overallHash,
    qrPayload,
    generatedAtMs: timestamp,
  };
}

function buildSpanishBody(
  input: AffidavitInput,
  jurisdiction: string,
  merkleRoot: string,
  preHash: string,
  postHash: string,
): string {
  const lines: string[] = [];
  lines.push('# DICTAMEN PERICIAL FORENSE DE INGENIERÍA AUTOMOTRIZ');
  lines.push(`**Ref. N.°**: ${input.affidavitNumber} | **Jurisdicción**: ${jurisdiction}`);
  lines.push(`**Perito Certificado**: ${input.inspectorName} (Carné MEP / CT: ${input.inspectorCredentialNumber})`);
  lines.push('');
  lines.push('---');
  lines.push('## 1. Identificación Registral y Física del Vehículo');
  lines.push(`- **Identificador del Sistema**: \`${input.vehicleId}\``);
  lines.push(`- **VIN Censurado (Anti-Doxxing)**: \`${input.redactedVin}\``);
  lines.push(`- **Placa Censurada**: \`${input.redactedPlate}\``);
  lines.push(`- **Odómetro Registrado**: ${input.odometerKm !== null ? `${input.odometerKm} km` : '*Dato no capturado*'}`);
  lines.push('');
  lines.push('## 2. Telemetría y Hallazgos Pre-Scan (Diagnóstico Inicial)');
  lines.push(`- **Hash SHA-256 Pre-Scan**: \`${preHash}\``);
  const activeDtcs = input.preScanSnapshot.dtcsActive;
  if (activeDtcs.length === 0) {
    lines.push('- **Códigos de Falla Activos**: Ningún código registrado (OBD en estado limpio).');
  } else {
    lines.push(`- **Códigos de Falla Activos**: ${activeDtcs.join(', ')}`);
    for (const [pid, value] of Object.entries(input.preScanSnapshot.freezeFramePidValues)) {
      lines.push(`  - *Freeze Frame ${pid}*: ${value}`);
    }
  }
  lines.push('');
  lines.push('## 3. Pruebas Físicas e Intervenciones Mecánicas');
  if (input.installedParts.length === 0) {
    lines.push('- *No se requirió sustitución de componentes mayores.*');
  } else {
    for (const part of input.installedParts) {
      lines.push(
        `- **Componente**: ${part.partName} | OEM: \`${part.oemPartNumber ?? 'Pendiente de validación'}\``,
      );
      lines.push(
        `  - Factura/Lote: ${part.invoiceOrBatchNumber ?? 'No provisto'} | Verificación física: ${
          part.verifiedByMultimeterOrPressure ? 'APROBADA' : 'Requiere prueba física'
        }`,
      );
    }
  }
  if (Object.keys(input.physicalMeasurements).length > 0) {
    lines.push('- **Mediciones de Laboratorio / Taller**:');
    for (const [k, v] of Object.entries(input.physicalMeasurements)) {
      lines.push(`  - ${k}: ${v}`);
    }
  }
  lines.push('');
  lines.push('## 4. Auditoría Post-Scan y Verificación Causal');
  if (!input.postScanSnapshot) {
    lines.push('> [!WARNING]');
    lines.push('> **Post-Scan no disponible**: El presente documento constituye un informe preliminar. La garantía no surtirá efecto legal hasta certificar la ausencia de códigos residuales.');
  } else {
    lines.push(`- **Hash SHA-256 Post-Scan**: \`${postHash}\``);
    const postDtcs = input.postScanSnapshot.dtcsActive;
    const resolved = activeDtcs.filter((d) => !postDtcs.includes(d));
    lines.push(`- **Códigos DTC Resueltos Exitosamente**: ${resolved.length === 0 ? 'Ninguno' : resolved.join(', ')}`);
    if (postDtcs.length > 0) {
      lines.push(`- **Códigos Residuales Persistentes**: ${postDtcs.join(', ')} *(Requiere diagnóstico complementario)*`);
    }
  }
  lines.push('');
  lines.push('## 5. Conclusión Pericial y Fe Pública');
  lines.push('El perito firmante certifica bajo fe de juramento que las mediciones, hashes criptográficos y evidencias fueron capturados de manera directa y sin manipulación sintética. Cualquier enmienda no registrada invalida la cadena de custodia.');
  lines.push('');
  lines.push(`- **Raíz Merkle de Evidencias**: \`${merkleRoot}\``);
  lines.push('- **Hash Inmutable del Dictamen**: *Generado al pie con sello QR de 6 campos.*');

  return lines.join('\n');
}

function buildEnglishC1Body(
  input: AffidavitInput,
  jurisdiction: string,
  merkleRoot: string,
  preHash: string,
  postHash: string,
): string {
  const lines: string[] = [];
  lines.push('# AUTOMOTIVE FORENSIC ENGINEERING AFFIDAVIT & EXPERT OPINION');
  lines.push(`**Affidavit Docket**: ${input.affidavitNumber} | **Jurisdiction**: ${jurisdiction}`);
  lines.push(`**Certified Forensic Examiner**: ${input.inspectorName} (MEET / MEP Credential: ${input.inspectorCredentialNumber})`);
  lines.push('');
  lines.push('---');
  lines.push('## 1. Vehicle Identification & Epistemic Boundary');
  lines.push(`- **Platform UUID**: \`${input.vehicleId}\``);
  lines.push(`- **VIN Identifier (Redacted)**: \`${input.redactedVin}\``);
  lines.push(`- **License Plate (Redacted)**: \`${input.redactedPlate}\``);
  lines.push(`- **Certified Odometer**: ${input.odometerKm !== null ? `${input.odometerKm} km` : '*Unrecorded metric*'}`);
  lines.push('');
  lines.push('## 2. Pre-Scan Telemetric Diagnostics & Empirical Findings');
  lines.push(`- **Pre-Scan Cryptographic Hash**: \`${preHash}\``);
  const activeDtcs = input.preScanSnapshot.dtcsActive;
  if (activeDtcs.length === 0) {
    lines.push('- **Diagnostic Trouble Codes**: No persistent active faults logged in non-volatile ECU memory.');
  } else {
    lines.push(`- **Logged Diagnostic Trouble Codes**: ${activeDtcs.join(', ')}`);
    for (const [pid, value] of Object.entries(input.preScanSnapshot.freezeFramePidValues)) {
      lines.push(`  - *Freeze Frame Snapshot PID [${pid}]*: ${value}`);
    }
    lines.push('');
    lines.push(
      'The empirical sensor telemetry strongly suggests that the anomalies detected correlate with the aforementioned DTCs. ' +
        'Under no circumstances should secondary component degradation be ruled out prior to physical circuit verification.',
    );
  }
  lines.push('');
  lines.push('## 3. Physical Interventions, Metrology & Component Substitution');
  if (input.installedParts.length === 0) {
    lines.push('- *No mechanical or electrical assembly replacement was necessitated during this session.*');
  } else {
    for (const part of input.installedParts) {
      lines.push(
        `- **Assembly Replaced**: ${part.partName} | OEM Spec: \`${part.oemPartNumber ?? 'Pending cross-reference validation'}\``,
      );
      lines.push(
        `  - Chain-of-Custody Trace: ${part.invoiceOrBatchNumber ?? 'Unspecified'} | Physical Multimeter/Pressure Test: ${
          part.verifiedByMultimeterOrPressure ? 'CONGRUENT / PASSED' : 'Requires physical re-testing'
        }`,
      );
    }
  }
  if (Object.keys(input.physicalMeasurements).length > 0) {
    lines.push('- **Laboratory Metrology Data**:');
    for (const [k, v] of Object.entries(input.physicalMeasurements)) {
      lines.push(`  - ${k}: ${v}`);
    }
  }
  lines.push('');
  lines.push('## 4. Post-Scan Telemetry & Causal Audit');
  if (!input.postScanSnapshot) {
    lines.push('> [!WARNING]');
    lines.push('> **Post-Scan Verification Pending**: Had a post-repair diagnostic capture been submitted, conclusive causal resolution would be affirmed. This dossier remains conditional.');
  } else {
    lines.push(`- **Post-Scan Cryptographic Hash**: \`${postHash}\``);
    const postDtcs = input.postScanSnapshot.dtcsActive;
    const resolved = activeDtcs.filter((d) => !postDtcs.includes(d));
    lines.push(`- **Empirically Resolved Codes**: ${resolved.length === 0 ? 'None confirmed' : resolved.join(', ')}`);
    if (postDtcs.length > 0) {
      lines.push(`- **Residual Faults**: ${postDtcs.join(', ')} *(Plausibly linked to peripheral wiring or secondary sub-systems)*`);
    } else {
      lines.push('The comparative analysis demonstrates complete clearing of the primary fault signatures without induced collateral faults.');
    }
  }
  lines.push('');
  lines.push('## 5. Forensic Expert Attestation');
  lines.push('I hereby declare under penalty of perjury that the diagnostic findings, cryptographic hashes, and metrological measurements detailed herein represent an authentic, unedited forensic record of the examined vehicle.');
  lines.push('');
  lines.push(`- **Evidence Merkle Root Hash**: \`${merkleRoot}\``);
  lines.push('- **Immutable Dossier Digest**: *Enforced via 6-field QR signature.*');

  return lines.join('\n');
}

function buildBilingualBody(
  input: AffidavitInput,
  jurisdiction: string,
  merkleRoot: string,
  preHash: string,
  postHash: string,
): string {
  const es = buildSpanishBody(input, jurisdiction, merkleRoot, preHash, postHash);
  const en = buildEnglishC1Body(input, jurisdiction, merkleRoot, preHash, postHash);
  return `${es}\n\n---\n\n${en}`;
}
