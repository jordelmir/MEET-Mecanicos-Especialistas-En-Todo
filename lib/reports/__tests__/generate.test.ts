/**
 * Tests for lib/reports/generate.ts
 *
 * Each builder gets a happy-path + a privacy/pathological-path test.
 * finalizeDraft must produce a frozen-shape report and a chainable
 * integrityHash. applySignature must lock the report at SIGNED and
 * re-hash the signature.
 */

import { describe, expect, it } from 'vitest';

import {
  applyPrivacy,
  applySignature,
  buildDvirDraft,
  buildPeritajeDraft,
  buildPostScanDraft,
  buildPreScanDraft,
  buildRepairEvidenceDraft,
  finalizeDraft,
  validateDraftForSign,
} from '../generate';
import type {
  DiagnosticSnapshot,
  PeritajeChecklist,
} from '../types';

const baseSnapshot: DiagnosticSnapshot = {
  id: 'snap_1',
  vehicleId: 'v1',
  sessionId: 's1',
  createdAtMs: 1_700_000_000_000,
  dtcsActive: ['P0230'],
  dtcsPending: [],
  dtcsPermanent: [],
  freezeFramePidValues: {},
  livePids: {},
  readiness: {},
  ecuVoltage: 14.1,
  rpm: 800,
  coolantTempC: 88,
  speedKph: 0,
  engineLoadPct: 20,
  fuelTrimStft: 0,
  fuelTrimLtft: 0,
  rawFrames: [],
  notes: '',
  provenance: { kind: 'REAL' },
};

const peritajeOk: PeritajeChecklist = {
  overallScore: 75,
  verdict: 'APROBADO_CON_OBSERVACIONES',
  confidence: 0.85,
  criticalAlerts: [],
  recommendation: 'Negociar',
  sectionScores: [
    { section: 'MOTOR', score: 8, notes: '', evidenceUris: [] },
    { section: 'FRENOS', score: 6, notes: '', evidenceUris: [] },
    { section: 'ELECTRICO', score: 9, notes: '', evidenceUris: [] },
  ],
};

describe('applyPrivacy', () => {
  it('keeps vin and plate when no redaction requested', () => {
    const out = applyPrivacy(
      { vin: 'KMHCN46C18U123456', plate: 'ABC-123' },
      { redactVin: false, redactPlate: false, redactLocation: false, publicShare: false },
    );
    expect(out.vin).toBe('KMHCN46C18U123456');
    expect(out.plate).toBe('ABC-123');
  });

  it('redacts the vin to a partial form', () => {
    const out = applyPrivacy(
      { vin: 'KMHCN46C18U123456', plate: 'ABC-123' },
      { redactVin: true, redactPlate: false, redactLocation: false, publicShare: false },
    );
    expect(out.vin).toBe('KMH…456');
  });

  it('redacts the plate fully', () => {
    const out = applyPrivacy(
      { vin: 'KMHCN46C18U123456', plate: 'ABC-123' },
      { redactVin: false, redactPlate: true, redactLocation: false, publicShare: false },
    );
    expect(out.plate).toBe('•••');
  });
});

describe('buildPreScanDraft', () => {
  it('adds a single OBD_SNAPSHOT evidence when the adapter is present', () => {
    const draft = buildPreScanDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      snapshot: baseSnapshot,
      notes: '',
    });
    expect(draft.reportType).toBe('PRE_SCAN_REPORT');
    expect(draft.evidence).toHaveLength(1);
    expect(draft.evidence[0].type).toBe('OBD_SNAPSHOT');
  });

  it('adds a REPAIR_NOTE evidence when the adapter is absent', () => {
    const draft = buildPreScanDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      snapshot: null,
      notes: '',
    });
    expect(draft.evidence).toHaveLength(1);
    expect(draft.evidence[0].type).toBe('REPAIR_NOTE');
    expect(draft.evidence[0].description.toLowerCase()).toContain('no se capturó');
  });
});

describe('buildPostScanDraft', () => {
  it('summarizes the DTC diff', () => {
    const { dtcSummary } = buildPostScanDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      preSnapshot: { ...baseSnapshot, dtcsActive: ['P0230', 'P1709'] },
      postSnapshot: { ...baseSnapshot, dtcsActive: ['P1709'] },
      notes: '',
    });
    expect(dtcSummary.before).toEqual(['P0230', 'P1709']);
    expect(dtcSummary.after).toEqual(['P1709']);
    expect(dtcSummary.cleared).toEqual(['P0230']);
    expect(dtcSummary.persistent).toEqual(['P1709']);
  });

  it('emits a warning note when there is no post-snapshot', () => {
    const { draft } = buildPostScanDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      preSnapshot: baseSnapshot,
      postSnapshot: null,
      notes: 'fin',
    });
    expect(draft.notes.toLowerCase()).toContain('no se puede afirmar "reparado"');
  });
});

