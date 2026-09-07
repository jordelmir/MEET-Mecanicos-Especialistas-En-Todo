/**
 * Tests for lib/reports/workflow.ts — Closed-Loop Forensic Repair Workflow Coordinator.
 */

import { describe, expect, it } from 'vitest';
import {
  startForensicRepairSession,
  proposePartsForSession,
  acceptQuoteAndStartWork,
  attachSessionEvidence,
  completeRepairWork,
  certifyRepairSession,
  auditDtcResolution,
} from '../workflow';
import { PROVENANCE_REAL } from '../types';

const vehicleId = 'veh-cr-492011';
const serviceId = 'srv-taller-sanjose-001';
const mechanicId = 'mech-eladio-mep-certified';

const preScan = {
  id: 'snap-pre-001',
  vehicleId,
  sessionId: 'session-srv-001',
  createdAtMs: 1700000000000,
  dtcsActive: ['P0230', 'P1709'],
  dtcsPending: [],
  dtcsPermanent: [],
  freezeFramePidValues: {},
  livePids: {},
  readiness: {},
  ecuVoltage: 14.1,
  rpm: 820,
  coolantTempC: 89,
  speedKph: 0,
  engineLoadPct: 20,
  fuelTrimStft: 0,
  fuelTrimLtft: 0,
  rawFrames: [],
  notes: '',
  provenance: PROVENANCE_REAL,
};

const postScan = {
  ...preScan,
  id: 'snap-post-002',
  createdAtMs: 1700000020000,
  dtcsActive: [],
};

describe('ForensicRepairWorkflowCoordinator (TypeScript)', () => {
  it('executes full closed-loop forensic workflow from prescan to certified report', async () => {
    // 1. Start Session
    let session = startForensicRepairSession(
      'sess-001',
      serviceId,
      vehicleId,
      mechanicId,
      preScan,
      1700000001000,
    );
    expect(session.state).toBe('PRE_SCAN_CAPTURED');

    // 2. Propose Parts
    session = proposePartsForSession(session);
    expect(session.state).toBe('COMPATIBLE_PARTS_PROPOSED');
    expect(session.proposedParts.length).toBeGreaterThan(0);

    // 3. Accept Quote & Start Work
    session = acceptQuoteAndStartWork(
      session,
      'quote-approved-99',
      ['fuel_pump_relay', 'fuel_pump_fuse'],
      1700000005000,
    );
    expect(session.state).toBe('WORK_IN_PROGRESS');

    // 4. Attach Evidence
    session = attachSessionEvidence(session, {
      id: 'ev-1',
      evidenceType: 'AFTER_PHOTO',
      uri: 'content://media/photos/relay_installed.jpg',
      sha256Hash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      recordedAtMs: 1700000010000,
      caption: 'Relé OEM instalado',
    });
    expect(session.evidenceList.length).toBe(1);

    // 5. Complete Physical Work
    session = completeRepairWork(session, 1700000015000);
    expect(session.state).toBe('POST_SCAN_REQUIRED');

    // 6. Certify Session with Post-Scan
    const certified = await certifyRepairSession(
      session,
      postScan,
      'https://meet.elysium.app/verify',
      1700000025000,
    );

    expect(certified.state).toBe('CERTIFIED_CLOSED');
    expect(certified.certificate).not.toBeNull();

    const cert = certified.certificate!;
    expect(cert.vehicleId).toBe(vehicleId);
    expect(cert.serviceId).toBe(serviceId);
    expect(cert.certifiedIntegrityHash).toHaveLength(64);

    // QR Verification (6 fields, zero personal names)
    expect(cert.qrPayload.reportId).toBe(cert.certificateId);
    expect(cert.qrPayload.vehicleId).toBe(vehicleId);
    expect(cert.qrPayload.reportType).toBe('POST_SCAN_REPORT');

    // DTC Audit
    const audit = certified.dtcAudit!;
    expect(audit.isFullyResolved).toBe(true);
    expect(audit.resolvedDtcs).toEqual(['P0230', 'P1709']);
    expect(audit.residualDtcs).toEqual([]);
    expect(audit.newDtcs).toEqual([]);
    expect(audit.clearedCodesRatio).toBe(1.0);
  });

  it('rejects certification if post-scan has identical hash to pre-scan', async () => {
    let session = startForensicRepairSession(
      'sess-001',
      serviceId,
      vehicleId,
      mechanicId,
      preScan,
      1700000001000,
    );
    session = completeRepairWork(
      { ...session, state: 'WORK_IN_PROGRESS' },
      1700000002000,
    );

    await expect(
      certifyRepairSession(session, preScan),
    ).rejects.toThrow('Post-Scan snapshot must be distinct');
  });

  it('audits residual and new DTCs with forensic precision', () => {
    const pre = { ...preScan, dtcsActive: ['P0300', 'P0301'] };
    const post = { ...preScan, dtcsActive: ['P0301', 'P0420'] };

    const audit = auditDtcResolution(pre, post);
    expect(audit.resolvedDtcs).toEqual(['P0300']);
    expect(audit.residualDtcs).toEqual(['P0301']);
    expect(audit.newDtcs).toEqual(['P0420']);
    expect(audit.isFullyResolved).toBe(false);
    expect(audit.clearedCodesRatio).toBeCloseTo(0.5, 2);
  });
});
