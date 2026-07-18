import pilotPackJson from '../android/app/src/main/assets/knowledge/catalog/pilot_hyundai_accent_verna_2005_front_end.json';
import type { DetailedPart, GuidedRepairProcedure, RepairStep3D } from '../types';

export type CatalogPublicationState = 'REVIEW_REQUIRED' | 'PUBLISHED' | 'REJECTED';
export type CatalogCompatibilityState = 'REQUIRES_VERIFICATION' | 'PROBABLE' | 'EXACT';
export type RepairProgressState = 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED';

interface PilotSourceRef {
  sourceFileName: string;
  sourceDocumentSha256: string;
  sourceBlockId: string;
  sourceTextHash: string;
  sectionPath: string[];
  reviewStatus: string;
}

interface PilotPart {
  id: string;
  nameEs: string;
  nameEn: string;
  aliases: string[];
  category: string;
  system: string;
  subsystem: string;
  assembly: string;
  subassembly: string | null;
  position: DetailedPart['position'];
  description: string;
  confidence: 'UNVERIFIED';
  publicationState: CatalogPublicationState;
  compatibilityState: CatalogCompatibilityState;
  compatibilityMessage: string;
  requiredCompatibilityEvidence: string[];
  technicalSpecifications: {
    oemNumber: string | null;
    torque: string | null;
    material: string | null;
    dimensions: string | null;
    pinout: Record<string, string> | null;
  };
  sourceRefs: PilotSourceRef[];
  threeDimensionalBinding: {
    sceneId: string;
    nodeId: string;
    visualAuthority: 'GENERIC_SCHEMATIC';
    isDimensionalModel: false;
  };
}

interface PilotStep {
  id: string;
  order: number;
  title: string;
  instruction: string;
  warning: string | null;
  tools: string[];
  requiredEvidence: string[];
  targetPartId: string;
  targetNodeId: string;
  animationAction: 'HIGHLIGHT' | 'ISOLATE' | 'REMOVE' | 'INSTALL' | 'RESET';
  completionGate: NonNullable<RepairStep3D['completion_gate']>;
  technicalValue: string | null;
  technicalValueMessage: string | null;
}

interface PilotProcedure {
  id: string;
  title: string;
  difficulty: 'INTERMEDIATE' | 'ADVANCED';
  safetyLevel: GuidedRepairProcedure['safety_level'];
  targetPartIds: string[];
  publicationState: 'REVIEW_REQUIRED';
  executionPolicy: 'TRAINING_ONLY_REVIEW_REQUIRED';
  steps: PilotStep[];
}

interface PilotPack {
  schemaVersion: number;
  packId: string;
  packVersion: string;
  publicationState: 'REVIEW_REQUIRED';
  autoPublishAllowed: false;
  contentSha256: string;
  disclaimer: string;
  parts: PilotPart[];
  procedures: PilotProcedure[];
}

export interface RepairProgress {
  procedureId: string;
  packVersion: string;
  state: RepairProgressState;
  completedStepIds: string[];
  blockedStepId: string | null;
  updatedAt: string;
}

const pack = pilotPackJson as unknown as PilotPack;

const localizedLabels: Record<string, string> = {
  Suspension: 'Suspensión',
  'Suspension / Chasis': 'Suspensión / Chasis',
  'Direccion / Suspension': 'Dirección / Suspensión',
  Direccion: 'Dirección',
  Electrico: 'Eléctrico',
  Transmision: 'Transmisión',
  'Transmision / Tren motriz': 'Transmisión / Tren motriz',
  Fijacion: 'Fijación',
  Rotulas: 'Rótulas',
  'Rotulas de suspension': 'Rótulas de suspensión',
};

const localize = (value: string): string => localizedLabels[value] ?? value;

