/**
 * Part Suggestion Engine — pure, no I/O.
 *
 * Given a DTC code (or a 3D component slug, or a mechanic work-order context),
 * returns an ordered list of part suggestions. The list IS the visible side of
 * the "DTC -> diagnosis -> mechanic -> compatible part" chain.
 *
 * The ordering is intentional and explicit. For P0230 specifically, the engine
 * must prioritize circuit verification before the fuel pump and keep the pump
 * last with an explicit warning. Position never means a part is confirmed bad.
 *
 * Later: we will feed this from the Knowledge Pack (PR-4); for now the static
 * map below covers the initial OBD-II rules supported by this engine.
 */

import {
  isCriticalSafetyPart,
  PartPosition,
} from './types';

export type SuggestionSource =
  | 'DTC'
  | '3D_COMPONENT'
  | 'WORK_ORDER'
  | 'MAINTENANCE_ALERT'
  | 'PREPURCHASE';

export type PartSuggestionEvidenceState =
  | 'INFORMATIONAL'
  | 'REQUIRES_TESTS'
  | 'REPLACEMENT_CONFIRMED'
  | 'PURCHASE_VERIFIED';

export interface PartSuggestion {
  partName: string;
  category: string;
  position: PartPosition;
  priority: number; // 1 = first to look at
  rationale: string;
  /** Stated without ever saying "guaranteed". */
  disclaimer?: string;
  /** True if this is the install risk-high part the user usually jumps to. */
  riskPart?: boolean;
  /** Canonical graph identity. Names and aliases never open a request. */
  canonicalKey?: string;
  evidenceState?: PartSuggestionEvidenceState;
  requestAllowed?: boolean;
  missingEvidence?: string[];
}

export interface PartEvidenceGateContract {
  componentCanonicalKey?: string | null;
  replacementAllowed: boolean;
  purchaseAllowed: boolean;
  purchaseCompatibility: 'EXACT' | 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
  requiredTests: string[];
  missingEvidence: string[];
  missingRequirements: string[];
}

export interface PartSuggestionInput {
  source: SuggestionSource;
  dtcCodes?: string[];
  /** Slug of a 3D component ("fuel_pump_assembly", "abs_module", ...). */
  componentSlug?: string;
  /** When from a mechanic work order: pre-typed part name. */
  workOrderHint?: string;
}

/* -------------------------------------------------------------------------- */
/*                          DTC -> suggestion matrix                         */
/* -------------------------------------------------------------------------- */

const DTC_TO_SUGGESTIONS: Record<string, PartSuggestion[]> = {
  P0230: [
    {
      partName: 'Relé de bomba de combustible',
      category: 'ELECTRICAL',
      position: 'FUSE_BOX',
      priority: 1,
      rationale:
        'Verificar el relé y su zócalo antes de atribuir P0230 a la bomba.',
      canonicalKey: 'fuel_pump_relay',
    },
    {
      partName: 'Fusible circuito bomba',
      category: 'ELECTRICAL',
      position: 'FUSE_BOX',
      priority: 2,
      rationale:
        'Verificar continuidad del fusible y descartar un corto aguas abajo antes de reemplazarlo.',
      canonicalKey: 'fuel_pump_fuse',
    },
    {
      partName: 'Arnés eléctrico / terminales de bomba',
      category: 'ELECTRICAL',
      position: 'ENGINE',
      priority: 3,
      rationale:
        'Inspeccionar conector y arnés; documentar alimentación, tierra y caída de voltaje bajo carga.',
      canonicalKey: 'fuel_pump_harness',
    },
    {
      partName: 'Bomba de combustible',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 99,
      rationale:
        'Considerar reemplazo sólo después de verificar batería, fusible, relé, ' +
        'arnés, conector, alimentación, tierra, presión y corriente.',
      disclaimer:
        'No reemplazar la bomba sin confirmar antes: alimentación, tierra, ' +
        'relé/fusible y presión con manómetro.',
      riskPart: true,
      canonicalKey: 'fuel_pump',
    },
  ],

  P0420: [
    {
      partName: 'Sensor de oxígeno aguas abajo',
      category: 'EXHAUST',
      position: 'EXHAUST',
      priority: 1,
      rationale:
        'Evaluar señal, calentador y cableado del sensor. No sustituirlo sólo por el DTC.',
    },
    {
      partName: 'Junta de escape',
      category: 'EXHAUST',
      position: 'EXHAUST',
      priority: 2,
      rationale:
        'Inspeccionar fugas de escape antes del catalizador y confirmar con una prueba física.',
    },
    {
      partName: 'Catalizador',
      category: 'EXHAUST',
      position: 'CENTER',
      priority: 99,
      rationale:
        'Considerar reemplazo sólo tras descartar fugas, mezcla incorrecta, fallos de encendido y sensores.',
      disclaimer: 'Pieza de alto costo; confirmar antes de reemplazar.',
      riskPart: true,
    },
  ],

  P0300: [
    {
      partName: 'Bujía',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 1,
      rationale:
        'Inspeccionar estado, luz y patrón de desgaste; P0300 no confirma una bujía defectuosa.',
    },
    {
      partName: 'Bobina de encendido',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 2,
      rationale:
        'Confirmar la bobina mediante intercambio controlado, señal o prueba equivalente antes de reemplazar.',
    },
    {
      partName: 'Inyector',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 3,
      rationale:
        'Confirmar balance, control eléctrico y estanqueidad del inyector antes de reemplazar.',
    },
  ],

  P0171: [
    {
      partName: 'Manguera de vacío',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 1,
      rationale:
        'Buscar entrada de aire no medida mediante inspección y prueba de humo cuando corresponda.',
    },
    {
      partName: 'Ducto o junta de admisión',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 2,
      rationale:
        'Inspeccionar grietas, uniones y sellos; no reemplazar sin localizar la fuga.',
    },
    {
      partName: 'Sensor de carga de motor (MAF o MAP según equipamiento)',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 3,
      rationale:
        'Confirmar qué sensor equipa el vehículo y contrastar su señal antes de intervenir.',
    },
  ],
};

