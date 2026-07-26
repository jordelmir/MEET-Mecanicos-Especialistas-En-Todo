/**
 * Compatibility Engine — pure, no I/O.
 *
 * The engine receives a `CompatibilityContext` and returns a verdict. It never
 * asserts "compatible" as a guarantee. Verdicts go from UNKNOWN -> LOW ->
 * MEDIUM -> HIGH -> EXACT, and we list exactly what the user needs to confirm
 * to climb one rung up.
 *
 * Deterministic: same input, same output. No randomness. No clock reads (we
 * accept an injected `now` if the consumer needs time-relative logic later).
 *
 * Anti-fraud posture:
 *   - A quote that doesn't pass through this engine cannot be marked EXACT.
 *   - A P0230 + fuel-pump request always carries a BLOCK warning, no matter
 *     how complete the rest of the context looks.
 *   - Critical safety parts (brakes, steering, suspension, airbag, fuel,
 *     high-voltage) always trigger a visible install-by-a-qualified-tech
 *     warning. This is the literal text the repuestera UI must show verbatim.
 */

import {
  CompatibilityConfidence,
  CompatibilityContext,
  CompatibilityResult,
  CompatibilityWarning,
  isCriticalSafetyPart,
  isValidVin,
} from './types';

const SAFETY_INSTALL_WARNING: CompatibilityWarning = {
  code: 'CRITICAL_SAFETY_PART',
  severity: 'WARN',
  message:
    'Instalación recomendada por técnico calificado. Una pieza incompatible ' +
    'puede causar falla mecánica, eléctrica o de seguridad.',
};

const P0230_PUMP_WARNING: CompatibilityWarning = {
  code: 'DTC_P0230_PUMP_REQUIRES_CONFIRMATION',
  severity: 'BLOCK',
  message:
    'No reemplazar bomba de combustible sin confirmar antes: alimentación ' +
    'eléctrica, tierra, integridad de relé y fusible, y presión de ' +
    'combustible con manómetro. P0230 identifica el circuito de control y ' +
    'no confirma por sí solo que la bomba esté dañada.',
};

const NO_VIN_WARNING = (
  whatWeHave: string,
): CompatibilityWarning => ({
  code: 'NO_VIN',
  severity: 'WARN',
  message:
    'No se recibió VIN. Continuamos con ' +
    whatWeHave +
    '. Para subir la confianza a EXACT, proporciona el VIN de 17 caracteres.',
});

const NO_OEM_WARNING: CompatibilityWarning = {
  code: 'NO_OEM',
  severity: 'WARN',
  message:
    'No se recibió un número OEM verificado. Un número de parte escrito o aftermarket ' +
    'no demuestra por sí solo compatibilidad exacta. Recomendamos adjuntar foto de la pieza, del ' +
    'conector o de la caja de fusibles.',
};

const INVALID_VIN_WARNING: CompatibilityWarning = {
  code: 'INVALID_VIN',
  severity: 'BLOCK',
  message:
    'El VIN recibido no tiene el formato estructural válido. Debe contener ' +
    'exactamente 17 caracteres y no puede incluir I, O ni Q. Corrígelo o ' +
    'elimínalo antes de confirmar compatibilidad.',
};

const NO_PHOTO_WARNING: CompatibilityWarning = {
  code: 'NO_PHOTO_EVIDENCE',
  severity: 'INFO',
  message:
    'Sin foto del repuesto viejo. Recomendamos adjuntar al menos una foto ' +
    'para que la repuestera pueda validar referencia y conector.',
};

const PART_NAME_AMBIGUOUS_WARNING: CompatibilityWarning = {
  code: 'PART_NAME_AMBIGUOUS',
  severity: 'WARN',
  message:
    'El nombre de la pieza es genérico. Para reducir ambigüedad indica ' +
    'posición exacta, OEM o número de parte original.',
};

/** Defensive: matches "bomba de combustible", "fuel pump", etc. */
function looksLikeFuelPump(partName: string): boolean {
  const l = partName.toLowerCase();
  return (
    l.includes('bomba de combustible') ||
    l.includes('bomba combustible') ||
    l.includes('fuel pump') ||
    l.includes('bomba gasolina') ||
    l.includes('gasoline pump')
  );
}

interface TierEvidence {
  vinProvided: boolean;
  hasVin: boolean;
  hasOem: boolean;
  hasBrand: boolean;
  hasModel: boolean;
  hasYear: boolean;
  hasEngine: boolean;
  hasTransmission: boolean;
  hasPosition: boolean;
  hasPhotoEvidence: boolean;
}

function collectEvidence(ctx: CompatibilityContext): TierEvidence {
  const v = ctx.vehicle;
  return {
    vinProvided: !!v.vin && v.vin.trim().length > 0,
    hasVin: isValidVin(v.vin),
    hasOem: !!v.oemNumber && v.oemNumber.trim().length > 0,
    hasBrand: !!v.brand && v.brand.trim().length > 0,
    hasModel: !!v.model && v.model.trim().length > 0,
    hasYear:
      Number.isInteger(v.year) && (v.year as number) >= 1886 && (v.year as number) <= 2100,
    hasEngine: !!v.engine && v.engine.trim().length > 0,
    hasTransmission: !!v.transmission && v.transmission.trim().length > 0,
    hasPosition: !!ctx.position && ctx.position !== 'NOT_APPLICABLE',
    hasPhotoEvidence: !!ctx.photoUrls && ctx.photoUrls.length > 0,
  };
}

