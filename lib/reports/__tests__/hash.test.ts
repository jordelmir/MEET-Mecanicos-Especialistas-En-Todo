/**
 * Tests for lib/reports/hash.ts
 *
 * The integrity chain is the security primitive of the entire reports
 * pipeline. These tests pin determinism, isolation, and chain verification.
 */

import { describe, expect, it } from 'vitest';

import {
  canonicalReportString,
  canonicalSnapshotString,
  hashDeviceId,
  hashEvidence,
  hashPeritaje,
  hashReportDraft,
  hashRepairAction,
  hashSignature,
  hashSnapshot,
  sha256Hex,
  verifyChain,
} from '../hash';
import type {
  DiagnosticSnapshot,
  PeritajeChecklist,
  ReportEvidence,
  RepairAction,
} from '../types';

const baseSnapshot: DiagnosticSnapshot = {
  id: 'snap_1',
  vehicleId: 'veh_accent_2005',
  sessionId: 'sess_42',
  createdAtMs: 1_700_000_000_000,
  dtcsActive: ['P0230', 'P1709'],
  dtcsPending: [],
  dtcsPermanent: [],
  freezeFramePidValues: { RPM: 850, ECT: 88 },
  livePids: {},
  readiness: { Misfire: true, Fuel: true },
  ecuVoltage: 14.1,
  rpm: 850,
  coolantTempC: 88,
  speedKph: 0,
  engineLoadPct: 22.5,
  fuelTrimStft: 0.5,
  fuelTrimLtft: -1.2,
  rawFrames: [],
  notes: '',
  liveFromAdapter: true,
  provenance: 'LIVE_OBD',
};

describe('sha256Hex', () => {
  it('produces 64-char hex', async () => {
    const h = await sha256Hex('hello world');
    expect(h).toMatch(/^[a-f0-9]{64}$/);
  });

  it('is deterministic', async () => {
    const a = await sha256Hex('MEET Vanguard 2026');
    const b = await sha256Hex('MEET Vanguard 2026');
    expect(a).toBe(b);
  });

  it('matches the known SHA-256 of "abc"', async () => {
    const h = await sha256Hex('abc');
    expect(h).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    );
  });
});

describe('canonicalSnapshotString', () => {
  it('produces a stable, identical string for the same input', () => {
    const a = canonicalSnapshotString(baseSnapshot);
    const b = canonicalSnapshotString({ ...baseSnapshot });
    expect(a).toBe(b);
  });

  it('sorts DTC lists so order does not change the hash', () => {
    const a = canonicalSnapshotString({
      ...baseSnapshot,
      dtcsActive: ['P0230', 'P1709'],
    });
    const b = canonicalSnapshotString({
      ...baseSnapshot,
      dtcsActive: ['P1709', 'P0230'],
    });
    expect(a).toBe(b);
  });

  it('sorts readiness + freeze frame keys', () => {
    const a = canonicalSnapshotString({
      ...baseSnapshot,
      readiness: { Misfire: true, Fuel: true },
      freezeFramePidValues: { RPM: 850, ECT: 88 },
    });
    const b = canonicalSnapshotString({
      ...baseSnapshot,
      readiness: { Fuel: true, Misfire: true },
      freezeFramePidValues: { ECT: 88, RPM: 850 },
    });
    expect(a).toBe(b);
  });

  it('changes the hash when a real value changes', () => {
    const a = canonicalSnapshotString(baseSnapshot);
    const b = canonicalSnapshotString({ ...baseSnapshot, rpm: 1200 });
    expect(a).not.toBe(b);
  });
});

describe('hashSnapshot', () => {
  it('is reproducible across calls', async () => {
    const a = await hashSnapshot(baseSnapshot);
    const b = await hashSnapshot({ ...baseSnapshot });
    expect(a).toBe(b);
  });

  it('changes when any captured field changes', async () => {
    const a = await hashSnapshot(baseSnapshot);
    const b = await hashSnapshot({ ...baseSnapshot, ecuVoltage: 13.9 });
    expect(a).not.toBe(b);
  });

  it('changes when a DTC is added', async () => {
    const a = await hashSnapshot(baseSnapshot);
    const b = await hashSnapshot({
      ...baseSnapshot,
      dtcsActive: ['P0230', 'P1709', 'P0420'],
    });
    expect(a).not.toBe(b);
  });
});

