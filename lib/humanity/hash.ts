/**
 * Cross-runtime parity hashing for Human Capability domain primitives.
 * Must match Kotlin's HumanityParityEngine byte-for-byte.
 */

import { createHash } from 'node:crypto';
import type { CapabilityRecord, EvidenceItem } from './types.ts';

export function canonicalEvidenceString(item: EvidenceItem): string {
  return [
    'EVIDENCE_V1',
    item.userId,
    item.skillId,
    item.missionId ?? '',
    item.evidenceType,
    item.executionTruth,
    item.evidencePayloadHash,
  ].join('|');
}

export function canonicalCapabilityString(record: CapabilityRecord): string {
  return [
    'CAPABILITY_V1',
    record.userId,
    record.skillId,
    record.currentLevel,
    record.demonstratedEvidenceCount.toString(),
    record.verifiedByExpert ? '1' : '0',
  ].join('|');
}

export function sha256Hex(content: string): string {
  return createHash('sha256').update(content, 'utf8').digest('hex');
}