/**
 * Decide the verdict tier based on evidence. We never jump tiers; we cap at
 * what we can defend in front of an angry customer with a wrong fuel pump.
 */
function pickTier(ctx: CompatibilityContext, e: TierEvidence): {
  confidence: CompatibilityConfidence;
  requiredConfirmations: string[];
  rationale: string[];
} {
  const rationale: string[] = [];
  const requiredConfirmations: string[] = [];

  // EXACT requires VIN + OEM, OR a closed tuple ending in OEM.
  if (
    e.hasVin &&
    e.hasOem
  ) {
    rationale.push('VIN + número OEM verificado disponibles: tupla cerrada.');
    return {
      confidence: 'EXACT',
      requiredConfirmations: [],
      rationale,
    };
  }
  if (
    e.hasBrand &&
    e.hasModel &&
    e.hasYear &&
    e.hasEngine &&
    e.hasOem
  ) {
    rationale.push(
      'marca + modelo + año + motorización + OEM: tupla cerrada sin VIN.',
    );
    return {
      confidence: 'EXACT',
      requiredConfirmations: [],
      rationale,
    };
  }

  // HIGH: brand+model+year+OEM, or brand+model+engine+position+OEM.
  if (e.hasBrand && e.hasModel && e.hasYear && e.hasOem) {
    rationale.push('marca + modelo + año + OEM.');
    return {
      confidence: 'HIGH',
      requiredConfirmations: e.hasVin
        ? []
        : ['Confirmar VIN (17 caracteres) en placa o tarjeta de propiedad.'],
      rationale,
    };
  }
  if (e.hasBrand && e.hasModel && e.hasEngine && e.hasPosition && e.hasOem) {
    rationale.push('marca + modelo + motorización + posición + OEM.');
    return {
      confidence: 'HIGH',
      requiredConfirmations: ['Confirmar año del vehículo.'],
      rationale,
    };
  }

  // MEDIUM: brand+model con motor, o brand+model con foto.
  if (e.hasBrand && e.hasModel && (e.hasEngine || e.hasYear)) {
    rationale.push('marca + modelo + motor o año.');
    return {
      confidence: 'MEDIUM',
      requiredConfirmations: [
        e.hasOem
          ? 'Confirmar que el OEM coincida con la pieza instalada.'
          : 'Adjuntar número OEM o foto legible de la etiqueta de la pieza.',
        e.hasVin ? 'Confirmar VIN para subir a EXACT.' : 'Adjuntar VIN.',
      ].filter(Boolean),
      rationale,
    };
  }
  if (e.hasBrand && e.hasModel && e.hasPhotoEvidence) {
    rationale.push('marca + modelo + foto de la pieza.');
    return {
      confidence: 'MEDIUM',
      requiredConfirmations: [
        e.hasOem ? 'Confirmar OEM.' : 'Adjuntar número OEM.',
      ],
      rationale,
    };
  }

  // LOW: apenas algo de contexto.
  if (e.hasBrand && e.hasModel) {
    rationale.push('solo marca + modelo. Año y motor desconocidos.');
    return {
      confidence: 'LOW',
      requiredConfirmations: [
        'Confirmar año del vehículo.',
        'Confirmar motorización (cilindrada, transmisión, combustible).',
        'Adjuntar número OEM o foto de la pieza.',
      ],
      rationale,
    };
  }
  if (e.hasPosition && ctx.partName.trim().length >= 4) {
    rationale.push('solo posición + nombre genérico de pieza.');
    return {
      confidence: 'LOW',
      requiredConfirmations: [
        'Seleccionar vehículo activo (marca + modelo + año + motor).',
        'Adjuntar número OEM o foto legible.',
      ],
      rationale,
    };
  }

  rationale.push('contexto insuficiente para emitir un veredicto significativo.');
  return {
    confidence: 'UNKNOWN',
    requiredConfirmations: [
      'Seleccionar vehículo activo (marca + modelo + año + motor).',
      'Adjuntar número OEM o foto de la pieza o conector.',
    ],
    rationale,
  };
}

/**
 * Anti-fraud + educational warnings layered on top of the tier.
 *
 * Order matters — the P0230 BLOCK warning is always emitted when relevant,
 * even at EXACT tier, because the safety-of-lives argument dominates the
 * confidence arithmetic.
 *
 * Returns the *possibly-clamped* confidence (EXACT -> MEDIUM if a BLOCK
 * warning is present) so the caller can honor the demotion.
 */
