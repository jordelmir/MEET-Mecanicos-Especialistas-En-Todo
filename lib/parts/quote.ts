/**
 * Supplier Quote utilities — pure, no I/O.
 *
 * Wraps a SupplierQuote form submission in validated, idempotent helpers.
 * The form has too many fields to keep validations inline; centralizing here
 * also makes the rules unit-testable.
 *
 *   - buildQuoteFromForm:   form input -> draft quote (with sensible defaults)
 *   - validateQuote:        quote  -> ValidationResult (block / warn / ok)
 *   - expiresAtFromNow:     helper for setting `expiresAt`
 *   - tagQuote:             assign a sales-history tag (COLD / WARM / TRUSTED)
 *   - isRiskPartForQuote:   true when the part alone warrants a warn ribbon
 */

import {
  PartCondition,
  PartAvailability,
  CompatibilityConfidence,
  isCriticalSafetyPart,
  isValidVin,
} from './types';

/* -------------------------------------------------------------------------- */
/*                                   Types                                    */
/* -------------------------------------------------------------------------- */

export const SUPPLIER_QUOTE_CONDITIONS: PartCondition[] = [
  'NEW_OEM',
  'NEW_AFTERMARKET',
  'USED',
  'REFURBISHED',
  'REBUILT',
  'UNKNOWN',
];
export const SUPPLIER_QUOTE_AVAILABILITIES: PartAvailability[] = [
  'IN_STOCK',
  'SAME_DAY',
  'NEXT_DAY',
  'IMPORT_REQUIRED',
  'UNKNOWN',
];
export const SUPPLIER_QUOTE_COMPAT: CompatibilityConfidence[] = [
  'EXACT',
  'HIGH',
  'MEDIUM',
  'LOW',
  'UNKNOWN',
];

export interface SupplierQuoteFormInput {
  partName: string;
  brand: string;
  partNumber: string;
  oemNumber?: string;
  condition: PartCondition;
  availability: PartAvailability;
  price: number;
  currency: string;
  includesDelivery: boolean;
  deliveryFee: number;
  estimatedDeliveryHours: number;
  warrantyDays: number;
  photoUrls: string[];
  compatibilityConfidence: CompatibilityConfidence;
  compatibilityNotes: string;
  expiresInHours: number;
  vehicleVin?: string;
  vehicleBrand?: string;
  vehicleModel?: string;
  vehicleYear?: number;
  vehicleEngine?: string;
}

export interface DraftSupplierQuote {
  partName: string;
  brand: string;
  partNumber: string;
  oemNumber: string | null;
  condition: PartCondition;
  availability: PartAvailability;
  price: number;
  currency: string;
  includesDelivery: boolean;
  deliveryFee: number;
  estimatedDeliveryHours: number;
  warrantyDays: number;
  photoUrls: string[];
  compatibilityConfidence: CompatibilityConfidence;
  compatibilityNotes: string;
  vehicleVin: string | null;
  vehicleBrand: string;
  vehicleModel: string;
  vehicleYear: number | null;
  vehicleEngine: string;
  expiresAt: number; // unix ms (bigint-shaped number)
}

export type ValidationLevel = 'OK' | 'WARN' | 'BLOCK';

export interface ValidationResult {
  level: ValidationLevel;
  errors: string[];
  warnings: string[];
}

export type SalesHistoryTag = 'COLD' | 'WARM' | 'TRUSTED';

/* -------------------------------------------------------------------------- */
/*                                  Helpers                                   */
/* -------------------------------------------------------------------------- */

export function buildQuoteFromForm(
  form: SupplierQuoteFormInput,
): DraftSupplierQuote {
  return {
    partName: form.partName.trim(),
    brand: form.brand.trim(),
    partNumber: form.partNumber.trim(),
    oemNumber: form.oemNumber?.trim() || null,
    condition: form.condition,
    availability: form.availability,
    price: form.price,
    currency: form.currency,
    includesDelivery: form.includesDelivery,
    deliveryFee: form.deliveryFee,
    estimatedDeliveryHours: form.estimatedDeliveryHours,
    warrantyDays: form.warrantyDays,
    photoUrls: form.photoUrls
      .map((u) => u.trim())
      .filter((u) => u.length > 0),
    compatibilityConfidence: form.compatibilityConfidence,
    compatibilityNotes: form.compatibilityNotes.trim(),
    vehicleVin: form.vehicleVin?.trim() || null,
    vehicleBrand: form.vehicleBrand?.trim() || '',
    vehicleModel: form.vehicleModel?.trim() || '',
    vehicleYear: Number.isInteger(form.vehicleYear) ? form.vehicleYear! : null,
    vehicleEngine: form.vehicleEngine?.trim() || '',
    expiresAt: expiresAtFromNow(form.expiresInHours),
  };
}