describe('buildRepairEvidenceDraft / buildPeritajeDraft / buildDvirDraft', () => {
  it('attaches photo evidence from URIs to the repair report', () => {
    const { draft } = buildRepairEvidenceDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      preSnapshot: baseSnapshot,
      postSnapshot: baseSnapshot,
      notes: 'fin',
      evidenceUris: ['file://a.jpg', 'file://b.jpg'],
      repairActions: [
        {
          actionType: 'REPLACE',
          component: 'Bobina',
          dtcRelated: 'P0301',
          description: 'cambio',
          partUsed: 'NGK',
          supplier: 'repuestera',
          mechanic: 'juan',
          cost: 35,
          currency: 'USD',
          warrantyDays: 90,
        },
      ],
    });
    expect(draft.reportType).toBe('REPAIR_EVIDENCE_REPORT');
    const photos = draft.evidence.filter((e) => e.type === 'PHOTO');
    expect(photos).toHaveLength(2);
  });

  it('peritaje draft carries the checklist and uses peritaje photo evidence', () => {
    const draft = buildPeritajeDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      snapshot: baseSnapshot,
      checklist: {
        ...peritajeOk,
        sectionScores: [
          ...peritajeOk.sectionScores,
          { section: 'CARROCERIA', score: 7, notes: '', evidenceUris: ['file://d.jpg'] },
        ],
      },
    });
    expect(draft.peritaje).toBeDefined();
    expect(draft.evidence.length).toBeGreaterThan(0);
  });

  it('DVIR draft encodes the checklist in a REPAIR_NOTE evidence', () => {
    const draft = buildDvirDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      operator: 'Luis',
      criticalNotes: 'n/a',
      brakesOk: true,
      lightsOk: false,
      tiresOk: true,
      fluidsOk: true,
    });
    expect(draft.reportType).toBe('DVIR_REPORT');
    expect(draft.evidence[0].type).toBe('REPAIR_NOTE');
    expect(draft.evidence[0].description).toContain('lights=FAIL');
  });
});

describe('finalizeDraft', () => {
  it('produces a DRAFT report with an integrityHash and chainable previousHash', async () => {
    const draft = buildPreScanDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      snapshot: baseSnapshot,
      notes: '',
    });
    const finalized = await finalizeDraft({ draft, previousHash: null });
    expect(finalized.report.status).toBe('DRAFT');
    expect(finalized.report.integrityHash).toMatch(/^[a-f0-9]{64}$/);
    expect(finalized.report.previousHash).toBeNull();
    expect(finalized.snapshotHash).toMatch(/^[a-f0-9]{64}$/);
    expect(finalized.evidence[0].hash).toMatch(/^[a-f0-9]{64}$/);
  });

  it('changes the integrityHash when previousHash changes', async () => {
    const draft = buildPreScanDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      snapshot: baseSnapshot,
      notes: '',
    });
    const a = await finalizeDraft({ draft, previousHash: null });
    const b = await finalizeDraft({ draft, previousHash: 'PREV-A' });
    expect(a.report.integrityHash).not.toBe(b.report.integrityHash);
  });
});

describe('applySignature', () => {
  it('moves the report to SIGNED and produces a signature', async () => {
    const draft = buildPreScanDraft({
      vehicleId: 'v1',
      userId: 'u1',
      odometerKm: 100_000,
      vin: 'KMHCN46C18U123456',
      plate: 'ABC-123',
      snapshot: baseSnapshot,
      notes: '',
    });
    const finalized = await finalizeDraft({ draft, previousHash: null });
    const signed = await applySignature({
      finalized,
      signerName: 'Alice',
      signerRole: 'OPERATOR',
      signatureImageUri: 'file://sig.png',
      deviceIdHash: 'devhash',
    });
    expect(signed.report.status).toBe('SIGNED');
    expect(signed.report.signedAt).not.toBeNull();
    expect(signed.signature.integrityHash).toMatch(/^[a-f0-9]{64}$/);
  });
});

describe('validateDraftForSign', () => {
  it('flags no vehicle as BLOCK', () => {
    const issues = validateDraftForSign({
      vehicleId: '',
      userId: 'u1',
      reportType: 'PRE_SCAN_REPORT',
      title: 't',
      odometerKm: null,
      vin: null,
      plate: null,
      snapshot: null,
      evidence: [],
      repairActions: [],
      privacy: { redactVin: false, redactPlate: false, redactLocation: false, publicShare: false },
      peritaje: null,
      notes: '',
    });
    expect(issues.some((i) => i.code === 'NO_VEHICLE' && i.severity === 'BLOCK')).toBe(true);
  });

  it('flags peritaje without checklist as BLOCK', () => {
    const issues = validateDraftForSign({
      vehicleId: 'v1',
      userId: 'u1',
      reportType: 'PRE_PURCHASE_INSPECTION_REPORT',
      title: 't',
      odometerKm: null,
      vin: null,
      plate: null,
      snapshot: null,
      evidence: [],
      repairActions: [],
      privacy: { redactVin: false, redactPlate: false, redactLocation: false, publicShare: false },
      peritaje: null,
      notes: '',
    });
    expect(issues.some((i) => i.code === 'NO_PERITAJE')).toBe(true);
  });

  it('warns on POST_SCAN without post-snapshot', () => {
    const issues = validateDraftForSign({
      vehicleId: 'v1',
      userId: 'u1',
      reportType: 'POST_SCAN_REPORT',
      title: 't',
      odometerKm: null,
      vin: null,
      plate: null,
      snapshot: null,
      evidence: [],
      repairActions: [],
      privacy: { redactVin: false, redactPlate: false, redactLocation: false, publicShare: false },
      peritaje: null,
      notes: '',
    });
    expect(issues.some((i) => i.code === 'POST_NO_SNAPSHOT' && i.severity === 'WARN')).toBe(true);
  });
});
