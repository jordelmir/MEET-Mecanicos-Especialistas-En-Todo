/**
 * Tests for lib/parts/compatibility.ts
 *
 * The user-facing test scenario from Jor (PR-1 acceptance):
 *
 *   "Desde P0230, pedir 'bomba de combustible' y validar que la app muestre
 *    advertencia: 'No reemplazar bomba sin confirmar alimentación, tierra,
 *    relé/fusible y presión con manómetro'."
 *
 * Plus the surrounding rules from the spec (no EXACT without VIN/OEM, etc.).
 */

import { describe, expect, it } from 'vitest';

import {
  CompatibilityContext,
  evaluateCompatibility,
  isCriticalSafetyPart,
  isValidVin,
} from '../index';

const baseVehicle = {
  brand: 'Hyundai',
  model: 'Accent Verna',
  year: 2005,
  engine: '1.6 AT',
  transmission: 'AUTOMATIC',
  fuel: 'GASOLINE',
};

describe('evaluateCompatibility — base rules', () => {
  it('returns UNKNOWN when there is no vehicle context and only a generic name', () => {
    const result = evaluateCompatibility({
      vehicle: {},
      partName: 'repuesto',
    });
    expect(result.confidence).toBe('UNKNOWN');
    expect(result.requiredConfirmations.length).toBeGreaterThan(0);
  });

  it('emits the P0230 fuel-pump BLOCK and clamps EXACT down when both apply', () => {
    // P0230 + fuel pump always emits a BLOCK. We still have a "complete"
    // vehicle profile. The block must downgrade.
    const ctx: CompatibilityContext = {
      vehicle: { ...baseVehicle, vin: 'KMHCN46C18U123456', oemNumber: '31110-25000' },
      partName: 'Bomba de combustible',
      dtcCodes: ['P0230'],
    };
    const result = evaluateCompatibility(ctx);
    expect(
      result.warnings.some((w) => w.code === 'DTC_P0230_PUMP_REQUIRES_CONFIRMATION'),
    ).toBe(true);
    expect(result.confidence).not.toBe('EXACT');
  });

  it('promotes to EXACT only with VIN + OEM, or a closed (brand+model+year+engine+OEM) tuple', () => {
    // VIN + OEM => EXACT, but only for non-fuel-pump parts (no BLOCK).
    expect(
      evaluateCompatibility({
        vehicle: { ...baseVehicle, vin: 'KMHCN46C18U123456', oemNumber: '27301-2B100' },
        partName: 'Bobina de encendido',
      }).confidence,
    ).toBe('EXACT');

    // Closed tuple without VIN => EXACT.
    expect(
      evaluateCompatibility({
        vehicle: { ...baseVehicle, oemNumber: '27301-2B100' },
        partName: 'Bobina de encendido',
      }).confidence,
    ).toBe('EXACT');

    // Brand+model+year+engine but no OEM => MEDIUM, NOT EXACT, NOT HIGH.
    // The spec is strict: HIGH requires OEM.
    expect(
      evaluateCompatibility({
        vehicle: baseVehicle,
        partName: 'Bobina de encendido',
      }).confidence,
    ).toBe('MEDIUM');
  });

  it('rejects partial VIN evidence instead of promoting it to EXACT', () => {
    const result = evaluateCompatibility({
      vehicle: { vin: 'KMHCN46C18U', oemNumber: '27301-2B100' },
      partName: 'Bobina de encendido',
    });

    expect(result.confidence).not.toBe('EXACT');
    expect(result.warnings.some((warning) => warning.code === 'INVALID_VIN')).toBe(true);
  });

  it('accepts only structurally valid 17-character VINs', () => {
    expect(isValidVin('KMHCN46C18U123456')).toBe(true);
    expect(isValidVin('KMHCN46C18O123456')).toBe(false);
    expect(isValidVin('KMHCN46C18U12345')).toBe(false);
  });

  it('produces MEDIUM for brand+model+year (no OEM) and adds the safety warning for brakes', () => {
    const result = evaluateCompatibility({
      vehicle: baseVehicle,
      partName: 'Pastilla de freno delantero',
    });
    expect(result.confidence).toBe('MEDIUM');
    // Brake = safety part => emits the safety warning regardless of tier.
    expect(result.warnings.some((w) => w.code === 'CRITICAL_SAFETY_PART')).toBe(
      true,
    );
  });

  it('produces MEDIUM for brand+model+year (no engine)', () => {
    expect(
      evaluateCompatibility({
        vehicle: { brand: 'Hyundai', model: 'Accent Verna', year: 2005 },
        partName: 'Bobina de encendido',
      }).confidence,
    ).toBe('MEDIUM');
  });

  it('produces LOW for brand+model alone (no year, no engine)', () => {
    expect(
      evaluateCompatibility({
        vehicle: { brand: 'Hyundai', model: 'Accent Verna' },
        partName: 'Bobina de encendido',
      }).confidence,
    ).toBe('LOW');
  });

  it('produces LOW when only the part name and position are known', () => {
    expect(
      evaluateCompatibility({
        vehicle: {},
        partName: 'Filtro de aire',
        position: 'ENGINE',
      }).confidence,
    ).toBe('LOW');
  });
});