export function expiresAtFromNow(hoursFromNow: number): number {
  if (!Number.isFinite(hoursFromNow) || hoursFromNow <= 0) {
    // 24h is the conservative default.
    return Date.now() + 24 * 60 * 60 * 1000;
  }
  return Date.now() + hoursFromNow * 60 * 60 * 1000;
}

export function validateQuote(quote: DraftSupplierQuote): ValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  if (quote.partName.length < 3) {
    errors.push('El nombre de la pieza debe tener al menos 3 caracteres.');
  }
  if (quote.price <= 0) {
    errors.push('El precio debe ser mayor que cero.');
  }
  if (quote.price > 1_000_000) {
    warnings.push('El precio es inusualmente alto; verificar con el cliente.');
  }
  if (quote.estimatedDeliveryHours < 0) {
    errors.push('La entrega estimada no puede ser negativa.');
  }
  if (quote.warrantyDays < 0) {
    errors.push('La garantía no puede ser negativa.');
  }
  if (quote.condition === 'USED' || quote.condition === 'REFURBISHED') {
    if (quote.photoUrls.length === 0) {
      errors.push(
        'Para piezas usadas o reacondicionadas se requiere al menos una foto.',
      );
    }
    if (quote.warrantyDays === 0) {
      warnings.push(
        'Pieza usada sin garantía declarada: algunos clientes podrían pedirla.',
      );
    }
  }
  const vinProvided = Boolean(quote.vehicleVin);
  const validVin = isValidVin(quote.vehicleVin);
  if (vinProvided && !validVin) {
    errors.push(
      'El VIN recibido no es válido: debe tener exactamente 17 caracteres y no incluir I, O ni Q.',
    );
  }
  if (quote.compatibilityConfidence === 'EXACT') {
    const hasPartIdentity = Boolean(quote.oemNumber || quote.partNumber);
    const hasVinEvidence = hasPartIdentity && validVin;
    const hasClosedTupleEvidence = Boolean(
      quote.vehicleBrand &&
        quote.vehicleModel &&
        quote.vehicleYear !== null &&
        quote.vehicleYear >= 1886 &&
        quote.vehicleYear <= 2100 &&
        quote.vehicleEngine &&
        quote.oemNumber,
    );

    if (!hasPartIdentity) {
      errors.push(
        'Para una confianza EXACTA se requiere número OEM o número de parte.',
      );
    }
    if (!hasVinEvidence && !hasClosedTupleEvidence) {
      errors.push(
        'EXACT requiere VIN válido + OEM/número de parte, o tupla cerrada ' +
          'marca/modelo/año/motor/OEM.',
      );
    }
    if (quote.compatibilityNotes.trim().length === 0) {
      warnings.push(
        'EXACT sin notas de compatibilidad: por favor documenta la verificación.',
      );
    }
  }

  // Critical-safety-part install warning. We don't block here; we surface it
  // for the UI; the form MUST render an explicit checkbox before submit.
  if (isCriticalSafetyPart(quote.partName)) {
    warnings.push(
      'Pieza crítica: requiere el checkbox de "Instalación por técnico ' +
        'calificado" antes de poder publicar.',
    );
  }

  // Availability-import with no delivery ETA is suspect.
  if (
    quote.availability === 'IMPORT_REQUIRED' &&
    quote.estimatedDeliveryHours < 24 * 7
  ) {
    warnings.push(
      'IMPORT_REQUIRED con menos de 1 semana de entrega: ' +
        'verificar el plazo real con el proveedor de despacho.',
    );
  }

  const level: ValidationLevel = errors.length > 0 ? 'BLOCK' : warnings.length > 0 ? 'WARN' : 'OK';

  return { level, errors, warnings };
}

/**
 * Assign a sales-history tag for the wizard's history ribbon.
 *
 *   TRUSTED -> ratingAvg >= 4.6 AND totalSales >= 50 AND claimRate < 0.05.
 *   WARM    -> ratingAvg >= 4.0 AND totalSales >= 5.
 *   COLD    -> anything else.
 */
export function tagQuote(input: {
  ratingAvg: number;
  totalSales: number;
  claimRate: number;
}): SalesHistoryTag {
  const r = input.ratingAvg ?? 0;
  const sales = input.totalSales ?? 0;
  const claims = input.claimRate ?? 0;

  if (r >= 4.6 && sales >= 50 && claims < 0.05) return 'TRUSTED';
  if (r >= 4.0 && sales >= 5) return 'WARM';
  return 'COLD';
}

/**
 * Whether the quote needs an explicit "install by qualified tech" checkbox
 * before submit. Used to gate the form's primary action button.
 */
export function isRiskPartForQuote(partName: string): boolean {
  return isCriticalSafetyPart(partName);
}
