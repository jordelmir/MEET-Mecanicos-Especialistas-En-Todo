/**
 * Humanity OS / Human Capability Platform — Canonical TypeScript Contracts.
 * Guarantees cross-runtime parity with Android Kotlin models.
 */

export type TruthState =
  | 'AUTHORITATIVE'
  | 'OBSERVED'
  | 'PEER_REVIEWED'
  | 'DERIVED'
  | 'EXPERT_CONSENSUS'
  | 'ESTIMATED'
  | 'ANECDOTAL'
  | 'DISPUTED'
  | 'HYPOTHESIS'
  | 'UNKNOWN';

export type ExecutionTruthState =
  | 'NOT_EXECUTED'
  | 'SIMULATED'
  | 'GUIDED'
  | 'OBSERVED'
  | 'PHYSICALLY_VERIFIED';

export type SafetyLevel =
  | 'KNOWLEDGE_ONLY'
  | 'SIMULATION_SAFE'
  | 'LOW_RISK_PRACTICE'
  | 'SUPERVISED_REQUIRED'
  | 'LICENSE_REQUIRED'
  | 'PROHIBITED_UNSUPERVISED';

export type CapabilityLevel =
  | 'L0_UNKNOWN'
  | 'L1_EXPOSED'
  | 'L2_UNDERSTOOD'
  | 'L3_SIMULATED'
  | 'L4_GUIDED_PRACTICE'
  | 'L5_DEMONSTRATED'
  | 'L6_INDEPENDENT'
  | 'L7_EXPERT_VERIFIED'
  | 'L8_TEACHER';

export type EvidenceType =
  | 'ASSESSMENT'
  | 'SIMULATION'
  | 'PHOTO'
  | 'MEASUREMENT'
  | 'OBD_SESSION'
  | 'DIAGNOSTIC_REPORT'
  | 'REPAIR_REPORT'
  | 'EXPERT_REVIEW';

export interface KnowledgeDomain {
  id: string;
  name: string;
  description: string;
  parentDomainId?: string | null;
  iconGlyph: string;
}

export interface KnowledgeSource {
  id: string;
  title: string;
  authorOrPublisher: string;
  url: string;
  sourceType: string;
  citationNote: string;
}

export interface KnowledgeNode {
  id: string;
  domainId: string;
  title: string;
  summary: string;
  truthState: TruthState;
  safetyLevel: SafetyLevel;
  prerequisiteNodeIds: string[];
  enablesSkillIds: string[];
  sources: KnowledgeSource[];
  version: string;
  updatedAtEpochMs: number;
}

export interface Skill {
  id: string;
  domainId: string;
  name: string;
  description: string;
  requiredKnowledgeIds: string[];
  prerequisiteSkillIds: string[];
  safetyLevel: SafetyLevel;
  minimumEvidenceForMastery: number;
}

export interface EvidenceItem {
  id: string;
  userId: string;
  skillId: string;
  missionId?: string | null;
  evidenceType: EvidenceType;
  executionTruth: ExecutionTruthState;
  evidencePayloadHash: string;
  capturedAtEpochMs: number;
  metadata: Record<string, string>;
}

export interface CapabilityRecord {
  userId: string;
  skillId: string;
  currentLevel: CapabilityLevel;
  demonstratedEvidenceCount: number;
  lastDemonstratedEpochMs: number;
  verifiedByExpert: boolean;
}
