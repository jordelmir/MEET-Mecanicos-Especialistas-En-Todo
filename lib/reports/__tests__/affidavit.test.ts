/**
 * Tests for lib/reports/affidavit.ts — Bilingual Forensic Technical Affidavit Generator.
 */

import { describe, expect, it } from 'vitest';
import { generateAffidavit, encodeQrAffidavitPayload, AffidavitInput } from '../affidavit';
import { PROVENANCE_REAL } from '../types';

const mockPreScan = {
  id: 'snap-pre-01',
  vehicleId: 'veh-cr-449102',
  sessionId: 'sess-001',
  createdAtMs: 1700000000000,
  dtcsActive: ['P0171', 'P0300'],
  dtcsPending: [],
  dtcsPermanent: [],
  freezeFramePidValues: { STFT: 22.5, LTFT: 19.8 },
  livePids: {},
  readiness: {},
  ecuVoltage: 14.1,
  rpm: 850,
  coolantTempC: 89,
  speedKph: 0,
  engineLoadPct: 25,
  fuelTrimStft: 22.5,
  fuelTrimLtft: 19.8,
  rawFrames: [],
  notes: '',
  provenance: PROVENANCE_REAL,
};

const mockPostScan = {
  ...mockPreScan,
  id: 'snap-post-02',
  createdAtMs: 1700005000000,
  dtcsActive: [],
  fuelTrimStft: 1.2,
  fuelTrimLtft: 0.8,
};

const sampleInput: AffidavitInput = {
  affidavitNumber: 'AFF-2026-CR-0091',
  inspectorName: 'Ing. Manuel Brenes Chaves',
  inspectorCredentialNumber: 'MEP-INA-AUT-8891',
  jurisdiction: 'República de Costa Rica — Ley de Tránsito N.° 9078',
  vehicleId: 'veh-cr-449102',
  redactedVin: '3N1AB7AP8HY******',
  redactedPlate: '***-789',
  odometerKm: 142350,
  preScanSnapshot: mockPreScan,
  postScanSnapshot: mockPostScan,
  installedParts: [
    {
      partName: 'Sensor MAF Hitachi OEM',
      oemPartNumber: '22680-7S000',
      invoiceOrBatchNumber: 'FAC-2026-08122',
      verifiedByMultimeterOrPressure: true,
    },
  ],
  physicalMeasurements: {
    'Presión de riel (psi)': '43.5 psi',
    'Resistencia del inyector 1 (ohms)': '12.2 Ω',
  },
  evidenceHashes: [
    'b1a9f5d37612f1234567890abcdef1234567890abcdef1234567890abcdef12',
    'c2b8e4f58723a9876543210fedcba9876543210fedcba9876543210fedcba98',
  ],
  timestampMs: 1700006000000,
};

describe('ForensicAffidavitGenerator (TypeScript)', () => {
  it('generates Spanish formal affidavit with valid Merkle root and QR', async () => {
    const doc = await generateAffidavit(sampleInput, 'SPANISH_FORMAL');

    expect(doc.affidavitNumber).toBe('AFF-2026-CR-0091');
    expect(doc.title).toContain('DICTAMEN PERICIAL');
    expect(doc.bodyMarkdown).toContain('P0171');
    expect(doc.bodyMarkdown).toContain('22680-7S000');
    expect(doc.bodyMarkdown).toContain('142350 km');
    expect(doc.forensicMerkleRoot).toHaveLength(64);
    expect(doc.overallAffidavitHash).toHaveLength(64);

    // QR compliance check: 6 fields, zero raw personal data
    expect(doc.qrPayload.reportId).toBe(doc.affidavitNumber);
    expect(doc.qrPayload.vehicleId).toBe('veh-cr-449102');
    expect(doc.qrPayload.reportType).toBe('POST_SCAN_REPORT');

    const rawQr = encodeQrAffidavitPayload(doc.qrPayload);
    expect(rawQr.startsWith('v1|AFF-2026-CR-0091|')).toBe(true);
    expect(rawQr).not.toContain('Manuel');
  });

  it('generates English C1 forensic affidavit with epistemic modal hedging', async () => {
    const doc = await generateAffidavit(sampleInput, 'ENGLISH_C1_FORENSIC');

    expect(doc.title).toContain('FORENSIC ENGINEERING AFFIDAVIT');
    expect(doc.bodyMarkdown).toContain('The empirical sensor telemetry strongly suggests');
    expect(doc.bodyMarkdown).toContain('Under no circumstances should secondary component degradation be ruled out');
    expect(doc.bodyMarkdown).toContain('Empirically Resolved Codes**: P0171, P0300');
  });

  it('generates bilingual dual-column document with both languages', async () => {
    const doc = await generateAffidavit(sampleInput, 'BILINGUAL_DUAL_COLUMN');

    expect(doc.bodyMarkdown).toContain('DICTAMEN PERICIAL FORENSE');
    expect(doc.bodyMarkdown).toContain('AUTOMOTIVE FORENSIC ENGINEERING AFFIDAVIT');
  });
});