describe('hashEvidence / hashRepairAction / hashPeritaje / hashSignature', () => {
  it('hashes a sample evidence deterministically', async () => {
    const e: ReportEvidence = {
      id: 'evi_1',
      reportId: 'rpt_1',
      type: 'PHOTO',
      label: 'Bobina quemada',
      description: 'Foto antes',
      uri: 'file://photo.jpg',
      hash: 'aa',
      capturedAt: 1_700_000_000_000,
      lat: null,
      lng: null,
    };
    const a = await hashEvidence(e);
    const b = await hashEvidence({ ...e });
    expect(a).toBe(b);
  });

  it('hashes a repair action ignoring the id+createdAt', async () => {
    const base: RepairAction = {
      id: 'act_1',
      reportId: 'rpt_1',
      actionType: 'REPLACE',
      component: 'Bobina',
      dtcRelated: 'P0301',
      description: 'Reemplazo de bobina',
      partUsed: 'NGK U5156',
      supplier: 'Repuestera X',
      mechanic: 'Juan',
      cost: 35,
      currency: 'USD',
      warrantyDays: 90,
      createdAt: 1_700_000_000_000,
    };
    const a = await hashRepairAction(base);
    const b = await hashRepairAction({ ...base, id: 'act_2' });
    expect(a).toBe(b);
  });

  it('hashes a peritaje deterministically and order-independent in criticalAlerts', async () => {
    const base: PeritajeChecklist = {
      overallScore: 78,
      verdict: 'APROBADO_CON_OBSERVACIONES',
      confidence: 0.85,
      criticalAlerts: ['Fuga aceite', 'Pastillas gastadas'],
      recommendation: 'Negociar precio',
      sectionScores: [
        { section: 'FRENOS', score: 7, notes: 'Pastillas al 30%', evidenceUris: [] },
        { section: 'MOTOR', score: 9, notes: 'OK', evidenceUris: [] },
      ],
    };
    const a = await hashPeritaje(base);
    const b = await hashPeritaje({
      ...base,
      criticalAlerts: ['Pastillas gastadas', 'Fuga aceite'],
      sectionScores: [
        { section: 'MOTOR', score: 9, notes: 'OK', evidenceUris: [] },
        { section: 'FRENOS', score: 7, notes: 'Pastillas al 30%', evidenceUris: [] },
      ],
    });
    expect(a).toBe(b);
  });

  it('hashSignature changes when signer name changes', async () => {
    const a = await hashSignature('h1', 'Alice', 'OPERATOR', 1, 'dev1');
    const b = await hashSignature('h1', 'Bob', 'OPERATOR', 1, 'dev1');
    expect(a).not.toBe(b);
  });
});

describe('hashReportDraft', () => {
  const baseDraft = {
    vehicleId: 'v1',
    userId: 'u1',
    reportType: 'PRE_SCAN_REPORT' as const,
    title: 'Pre-Scan',
    odometerKm: 100_000,
    vin: 'KMHCN46C18U123456',
    plate: 'ABC-123',
    snapshot: baseSnapshot,
    evidence: [] as never[],
    repairActions: [] as never[],
    privacy: {
      redactVin: false,
      redactPlate: false,
      redactLocation: false,
      publicShare: false,
    },
    peritaje: null,
    notes: 'Inspección pre-compra',
  };

  it('changes when previousHash changes (chain)', async () => {
    const a = await hashReportDraft(baseDraft, 'snap-h', [], [], null, null);
    const b = await hashReportDraft(baseDraft, 'snap-h', [], [], null, 'PREV-H');
    expect(a).not.toBe(b);
  });

  it('changes when vin changes', async () => {
    const a = await hashReportDraft(baseDraft, 'snap-h', [], [], null, null);
    const b = await hashReportDraft(
      { ...baseDraft, vin: 'KMHCN46C18U999999' },
      'snap-h',
      [],
      [],
      null,
      null,
    );
    expect(a).not.toBe(b);
  });

  it('changes when snapshot hash changes', async () => {
    const a = await hashReportDraft(baseDraft, 'snap-h1', [], [], null, null);
    const b = await hashReportDraft(baseDraft, 'snap-h2', [], [], null, null);
    expect(a).not.toBe(b);
  });

  it('canonicalReportString is stable and sortable', () => {
    const a = canonicalReportString(baseDraft, 's', [], [], null, null);
    const b = canonicalReportString(baseDraft, 's', [], [], null, null);
    expect(a).toBe(b);
  });
});

describe('verifyChain', () => {
  it('returns ok for an empty list (genesis)', () => {
    expect(verifyChain([])).toEqual({ ok: true, brokenAt: null });
  });

  it('returns ok for a single signed report with previousHash = null', () => {
    const r = {
      id: 'r1',
      vehicleId: 'v1',
      generatedAt: 1,
      integrityHash: 'A',
      previousHash: null,
    };
    expect(verifyChain([r])).toEqual({ ok: true, brokenAt: null });
  });

  it('verifies a 2-link chain', () => {
    const a = {
      id: 'r1',
      vehicleId: 'v1',
      generatedAt: 1,
      integrityHash: 'A',
      previousHash: null,
    };
    const b = {
      id: 'r2',
      vehicleId: 'v1',
      generatedAt: 2,
      integrityHash: 'B',
      previousHash: 'A',
    };
    expect(verifyChain([a, b])).toEqual({ ok: true, brokenAt: null });
  });

  it('detects a broken link', () => {
    const a = {
      id: 'r1',
      vehicleId: 'v1',
      generatedAt: 1,
      integrityHash: 'A',
      previousHash: null,
    };
    const broken = {
      id: 'r2',
      vehicleId: 'v1',
      generatedAt: 2,
      integrityHash: 'B',
      previousHash: 'WRONG',
    };
    expect(verifyChain([a, broken])).toEqual({ ok: false, brokenAt: 'r2' });
  });

  it('verifies regardless of input order (sorts by generatedAt)', () => {
    const a = {
      id: 'r1',
      vehicleId: 'v1',
      generatedAt: 1,
      integrityHash: 'A',
      previousHash: null,
    };
    const b = {
      id: 'r2',
      vehicleId: 'v1',
      generatedAt: 2,
      integrityHash: 'B',
      previousHash: 'A',
    };
    expect(verifyChain([b, a])).toEqual({ ok: true, brokenAt: null });
  });
});

describe('hashDeviceId', () => {
  it('produces a stable hash for the same input', async () => {
    const a = await hashDeviceId('android-1234');
    const b = await hashDeviceId('android-1234');
    expect(a).toBe(b);
  });

  it('produces different hashes for different inputs', async () => {
    const a = await hashDeviceId('android-1234');
    const b = await hashDeviceId('android-9999');
    expect(a).not.toBe(b);
  });
});
