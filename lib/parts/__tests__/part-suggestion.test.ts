/**
 * Tests for lib/parts/part-suggestion.ts
 *
 * The headline test is the spec's acceptance scenario for P0230: when the
 * user requests a fuel pump for P0230, the engine MUST surface the relay,
 * fuse, and harness options BEFORE the pump, and tag the pump as a
 * riskPart so the UI shows the warning verbatim.
 */

import { describe, expect, it } from 'vitest';

import {
  filterRiskParts,
  suggestParts,
} from '../part-suggestion';

describe('suggestParts — P0230 (the headline scenario)', () => {
  const suggestions = suggestParts({
    source: 'DTC',
    dtcCodes: ['P0230'],
  });

  it('returns at least 4 distinct suggestions for P0230', () => {
    expect(suggestions.length).toBeGreaterThanOrEqual(4);
  });

  it('lists the relay BEFORE the fuel pump', () => {
    const relayIndex = suggestions.findIndex((s) =>
      s.partName.toLowerCase().includes('relé de bomba'),
    );
    const pumpIndex = suggestions.findIndex((s) => s.riskPart);
    expect(relayIndex).toBeGreaterThanOrEqual(0);
    expect(pumpIndex).toBeGreaterThanOrEqual(0);
    expect(relayIndex).toBeLessThan(pumpIndex);
  });

  it('puts the fuel pump LAST and flags it as the risk part', () => {
    const last = suggestions[suggestions.length - 1];
    expect(last.riskPart).toBe(true);
    expect(last.disclaimer).toBeDefined();
    expect(last.disclaimer!.toLowerCase()).toContain('manómetro');
  });

  it('emits the verbatim pressure-with-manometer warning on the fuel pump', () => {
    const pump = suggestions.find((s) => s.riskPart);
    expect(pump).toBeDefined();
    expect(pump?.disclaimer).toContain('presión');
    expect(pump?.disclaimer?.toLowerCase()).toContain('manómetro');
  });
});

describe('suggestParts — other DTCs', () => {
  it('returns prioritized list for P0420', () => {
    const suggestions = suggestParts({
      source: 'DTC',
      dtcCodes: ['P0420'],
    });
    expect(suggestions.length).toBeGreaterThanOrEqual(2);
    // The cheap O2 sensor comes first, the catalytic converter is riskPart last.
    const last = suggestions[suggestions.length - 1];
    expect(last.riskPart).toBe(true);
  });

  it('returns prioritized list for P0300', () => {
    const suggestions = suggestParts({
      source: 'DTC',
      dtcCodes: ['P0300'],
    });
    const first = suggestions[0];
    expect(first.partName.toLowerCase()).toContain('bujía');
  });

  it('emits a generic placeholder when the DTC is unknown', () => {
    const suggestions = suggestParts({
      source: 'DTC',
      dtcCodes: ['P9999'],
    });
    expect(suggestions.length).toBe(1);
    expect(suggestions[0].partName.toLowerCase()).toContain('diagnóstico');
  });
});

describe('suggestParts — 3D component input', () => {
  it('maps fuel_pump_relay correctly', () => {
    const suggestions = suggestParts({
      source: '3D_COMPONENT',
      componentSlug: 'fuel_pump_relay',
    });
    expect(suggestions).toHaveLength(1);
    expect(suggestions[0].partName).toContain('Relé');
  });

  it('flags fuel_pump_assembly as a risk part', () => {
    const suggestions = suggestParts({
      source: '3D_COMPONENT',
      componentSlug: 'fuel_pump_assembly',
    });
    expect(suggestions[0].riskPart).toBe(true);
  });

  it('flags abs_module as a risk part and emits the safety disclaimer', () => {
    const suggestions = suggestParts({
      source: '3D_COMPONENT',
      componentSlug: 'abs_module',
    });
    expect(suggestions[0].riskPart).toBeFalsy();
    expect(suggestions[0].disclaimer?.toLowerCase()).toContain('técnico');
  });
});

describe('suggestParts — manual / work order', () => {
  it('passes through work-order hints verbatim', () => {
    const suggestions = suggestParts({
      source: 'WORK_ORDER',
      workOrderHint: 'Pastilla freno trasero',
    });
    expect(suggestions).toHaveLength(1);
    expect(suggestions[0].partName).toBe('Pastilla freno trasero');
  });

  it('returns nothing for MANUAL without a hint', () => {
    const suggestions = suggestParts({ source: 'MAINTENANCE_ALERT' });
    expect(suggestions).toHaveLength(0);
  });
});

describe('filterRiskParts', () => {
  it('keeps fuel pump suggestion because it is riskPart', () => {
    const suggestions = suggestParts({
      source: 'DTC',
      dtcCodes: ['P0230'],
    });
    const risk = filterRiskParts(suggestions);
    expect(risk.length).toBeGreaterThanOrEqual(1);
    expect(risk.find((r) => r.partName.toLowerCase().includes('bomba'))).toBeDefined();
  });

  it('keeps any safety-critical part even if not riskPart-tagged', () => {
    const result = filterRiskParts([
      {
        partName: 'Pastilla de freno delantero',
        category: 'BRAKES',
        position: 'NOT_APPLICABLE',
        priority: 1,
        rationale: 'manual',
      },
      {
        partName: 'Filtro de aire',
        category: 'ENGINE',
        position: 'NOT_APPLICABLE',
        priority: 1,
        rationale: 'manual',
      },
    ]);
    expect(result).toHaveLength(1);
    expect(result[0].partName).toContain('Pastilla');
  });
});