describe('evaluateCompatibility — Jor acceptance scenario (P0230 + fuel pump)', () => {
  const ctx: CompatibilityContext = {
    vehicle: baseVehicle,
    partName: 'Bomba de combustible',
    dtcCodes: ['P0230'],
  };
  const result = evaluateCompatibility(ctx);

  it('does NOT mark the verdict as EXACT', () => {
    expect(result.confidence).not.toBe('EXACT');
  });

  it('includes the exact anti-fraud warning the spec asks for', () => {
    expect(
      result.warnings.some((w) =>
        w.message.includes(
          'No reemplazar bomba de combustible sin confirmar antes',
        ),
      ),
    ).toBe(true);
    expect(result.warnings.some((w) => w.message.includes('alimentación'))).toBe(
      true,
    );
    expect(result.warnings.some((w) => w.message.includes('presión'))).toBe(true);
    expect(result.warnings.some((w) => w.message.includes('relé'))).toBe(true);
  });

  it('still asks for missing VIN and OEM', () => {
    expect(result.warnings.some((w) => w.code === 'NO_VIN')).toBe(true);
    expect(result.warnings.some((w) => w.code === 'NO_OEM')).toBe(true);
  });

  it('recommends verification questions for the supplier', () => {
    expect(result.recommendedQuestions.length).toBeGreaterThan(0);
    expect(
      result.recommendedQuestions.some((q) => q.toLowerCase().includes('relé')),
    ).toBe(true);
  });
});

describe('evaluateCompatibility — DTC linkage does NOT push the part', () => {
  it('emits the safety warning even for fuel pump without P0230 in context', () => {
    const result = evaluateCompatibility({
      vehicle: baseVehicle,
      partName: 'Bomba de combustible',
    });
    expect(result.warnings.some((w) => w.code === 'CRITICAL_SAFETY_PART')).toBe(
      true,
    );
    // Without P0230 in DTC codes, we don't get the BLOCK warning.
    expect(
      result.warnings.find((w) => w.code === 'DTC_P0230_PUMP_REQUIRES_CONFIRMATION'),
    ).toBeUndefined();
  });

  it('does NOT trigger the BLOCK warning when the user requests a relay for P0230 instead of the pump', () => {
    const ctx: CompatibilityContext = {
      vehicle: baseVehicle,
      partName: 'Relé de bomba de gasolina',
      position: 'FUSE_BOX',
      dtcCodes: ['P0230'],
    };
    const result = evaluateCompatibility(ctx);
    expect(
      result.warnings.find(
        (w) => w.code === 'DTC_P0230_PUMP_REQUIRES_CONFIRMATION',
      ),
    ).toBeUndefined();
  });
});

describe('isCriticalSafetyPart', () => {
  it.each([
    ['Bomba de combustible', true],
    ['Pastilla de freno delantero', true],
    ['Rotula suspension', true],
    ['Bolsa de aire conductor', true],
    ['Bateria alta tensión', true],
    ['Filtro de aire', false],
    ['Espejo retrovisor', false],
  ])('classifies "%s" as %s', (name, expected) => {
    expect(isCriticalSafetyPart(name)).toBe(expected);
  });
});