function assertPilotPack(value: PilotPack): void {
  if (value.schemaVersion !== 1 || value.packId !== 'pilot_hyundai_accent_verna_2005_front_end') {
    throw new Error('Unsupported MEET parts catalog pack');
  }
  if (value.autoPublishAllowed || value.publicationState !== 'REVIEW_REQUIRED') {
    throw new Error('Pilot catalog cannot be auto-published');
  }
  if (value.parts.length < 50 || value.procedures.length < 3) {
    throw new Error('Pilot catalog is incomplete');
  }
  const ids = new Set<string>();
  for (const part of value.parts) {
    if (ids.has(part.id)) throw new Error(`Duplicate part id: ${part.id}`);
    ids.add(part.id);
    if (!part.sourceRefs.length) throw new Error(`Missing source for ${part.id}`);
    if (part.compatibilityState !== 'REQUIRES_VERIFICATION' || part.confidence !== 'UNVERIFIED') {
      throw new Error(`Unsafe promotion for ${part.id}`);
    }
    if (Object.values(part.technicalSpecifications).some(item => item !== null)) {
      throw new Error(`Unverified technical specification for ${part.id}`);
    }
    if (part.threeDimensionalBinding.nodeId !== part.id) {
      throw new Error(`Broken 3D binding for ${part.id}`);
    }
  }
  for (const procedure of value.procedures) {
    for (const step of procedure.steps) {
      if (!ids.has(step.targetPartId) || !ids.has(step.targetNodeId)) {
        throw new Error(`Broken procedure target: ${step.id}`);
      }
      if (step.completionGate === 'VERIFIED_TORQUE_REQUIRED' && step.technicalValue !== null) {
        throw new Error(`Unverified torque exposed by ${step.id}`);
      }
    }
  }
}

assertPilotPack(pack);

const animationMap: Record<PilotStep['animationAction'], RepairStep3D['animation_action']> = {
  HIGHLIGHT: 'NONE',
  ISOLATE: 'EXPLODE',
  REMOVE: 'TRANSLATE_X',
  INSTALL: 'TRANSLATE_X',
  RESET: 'NONE',
};

function stepType(step: PilotStep): RepairStep3D['type'] {
  if (step.completionGate === 'VERIFIED_TORQUE_REQUIRED') return 'TORQUE';
  if (step.completionGate === 'ALIGNMENT_EVIDENCE_REQUIRED') return 'ALIGN';
  if (step.animationAction === 'REMOVE') return 'DISASSEMBLE';
  if (step.animationAction === 'INSTALL') return 'ASSEMBLE';
  return 'INSPECT';
}

export const SOURCE_BACKED_PARTS_CATALOG: DetailedPart[] = pack.parts.map(part => ({
  id: part.id,
  name: part.nameEs,
  aliases: [...part.aliases, part.nameEn],
  category: localize(part.category),
  system: localize(part.system),
  subsystem: localize(part.subsystem),
  assembly: localize(part.assembly),
  subassembly: part.subassembly ? localize(part.subassembly) : undefined,
  description: part.description,
  position: part.position,
  specification: {
    oem_number: part.technicalSpecifications.oemNumber ?? '',
    equivalent_numbers: [],
    dimensions: part.technicalSpecifications.dimensions ?? '',
    material: part.technicalSpecifications.material ?? '',
    torque_nm: part.technicalSpecifications.torque ?? undefined,
    pinout: part.technicalSpecifications.pinout ?? undefined,
  },
  symptoms: [],
  related_dtcs: [],
  confidence_level: 'UNCONFIRMED',
  publication_state: part.publicationState,
  compatibility_state: part.compatibilityState,
  compatibility_message: part.compatibilityMessage,
  required_compatibility_evidence: part.requiredCompatibilityEvidence,
  visual_authority: part.threeDimensionalBinding.visualAuthority,
  source_refs: part.sourceRefs.map(ref => ({
    source_file_name: ref.sourceFileName,
    source_document_sha256: ref.sourceDocumentSha256,
    source_block_id: ref.sourceBlockId,
    source_text_hash: ref.sourceTextHash,
    section_path: ref.sectionPath,
    review_status: ref.reviewStatus,
  })),
}));