/* -------------------------------------------------------------------------- */
/*                        3D component -> suggestions                         */
/* -------------------------------------------------------------------------- */

const COMPONENT_TO_SUGGESTION: Record<string, PartSuggestion> = {
  fuel_pump_relay: {
    partName: 'Relé de bomba de combustible',
    category: 'ELECTRICAL',
    position: 'FUSE_BOX',
    priority: 1,
    rationale: 'Selected from the 3D engine viewer.',
    canonicalKey: 'fuel_pump_relay',
  },
  fuel_pump_assembly: {
    partName: 'Bomba de combustible',
    category: 'ENGINE',
    position: 'ENGINE',
    priority: 99,
    rationale: 'Selected from the 3D engine viewer.',
    disclaimer:
      'Antes de ordenar, verifica alimentación, tierra, relé y presión con manómetro.',
    riskPart: true,
    canonicalKey: 'fuel_pump',
  },
  abs_module: {
    partName: 'Módulo ABS',
    category: 'BRAKES',
    position: 'NOT_APPLICABLE',
    priority: 1,
    rationale: 'Selected from the 3D chassis viewer.',
    disclaimer: 'Pieza safety-critical: instalación por técnico calificado.',
    canonicalKey: 'abs_module',
  },
};

/* -------------------------------------------------------------------------- */
/*                                  Public API                                */
/* -------------------------------------------------------------------------- */

export function suggestParts(input: PartSuggestionInput): PartSuggestion[] {
  const out: PartSuggestion[] = [];

  if (input.source === 'DTC' && input.dtcCodes) {
    for (const code of input.dtcCodes) {
      const upper = code.toUpperCase().trim();
      const entries = DTC_TO_SUGGESTIONS[upper];
      if (entries) {
        out.push(...entries);
      } else {
        // Unknown DTC: surface a generic diagnostic suggestion.
        out.push({
          partName: `Diagnóstico de ${upper}`,
          category: 'ENGINE',
          position: 'NOT_APPLICABLE',
          priority: 50,
          rationale:
            'DTC no tenemos un mapa específico en esta versión. La app ' +
            'mostrará las piezas críticas de seguridad como advertencia.',
        });
      }
    }
  }

  if (input.source === '3D_COMPONENT' && input.componentSlug) {
    const hit = COMPONENT_TO_SUGGESTION[input.componentSlug];
    if (hit) {
      out.push(hit);
    }
  }

  if (input.source === 'WORK_ORDER' && input.workOrderHint) {
    out.push({
      partName: input.workOrderHint,
      category: 'ENGINE',
      position: 'NOT_APPLICABLE',
      priority: 1,
      rationale: 'Pre-llenado por la orden de trabajo del mecánico.',
    });
  }

  // Stable, priority-based sort. riskPart is ALWAYS last regardless of number.
  return out
    .slice()
    .sort((a, b) => {
      const ap = a.riskPart ? 1000 : a.priority;
      const bp = b.riskPart ? 1000 : b.priority;
      return ap - bp;
    })
    .map((suggestion) => ({
      ...suggestion,
      evidenceState: suggestion.evidenceState ?? 'INFORMATIONAL',
      requestAllowed: suggestion.requestAllowed ?? false,
      missingEvidence: suggestion.missingEvidence ?? [],
    }));
}

/**
 * Applies the same fail-closed canonical evidence gate used by Android.
 * Legacy DTC/3D mappings remain informational until this function receives an EXACT graph gate.
 */
export function applyPartEvidenceGate(
  suggestions: PartSuggestion[],
  gate: PartEvidenceGateContract,
): PartSuggestion[] {
  const missing = Array.from(new Set([
    ...gate.missingEvidence,
    ...gate.missingRequirements,
    ...(gate.replacementAllowed ? [] : gate.requiredTests),
  ])).sort();

  return suggestions.map((suggestion) => {
    const isGateTarget =
      Boolean(suggestion.canonicalKey) &&
      suggestion.canonicalKey === gate.componentCanonicalKey;
    if (
      isGateTarget &&
      gate.purchaseAllowed &&
      gate.purchaseCompatibility === 'EXACT'
    ) {
      return {
        ...suggestion,
        evidenceState: 'PURCHASE_VERIFIED',
        requestAllowed: true,
        missingEvidence: [],
      };
    }
    if (isGateTarget && gate.replacementAllowed) {
      return {
        ...suggestion,
        evidenceState: 'REPLACEMENT_CONFIRMED',
        requestAllowed: false,
        missingEvidence: missing,
      };
    }
    if (isGateTarget) {
      return {
        ...suggestion,
        evidenceState: 'REQUIRES_TESTS',
        requestAllowed: false,
        missingEvidence: missing,
      };
    }
    return {
      ...suggestion,
      evidenceState: 'INFORMATIONAL',
      requestAllowed: false,
    };
  });
}

/**
 * Filter to keep just the high-risk items. Useful for "we recommend you do
 * NOT buy this without a qualified tech" ribbons in the wizard.
 */
export function filterRiskParts(
  suggestions: PartSuggestion[],
): PartSuggestion[] {
  return suggestions.filter(
    (s) => s.riskPart || isCriticalSafetyPart(s.partName),
  );
}
