/**
 * Tests for lib/parts/quote.ts
 *
 * The mission: enforce the spec's anti-fraud rules so they don't regress.
 *   - USED / REFURBISHED requires photo evidence.
 *   - EXACT compat requires OEM/part number + notes.
 *   - Safety-critical parts gate publish behind a confirmation.
 *   - IMPORT_REQUIRED with too-short ETA is suspicious.
 *
 * If a future PR relaxes any of these, this test will demand a deliberate
 * change rather than a silent regression.
 */

import { describe, expect, it } from 'vitest';

import {
  buildQuoteFromForm,
  expiresAtFromNow,
  isRiskPartForQuote,
  tagQuote,
  validateQuote,
  SupplierQuoteFormInput,
} from '../quote';

const baseForm: SupplierQuoteFormInput = {
  partName: 'Bobina de encendido',
  brand: 'NGK',
  partNumber: 'U5156',
  oemNumber: '27301-2B100',
  condition: 'NEW_OEM',
  availability: 'IN_STOCK',
  price: 85,
  currency: 'CRC',
  includesDelivery: false,
  deliveryFee: 0,
  estimatedDeliveryHours: 24,
  warrantyDays: 90,
  photoUrls: [],
  compatibilityConfidence: 'EXACT',
  compatibilityNotes: 'Verificado contra Hyundai Accent Verna 2005 1.6 G4FC',
  expiresInHours: 48,
};

describe('buildQuoteFromForm', () => {
  it('trims whitespace and keeps OEM only when present', () => {
    const built = buildQuoteFromForm({
      ...baseForm,
      oemNumber: '   ',
      partName: '   Bobina de encendido   ',
    });
    expect(built.partName).toBe('Bobina de encendido');
    expect(built.oemNumber).toBeNull();
  });

  it('filters out blank photo URLs', () => {
    const built = buildQuoteFromForm({
      ...baseForm,
      photoUrls: ['https://example.com/a.jpg', '', '   ', 'https://example.com/b.jpg'],
    });
    expect(built.photoUrls).toEqual([
      'https://example.com/a.jpg',
      'https://example.com/b.jpg',
    ]);
  });

  it('builds an expiresAt that is roughly `expiresInHours` from now', () => {
    const before = Date.now();
    const built = buildQuoteFromForm(baseForm);
    const expectedMs = baseForm.expiresInHours * 60 * 60 * 1000;
    expect(built.expiresAt - before).toBeGreaterThanOrEqual(expectedMs - 1000);
    expect(built.expiresAt - before).toBeLessThanOrEqual(expectedMs + 1000);
  });
});

describe('expiresAtFromNow', () => {
  it('defaults to 24h when given a non-positive value', () => {
    const before = Date.now();
    const at = expiresAtFromNow(0);
    expect(at - before).toBeGreaterThanOrEqual(24 * 60 * 60 * 1000 - 100);
    expect(at - before).toBeLessThanOrEqual(24 * 60 * 60 * 1000 + 100);
  });

  it('defaults to 24h when given NaN', () => {
    const at = expiresAtFromNow(Number.NaN);
    expect(at - Date.now()).toBeGreaterThan(0);
  });
});

describe('validateQuote — anti-fraud rules', () => {
  it('returns OK on a clean, well-documented OEM quote', () => {
    expect(validateQuote(buildQuoteFromForm(baseForm)).level).toBe('OK');
  });

  it('BLOCKS USED part without photos', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        condition: 'USED',
        price: 30,
        photoUrls: [],
      }),
    );
    expect(v.level).toBe('BLOCK');
    expect(v.errors.join(' ')).toContain('foto');
  });

  it('BLOCKS REFURBISHED part without photos', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        condition: 'REFURBISHED',
        photoUrls: [],
      }),
    );
    expect(v.level).toBe('BLOCK');
  });

  it('BLOCKS an EXACT-compat quote that has no OEM and no part number', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        oemNumber: '',
        partNumber: '',
        compatibilityConfidence: 'EXACT',
      }),
    );
    expect(v.level).toBe('BLOCK');
    expect(v.errors.join(' ')).toContain('EXACTA');
  });

  it('WARNS an EXACT-compat quote that has no notes', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        compatibilityNotes: '',
      }),
    );
    expect(v.warnings.join(' ')).toContain('notas');
  });

  it('BLOCKS a 0-price quote', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        price: 0,
      }),
    );
    expect(v.level).toBe('BLOCK');
  });

  it('BLOCKS a negative-warranty quote', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        warrantyDays: -1,
      }),
    );
    expect(v.level).toBe('BLOCK');
  });

  it('WARNS safety-critical parts with the install-by-qualified-tech notice', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        partName: 'Bomba de combustible',
        condition: 'NEW_OEM',
      }),
    );
    expect(v.warnings.join(' ').toLowerCase()).toContain('técnico');
  });

  it('WARNS USED without warranty days', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        condition: 'USED',
        warrantyDays: 0,
        photoUrls: ['https://example.com/used.jpg'],
      }),
    );
    expect(v.warnings.join(' ')).toContain('garantía');
  });

  it('WARNS IMPORT_REQUIRED with suspiciously short ETA (< 7 days)', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        availability: 'IMPORT_REQUIRED',
        estimatedDeliveryHours: 48,
      }),
    );
    expect(v.warnings.join(' ')).toContain('IMPORT_REQUIRED');
  });

  it('does NOT warn IMPORT_REQUIRED when the ETA is plausibly long', () => {
    const v = validateQuote(
      buildQuoteFromForm({
        ...baseForm,
        availability: 'IMPORT_REQUIRED',
        estimatedDeliveryHours: 24 * 14,
      }),
    );
    expect(v.warnings.join(' ')).not.toContain('IMPORT_REQUIRED');
  });

  it('WARNS the form on excessive price', () => {
    const v = validateQuote(
      buildQuoteFromForm({ ...baseForm, price: 5_000_000 }),
    );
    expect(v.warnings.join(' ')).toContain('precio');
  });
});

describe('tagQuote', () => {
  it('returns TRUSTED for high-rating high-volume low-claim suppliers', () => {
    expect(tagQuote({ ratingAvg: 4.8, totalSales: 100, claimRate: 0.01 })).toBe(
      'TRUSTED',
    );
  });

  it('returns WARM for medium ratings and modest volume', () => {
    expect(tagQuote({ ratingAvg: 4.2, totalSales: 8, claimRate: 0.2 })).toBe(
      'WARM',
    );
  });

  it('returns COLD for everyone else', () => {
    expect(tagQuote({ ratingAvg: 3.0, totalSales: 100, claimRate: 0.1 })).toBe(
      'COLD',
    );
    expect(tagQuote({ ratingAvg: 5.0, totalSales: 0, claimRate: 0 })).toBe(
      'COLD',
    );
  });
});

describe('isRiskPartForQuote', () => {
  it.each([
    ['Bomba de combustible', true],
    ['Pastilla de freno delantero', true],
    ['Bolsa de aire', true],
    ['Filtro de aire', false],
  ])('classifies "%s" as %s', (name, expected) => {
    expect(isRiskPartForQuote(name)).toBe(expected);
  });
});