export const SOURCE_BACKED_REPAIR_PROCEDURES: GuidedRepairProcedure[] = pack.procedures.map(procedure => ({
  id: procedure.id,
  title: procedure.title,
  vehicle_applicability: 'Perfil piloto; compatibilidad exacta pendiente de VIN/OEM y fuente aprobada.',
  estimated_duration_min: 0,
  difficulty: procedure.difficulty === 'ADVANCED' ? 'HARD' : 'MEDIUM',
  safety_level: procedure.safetyLevel,
  prerequisites: ['Validar el vehiculo, la pieza y el procedimiento aplicable antes de intervenir.'],
  steps: procedure.steps.map(step => ({
    id: step.id,
    order: step.order,
    title: step.title,
    description: step.instruction,
    type: stepType(step),
    target_node_id: step.targetNodeId,
    animation_action: animationMap[step.animationAction],
    required_tools: step.tools,
    torque_spec: step.completionGate === 'VERIFIED_TORQUE_REQUIRED'
      ? (step.technicalValueMessage ?? 'No confirmado para esta variante')
      : undefined,
    warning_notes: step.warning ?? undefined,
    completion_gate: step.completionGate,
    required_evidence: step.requiredEvidence,
    technical_value_message: step.technicalValueMessage ?? undefined,
  })),
  final_verification: procedure.id.startsWith('verify_')
    ? ['Alineación documentada.', 'Prueba funcional controlada.', 'Reinspección final registrada.']
    : ['Continuar con el procedimiento de verificación posterior antes de cerrar el trabajo.'],
  publication_state: procedure.publicationState,
  execution_policy: procedure.executionPolicy,
}));

export const SOURCE_BACKED_PACK_META = Object.freeze({
  packId: pack.packId,
  packVersion: pack.packVersion,
  contentSha256: pack.contentSha256,
  publicationState: pack.publicationState,
  disclaimer: pack.disclaimer,
});

function normalized(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

export function searchSourceBackedParts(query: string): DetailedPart[] {
  const term = normalized(query);
  if (!term) return SOURCE_BACKED_PARTS_CATALOG;
  return SOURCE_BACKED_PARTS_CATALOG.filter(part =>
    [part.name, ...part.aliases, part.category, part.system, part.subsystem, part.assembly]
      .some(value => normalized(value).includes(term))
  );
}

export function canCompleteRepairStep(
  step: RepairStep3D,
  evidenceIds: ReadonlySet<string> = new Set(),
  hasVerifiedTechnicalClaim = false,
): { allowed: boolean; reason: string | null } {
  if (step.completion_gate === 'VERIFIED_TORQUE_REQUIRED' && !hasVerifiedTechnicalClaim) {
    return { allowed: false, reason: 'Torque no confirmado para esta variante. Adjunte una fuente técnica verificada.' };
  }
  if (step.completion_gate !== 'MANUAL_CONFIRMATION') {
    const missing = (step.required_evidence ?? []).filter(item => !evidenceIds.has(item));
    if (missing.length > 0) {
      return { allowed: false, reason: `Falta evidencia requerida: ${missing.join(', ')}` };
    }
  }
  return { allowed: true, reason: null };
}

const progressKey = (procedureId: string): string =>
  `meet.repair-progress.${pack.packVersion}.${procedureId}`;

export function loadRepairProgress(procedureId: string): RepairProgress {
  const empty: RepairProgress = {
    procedureId,
    packVersion: pack.packVersion,
    state: 'NOT_STARTED',
    completedStepIds: [],
    blockedStepId: null,
    updatedAt: new Date(0).toISOString(),
  };
  if (typeof window === 'undefined') return empty;
  try {
    const stored = window.localStorage.getItem(progressKey(procedureId));
    if (!stored) return empty;
    const parsed = JSON.parse(stored) as RepairProgress;
    return parsed.procedureId === procedureId && parsed.packVersion === pack.packVersion ? parsed : empty;
  } catch {
    return empty;
  }
}

export function saveRepairProgress(progress: RepairProgress): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(progressKey(progress.procedureId), JSON.stringify(progress));
  } catch {
    // Progress remains usable in memory when storage is unavailable or full.
  }
}
