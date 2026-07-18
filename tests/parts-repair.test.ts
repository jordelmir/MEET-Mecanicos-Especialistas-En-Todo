import { describe, expect, it } from 'vitest';
import {
  SOURCE_BACKED_PACK_META,
  SOURCE_BACKED_PARTS_CATALOG,
  SOURCE_BACKED_REPAIR_PROCEDURES,
  canCompleteRepairStep,
  searchSourceBackedParts,
} from '../services/universalPartsCatalog';

describe('source-backed parts pack', () => {
  it('loads the review-only deterministic pilot', () => {
    expect(SOURCE_BACKED_PACK_META.packId).toBe('pilot_hyundai_accent_verna_2005_front_end');
    expect(SOURCE_BACKED_PACK_META.packVersion).toBe('1.0.0');
    expect(SOURCE_BACKED_PACK_META.publicationState).toBe('REVIEW_REQUIRED');
    expect(SOURCE_BACKED_PACK_META.contentSha256).toMatch(/^[a-f0-9]{64}$/);
  });

  it('contains 50 unique source-backed parts', () => {
    expect(SOURCE_BACKED_PARTS_CATALOG).toHaveLength(50);
    expect(new Set(SOURCE_BACKED_PARTS_CATALOG.map(part => part.id)).size).toBe(50);
    for (const part of SOURCE_BACKED_PARTS_CATALOG) {
      expect(part.source_refs?.length).toBeGreaterThan(0);
      expect(part.source_refs?.[0].source_document_sha256).toMatch(/^[a-f0-9]{64}$/);
      expect(part.source_refs?.[0].source_text_hash).toMatch(/^[a-f0-9]{64}$/);
      expect(part.visual_authority).toBe('GENERIC_SCHEMATIC');
    }
  });

  it('does not promote OEM, torque, material or dimensions without evidence', () => {
    for (const part of SOURCE_BACKED_PARTS_CATALOG) {
      expect(part.confidence_level).toBe('UNCONFIRMED');
      expect(part.publication_state).toBe('REVIEW_REQUIRED');
      expect(part.compatibility_state).toBe('REQUIRES_VERIFICATION');
      expect(part.specification.oem_number).toBe('');
      expect(part.specification.torque_nm).toBeUndefined();
      expect(part.specification.material).toBe('');
      expect(part.specification.dimensions).toBe('');
    }
  });

  it.each(['tijereta', 'trapecio', 'lower control arm'])(
    'finds the control arm through alias %s',
    query => {
      const results = searchSourceBackedParts(query);
      expect(results.some(part => part.id === 'front_left_lower_control_arm')).toBe(true);
      expect(results.some(part => part.id === 'front_right_lower_control_arm')).toBe(true);
    },
  );

  it('covers the complete front-end taxonomy', () => {
    const allTerms = SOURCE_BACKED_PARTS_CATALOG
      .flatMap(part => [part.name, ...part.aliases])
      .join(' ')
      .toLowerCase();
    for (const term of ['brazo', 'rotula', 'buje', 'estabilizadora', 'amortiguador', 'freno', 'abs']) {
      expect(allTerms.normalize('NFD').replace(/[\u0300-\u036f]/g, '')).toContain(term);
    }
  });
});

describe('review-only guided procedures', () => {
  it('contains inspection, replacement and final verification procedures', () => {
    expect(SOURCE_BACKED_REPAIR_PROCEDURES).toHaveLength(3);
    expect(SOURCE_BACKED_REPAIR_PROCEDURES.map(item => item.id)).toEqual([
      'inspect_front_left_lower_control_arm',
      'replace_front_left_lower_control_arm_training',
      'verify_front_left_lower_control_arm_service',
    ]);
  });

  it('uses semantic catalog IDs for every 3D target', () => {
    const knownIds = new Set(SOURCE_BACKED_PARTS_CATALOG.map(part => part.id));
    for (const procedure of SOURCE_BACKED_REPAIR_PROCEDURES) {
      for (const step of procedure.steps) {
        expect(step.target_node_id).toMatch(/^[a-z][a-z0-9_]*$/);
        expect(knownIds.has(step.target_node_id)).toBe(true);
      }
    }
  });

  it('keeps the torque value absent and blocks the torque step', () => {
    const torqueStep = SOURCE_BACKED_REPAIR_PROCEDURES
      .flatMap(procedure => procedure.steps)
      .find(step => step.completion_gate === 'VERIFIED_TORQUE_REQUIRED');
    expect(torqueStep).toBeDefined();
    expect(torqueStep?.torque_spec).toBe('No confirmado para esta variante');
    expect(torqueStep?.torque_spec).not.toMatch(/\d+\s*N.?m/i);
    expect(canCompleteRepairStep(torqueStep!)).toEqual({
      allowed: false,
      reason: 'Torque no confirmado para esta variante. Adjunte una fuente técnica verificada.',
    });
  });

  it('allows manual inspection progress but gates compatibility evidence', () => {
    const inspection = SOURCE_BACKED_REPAIR_PROCEDURES[0].steps[0];
    const replacement = SOURCE_BACKED_REPAIR_PROCEDURES[1].steps[0];
    expect(canCompleteRepairStep(inspection).allowed).toBe(true);
    expect(canCompleteRepairStep(replacement).allowed).toBe(false);
    expect(canCompleteRepairStep(
      replacement,
      new Set(replacement.required_evidence),
    ).allowed).toBe(true);
  });

  it('requires alignment and final controlled verification', () => {
    const verification = SOURCE_BACKED_REPAIR_PROCEDURES[2];
    expect(verification.steps.some(step => step.completion_gate === 'ALIGNMENT_EVIDENCE_REQUIRED')).toBe(true);
    expect(verification.steps.some(step => step.description.toLowerCase().includes('prueba'))).toBe(true);
    expect(verification.final_verification.length).toBeGreaterThanOrEqual(3);
  });

  it('retains warnings in the dangerous replacement flow', () => {
    const replacement = SOURCE_BACKED_REPAIR_PROCEDURES[1];
    expect(replacement.safety_level).toBe('DANGER');
    expect(replacement.steps.some(step => Boolean(step.warning_notes))).toBe(true);
    expect(replacement.execution_policy).toBe('TRAINING_ONLY_REVIEW_REQUIRED');
  });
});
