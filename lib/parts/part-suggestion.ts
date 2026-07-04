/**
 * Part Suggestion Engine — pure, no I/O.
 *
 * Given a DTC code (or a 3D component slug, or a mechanic work-order context),
 * returns an ordered list of part suggestions. The list IS the visible side of
 * the "DTC -> diagnosis -> mechanic -> compatible part" chain.
 *
 * The ordering is intentional and explicit. For P0230 specifically, the engine
 * must:
 *   1. Prioritize the cheap / quick checks FIRST (relay, fuse, wiring, ground).
 *   2. Move the high-cost / wrong-install-risk part (the fuel pump) LAST, with
 *      an explicit warning if the user picks it anyway.
 *
 * Later: we will feed this from the Knowledge Pack (PR-4); for now the static
 * map below covers the most common OBD-II patterns seen in Costa Rica.
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
      rationale: 'P0230 commonly points to the relay circuit. Cheap to swap.',
    },
    {
      partName: 'Fusible circuito bomba',
      category: 'ELECTRICAL',
      position: 'FUSE_BOX',
      priority: 2,
      rationale: 'Blown fuse accounts for a meaningful slice of P0230 cases.',
    },
    {
      partName: 'Arnés eléctrico / terminales de bomba',
      category: 'ELECTRICAL',
      position: 'ENGINE',
      priority: 3,
      rationale: 'Corroded connectors or broken harness wires trigger P0230.',
    },
    {
      partName: 'Sensor de presión de combustible',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 4,
      rationale: 'Verify FTP sensor is reporting before condemning the pump.',
    },
    {
      partName: 'Bomba de combustible',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 99,
      rationale:
        'Final-tier part. Replace only AFTER voltage, ground, relay and ' +
        'fuel pressure have been verified with a gauge.',
      disclaimer:
        'No reemplazar la bomba sin confirmar antes: alimentación, tierra, ' +
        'relé/fusible y presión con manómetro.',
      riskPart: true,
    },
  ],

  P0420: [
    {
      partName: 'Sensor de oxígeno aguas abajo',
      category: 'EXHAUST',
      position: 'EXHAUST',
      priority: 1,
      rationale:
        'Downstream O2 sensor drift causes P0420 in older vehicles. Cheap ' +
        'first attempt.',
    },
    {
      partName: 'Junta de escape',
      category: 'EXHAUST',
      position: 'EXHAUST',
      priority: 2,
      rationale: 'Exhaust leak ahead of the cat triggers the same code.',
    },
    {
      partName: 'Catalizador',
      category: 'EXHAUST',
      position: 'CENTER',
      priority: 99,
      rationale: 'Last. Confirm wiring and O2 sensors first; cats are expensive.',
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
      rationale: 'Worn spark plugs are the most common P0300 cause.',
    },
    {
      partName: 'Bobina de encendido',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 2,
      rationale: 'Failing coil triggers random misfires.',
    },
    {
      partName: 'Inyector',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 3,
      rationale: 'Last: clogged or leaking injector requires diagnostic time.',
    },
  ],

  P0171: [
    {
      partName: 'Tapa del depósito de gasolina',
      category: 'ENGINE',
      position: 'NOT_APPLICABLE',
      priority: 1,
      rationale: 'Loose / bad gas cap produces P0171 in many vehicles.',
    },
    {
      partName: 'Manguera de vacío',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 2,
      rationale: 'Unmetered air from a cracked hose skews the fuel trim.',
    },
    {
      partName: 'Sensor MAF',
      category: 'ENGINE',
      position: 'ENGINE',
      priority: 3,
      rationale: 'Dirty MAF reads low; clean before replacing.',
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
  },
  abs_module: {
    partName: 'Módulo ABS',
    category: 'BRAKES',
    position: 'NOT_APPLICABLE',
    priority: 1,
    rationale: 'Selected from the 3D chassis viewer.',
    disclaimer: 'Pieza safety-critical: instalación por técnico calificado.',
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