function buildWarnings(
  ctx: CompatibilityContext,
  e: TierEvidence,
  confidence: CompatibilityConfidence,
  warnings: CompatibilityWarning[],
): {
  warnings: CompatibilityWarning[];
  recommendedQuestions: string[];
  crossReferenceNumbers: string[];
  finalConfidence: CompatibilityConfidence;
} {
  const out: CompatibilityWarning[] = [...warnings];
  const recommendedQuestions: string[] = [];
  const crossReferenceNumbers: string[] = [];

  // 1) Critical safety surface — brakes, steering, airbag, suspension.
  if (isCriticalSafetyPart(ctx.partName)) {
    out.push(SAFETY_INSTALL_WARNING);
    if (
      ctx.partName.toLowerCase().includes('freno') ||
      ctx.partName.toLowerCase().includes('brake')
    ) {
      recommendedQuestions.push(
        '¿La pieza es específica para el eje (delantero/trasero) de mi vehículo?',
      );
    }
  }

  // 2) DTC-P0230 fuel pump case (explicit anti-fraud posture).
  const dtcs = (ctx.dtcCodes ?? []).map((d) => d.toUpperCase());
  const isP0230 = dtcs.includes('P0230');
  const isFuelPump = looksLikeFuelPump(ctx.partName);
  if (isP0230 && isFuelPump) {
    out.push(P0230_PUMP_WARNING);
    recommendedQuestions.push(
      '¿Confirma que la pieza es específicamente la bomba de combustible y ' +
        'no el relé o el fusible? P0230 identifica el circuito de control y ' +
        'no confirma por sí solo una bomba dañada.',
    );
    recommendedQuestions.push(
      '¿Pueden verificar voltaje en el relé y en el conector de la bomba ' +
        'antes de cerrar la venta?',
    );
  } else if (isFuelPump) {
    // Even without P0230, fuel pumps get the safety-of-lives warning.
    out.push(SAFETY_INSTALL_WARNING);
  }

  // 3) Tier-driven prompts.
  if (e.vinProvided && !e.hasVin) {
    out.push(INVALID_VIN_WARNING);
  } else if (!e.hasVin) {
    const whatWeHave =
      ctx.vehicle.brand && ctx.vehicle.model
        ? `${ctx.vehicle.brand} ${ctx.vehicle.model}`
        : 'datos parciales del vehículo';
    out.push(NO_VIN_WARNING(whatWeHave));
  }
  if (!e.hasOem) {
    out.push(NO_OEM_WARNING);
  }
  if (!e.hasPhotoEvidence) {
    out.push(NO_PHOTO_WARNING);
  }

  // 4) Demote a confidence that says EXACT if any BLOCK-class warning exists.
  //    We never want EXACT + BLOCK on screen at the same time.
  const hasBlock = out.some((w) => w.severity === 'BLOCK');
  let finalConfidence: CompatibilityConfidence = confidence;
  if (hasBlock && finalConfidence === 'EXACT') {
    finalConfidence = 'MEDIUM';
  }

  // 5) Ambiguous generic names.
  if (
    ctx.partName.trim().length < 4 ||
    ['parte', 'repuesto', 'pieza', 'repuesto genérico'].includes(
      ctx.partName.trim().toLowerCase(),
    )
  ) {
    out.push(PART_NAME_AMBIGUOUS_WARNING);
  }

  // 6) OEM-derived cross references.
  if (ctx.vehicle.oemNumber) {
    crossReferenceNumbers.push(ctx.vehicle.oemNumber);
  }
  if (ctx.vehicle.partNumber && ctx.vehicle.partNumber !== ctx.vehicle.oemNumber) {
    crossReferenceNumbers.push(ctx.vehicle.partNumber);
  }

  return {
    warnings: out,
    recommendedQuestions,
    crossReferenceNumbers,
    finalConfidence,
  };
}

/**
 * Public API.
 */
export function evaluateCompatibility(
  ctx: CompatibilityContext,
): CompatibilityResult {
  const evidence = collectEvidence(ctx);
  const { confidence, requiredConfirmations, rationale } = pickTier(ctx, evidence);

  // We always start with INFO warnings; buildWarnings upgrades or adds.
  const baseWarnings: CompatibilityWarning[] = [];
  const {
    warnings,
    recommendedQuestions,
    crossReferenceNumbers,
    finalConfidence,
  } = buildWarnings(ctx, evidence, confidence, baseWarnings);

  return {
    confidence: finalConfidence,
    warnings,
    requiredConfirmations,
    crossReferenceNumbers,
    recommendedQuestions,
    rationale,
  };
}

/**
 * Pretty-print the verdict for human-readable UI strips. Never expose this
 * as "compatible guaranteed".
 */
export function describeVerdict(result: CompatibilityResult): string {
  switch (result.confidence) {
    case 'EXACT':
      return 'Compatibilidad EXACTA según los datos aportados. Sigue requiriendo confirmación del proveedor.';
    case 'HIGH':
      return 'Compatibilidad probable; requiere confirmar por VIN, OEM o foto del conector.';
    case 'MEDIUM':
      return 'Compatibilidad probable; faltan datos para subir a HIGH.';
    case 'LOW':
      return 'Compatibilidad estimada baja; se necesita más contexto del vehículo.';
    case 'UNKNOWN':
    default:
      return 'Sin información suficiente para emitir un veredicto.';
  }
}
