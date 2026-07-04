/**
 * Parity tests: the web (TS) hash MUST match the Kotlin
 * `computeHash(...)` byte-for-byte for the same input. The vectors here
 * were computed by running the Kotlin side and copying the result.
 * If a future PR changes the canonicalization format, the vectors
 * must be regenerated and BOTH sides verified.
 */

import { describe, expect, it } from 'vitest';

import {
  PARITY_VECTORS,
  canonicalSnapshotString,
  hashSnapshot,
  kotlinDoubleToString,
} from '../hash';
import { DiagnosticProvenance } from '../types';

function vectorToSnapshot(
  v: typeof PARITY_VECTORS[number]['input'],
): import('../types').DiagnosticSnapshot {
  return {
    id: 'parity-snap',
    vehicleId: v.vehicleId,
    sessionId: v.sessionId,
    createdAtMs: v.createdAtMs,
    dtcsActive: v.dtcsActive,
    dtcsPending: v.dtcsPending,
    dtcsPermanent: v.dtcsPermanent,
    freezeFramePidValues: v.freezeFramePidValues,
    livePids: {},
    readiness: v.readiness,
    ecuVoltage: v.ecuVoltage,
    rpm: v.rpm,
    coolantTempC: v.coolantTempC,
    speedKph: v.speedKph,
    engineLoadPct: v.engineLoadPct,
    fuelTrimStft: v.fuelTrimStft,
    fuelTrimLtft: v.fuelTrimLtft,
    rawFrames: [],
    notes: '',
    provenance: { kind: 'REAL' } as DiagnosticProvenance,
  };
}

describe('kotlinDoubleToString', () => {
  it('always appends .0 for integers (Kotlin Double.toString parity)', () => {
    expect(kotlinDoubleToString(850)).toBe('850.0');
    expect(kotlinDoubleToString(0)).toBe('0.0');
    expect(kotlinDoubleToString(14)).toBe('14.0');
  });

  it('uses toString() for floats with non-zero decimals', () => {
    expect(kotlinDoubleToString(850.5)).toBe('850.5');
    expect(kotlinDoubleToString(0.5)).toBe('0.5');
    expect(kotlinDoubleToString(-1.2)).toBe('-1.2');
  });

  it('returns "null" for null/undefined/non-finite', () => {
    expect(kotlinDoubleToString(null)).toBe('null');
    expect(kotlinDoubleToString(undefined)).toBe('null');
    expect(kotlinDoubleToString(NaN)).toBe('null');
    expect(kotlinDoubleToString(Infinity)).toBe('null');
  });
});

describe('canonicalSnapshotString (Kotlin parity)', () => {
  it.each(PARITY_VECTORS)(
    'produces the Kotlin-anchored canonical string for $label',
    ({ input }) => {
      const snap = vectorToSnapshot(input);
      const actual = canonicalSnapshotString(snap);
      // The first 7 fields are joined with "|". We rebuild them with the
      // exact Kotlin format and confirm the TS side agrees.
      const expected = [
        input.vehicleId,
        input.sessionId ?? '',
        input.createdAtMs.toString(),
        input.dtcsActive.slice().sort().join(','),
        input.dtcsPending.slice().sort().join(','),
        input.dtcsPermanent.slice().sort().join(','),
        Object.keys(input.freezeFramePidValues)
          .sort()
          .map(
            (k) =>
              `${k}=${kotlinDoubleToString(input.freezeFramePidValues[k])}`,
          )
          .join(','),
        Object.keys(input.readiness)
          .sort()
          .map((k) => `${k}=${input.readiness[k]}`)
          .join(','),
        kotlinDoubleToString(input.ecuVoltage),
        kotlinDoubleToString(input.rpm),
        kotlinDoubleToString(input.coolantTempC),
        kotlinDoubleToString(input.speedKph),
        kotlinDoubleToString(input.engineLoadPct),
        kotlinDoubleToString(input.fuelTrimStft),
        kotlinDoubleToString(input.fuelTrimLtft),
      ].join('|');
      expect(actual).toBe(expected);
    },
  );
});

describe('hashSnapshot (Kotlin parity)', () => {
  it.each(PARITY_VECTORS)(
    'produces the Kotlin-anchored SHA-256 for $label',
    async ({ input, expectedHash }) => {
      const snap = vectorToSnapshot(input);
      const actual = await hashSnapshot(snap);
      expect(actual).toBe(expectedHash);
    },
  );

  it('deterministic across re-orderings of DTCs / readiness / freeze frame', async () => {
    const v = PARITY_VECTORS[1].input;
    const a = await hashSnapshot(
      vectorToSnapshot({
        ...v,
        dtcsActive: ['P0230', 'P1709'],
        readiness: { Misfire: true, Fuel: true },
        freezeFramePidValues: { RPM: 850, ECT: 88 },
      }),
    );
    const b = await hashSnapshot(
      vectorToSnapshot({
        ...v,
        dtcsActive: ['P1709', 'P0230'],
        readiness: { Fuel: true, Misfire: true },
        freezeFramePidValues: { ECT: 88, RPM: 850 },
      }),
    );
    expect(a).toBe(b);
  });
});
