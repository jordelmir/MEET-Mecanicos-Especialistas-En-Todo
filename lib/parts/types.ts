/**
 * Parts Marketplace — Shared Types (TypeScript → JSON Schema-like)
 *
 * These types are the contract between the Supabase schema defined in
 * `supabase/migrations/20260704000000_parts_marketplace_foundation.sql`
 * and the web/TS surface. They deliberately mirror the SQL column names
 * so we keep one source of truth between DB and client.
 *
 * CRITICAL design rule: NEVER expose an `EXACT` compatibility verdict
 * unless the evidence list shows VIN + OEM match, or a confirmed
 * (mark + model + year + engine + OEM) tuple.
 */

/* -------------------------------------------------------------------------- */
/*                               Enumerations                                */
/* -------------------------------------------------------------------------- */

export const PART_REQUEST_STATUSES = [
  'DRAFT',
  'OPEN',
  'RECEIVING_QUOTES',
  'QUOTE_ACCEPTED',
  'WAITING_PAYMENT',
  'ORDERED',
  'READY_FOR_PICKUP',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLED',
  'DISPUTED',
] as const;
export type PartRequestStatus = typeof PART_REQUEST_STATUSES[number];

export const PART_PREFERENCES = [
  'ANY',
  'OEM',
  'AFTERMARKET',
  'USED',
  'REFURBISHED',
  'PERFORMANCE',
  'BUDGET',
] as const;
export type PartPreference = typeof PART_PREFERENCES[number];

export const PART_POSITIONS = [
  'FRONT_RIGHT',
  'FRONT_LEFT',
  'REAR_RIGHT',
  'REAR_LEFT',
  'CENTER',
  'ENGINE',
  'TRANSMISSION',
  'ELECTRICAL',
  'BODY',
  'INTERIOR',
  'NOT_APPLICABLE',
  'FUSE_BOX',
  'EXHAUST',
] as const;
export type PartPosition = typeof PART_POSITIONS[number];

export const PART_SOURCE_CONTEXTS = [
  'MANUAL',
  'FROM_DTC',
  'FROM_3D_COMPONENT',
  'FROM_MECHANIC_WORK_ORDER',
  'FROM_MAINTENANCE_ALERT',
  'FROM_PREPURCHASE_INSPECTION',
] as const;
export type PartSourceContext = typeof PART_SOURCE_CONTEXTS[number];

export const PART_CONDITIONS = [
  'NEW_OEM',
  'NEW_AFTERMARKET',
  'USED',
  'REFURBISHED',
  'REBUILT',
  'UNKNOWN',
] as const;
export type PartCondition = typeof PART_CONDITIONS[number];

export const PART_AVAILABILITIES = [
  'IN_STOCK',
  'SAME_DAY',
  'NEXT_DAY',
  'IMPORT_REQUIRED',
  'UNKNOWN',
] as const;
export type PartAvailability = typeof PART_AVAILABILITIES[number];

export const QUOTE_STATUSES = [
  'SENT',
  'ACCEPTED',
  'REJECTED',
  'EXPIRED',
  'CANCELLED',
] as const;
export type QuoteStatus = typeof QUOTE_STATUSES[number];

export const VERIFICATION_STATUSES = [
  'UNVERIFIED',
  'PHONE_VERIFIED',
  'BUSINESS_VERIFIED',
  'INVENTORY_VERIFIED',
  'ELITE_SUPPLIER',
  'SUSPENDED',
] as const;
export type VerificationStatus = typeof VERIFICATION_STATUSES[number];

export const COMPATIBILITY_CONFIDENCES = [
  'EXACT',
  'HIGH',
  'MEDIUM',
  'LOW',
  'UNKNOWN',
] as const;
export type CompatibilityConfidence = typeof COMPATIBILITY_CONFIDENCES[number];

/* -------------------------------------------------------------------------- */
/*                                   Inputs                                   */
/* -------------------------------------------------------------------------- */

export interface VehicleFingerprint {
  /** Marca. Free-form ("Hyundai"). */
  brand?: string;
  /** Modelo. Free-form ("Accent Verna"). */
  model?: string;
  /** Año modelo. 4 dígitos. */
  year?: number;
  /** Motorization tag ("1.6 AT", "G4FC", "D4CB", etc). */
  engine?: string;
  /** "AUTOMATIC" | "MANUAL" | free form. */
  transmission?: string;
  /** "GASOLINE" | "DIESEL" | "HYBRID" | free form. */
  fuel?: string;
  /** VIN (17 chars preferred). Decisive for EXACT verdicts. */
  vin?: string;
  /** OEM part number explicitly provided by user. Decisive for EXACT verdicts. */
  oemNumber?: string;
  /** Part number (aftermarket or OEM generic). Useful but not decisive. */
  partNumber?: string;
}

export interface CompatibilityContext {
  vehicle: VehicleFingerprint;
  /** Requested part name, e.g. "Bomba de combustible" or "Relé bomba gasolina". */
  partName: string;
  /** Optional category: ENGINE | ELECTRICAL | BRAKES | BODY | … */
  category?: string;
  /** Where on the vehicle: FRONT_RIGHT, FUSE_BOX, … */
  position?: PartPosition;
  /** DTCs the user knows about. We use them to bias warnings (P0230 case). */
  dtcCodes?: string[];
  /** 3D component slug if the part was selected from the engine viewer. */
  from3DComponentSlug?: string;
  /** Photo evidence URLs (old part, connector, fuse box). */
  photoUrls?: string[];
}

/* -------------------------------------------------------------------------- */
/*                                  Outputs                                   */
/* -------------------------------------------------------------------------- */

export interface CompatibilityWarning {
  /** Stable code so the UI can act on it (e.g. collapse / pin / color). */
  code:
    | 'DTC_P0230_PUMP_REQUIRES_CONFIRMATION'
    | 'NO_VIN'
    | 'NO_OEM'
    | 'CRITICAL_SAFETY_PART'
    | 'FUEL_SYSTEM_PART'
    | 'BRAKING_PART'
    | 'AIRBAG_PART'
    | 'HIGH_VOLTAGE_PART'
    | 'NO_PHOTO_EVIDENCE'
    | 'PART_NAME_AMBIGUOUS';
  /** Human readable, friendly, never says "guaranteed". */
  message: string;
  /** Severity we expose to UI. */
  severity: 'INFO' | 'WARN' | 'BLOCK';
}

export interface CompatibilityResult {
  confidence: CompatibilityConfidence;
  /** Always non-empty when we have at least some context. */
  warnings: CompatibilityWarning[];
  /** Things the user must confirm before we upgrade to a higher confidence. */
  requiredConfirmations: string[];
  /** Cross-reference numbers (OEM alternates) the user can verify externally. */
  crossReferenceNumbers: string[];
  /** Questions we recommend asking the repuestera before purchase. */
  recommendedQuestions: string[];
  /** Free-form rationale, for debugging or audit. */
  rationale: string[];
}

/* -------------------------------------------------------------------------- */
/*                          Critical-part taxonomy                            */
/* -------------------------------------------------------------------------- */

const SAFETY_KEYWORDS = [
  // braking
  'brake', 'freno', 'pastilla', 'pad', 'caliper', 'caliper', 'disco', 'rotor',
  'drum', 'tambor', 'abs', 'cilindro maestro', 'master cylinder',
  // steering / suspension
  'steering', 'direccion', 'suspension', 'suspension', 'shock', 'amortiguador',
  'strut', 'ball joint', 'rotula', 'tie rod', 'terminal',
  // fuel
  'fuel pump', 'bomba combustible', 'bomba de combustible', 'inyector', 'injector',
  'fuel rail', 'fuel line', 'manguera combustible',
  // airbag
  'airbag', 'bolsa de aire', 'pretensioner', 'pretensor',
  // high voltage (hybrids / EV)
  'hybrid battery', 'high voltage', 'alto voltaje', 'hv cable',
  'alta tension', 'alta tensión', 'bateria alta', 'bateria hibrida',
];

export function isCriticalSafetyPart(name: string): boolean {
  const lower = name.toLowerCase();
  return SAFETY_KEYWORDS.some((kw) => lower.includes(kw));
}
