/**
 * Compatibility Panel
 *
 * Renders a CompatibilityResult (from `lib/parts/compatibility.ts`) in a
 * driver-readable way. The page can read this panel for:
 *
 *   - the verdict pill (NEVER says "compatible guaranteed");
 *   - the verbatim P0230 fuel-pump warning;
 *   - the safety-install warning;
 *   - the list of questions to ask the repuestera;
 *   - the things the user needs to confirm (VIN, OEM, photo, etc.)
 *
 * No domain logic here: the panel is a dumb renderer. All decisions come
 * from the engine, so the panel stays future-proof.
 */

import React from 'react';
import {
  ShieldAlert,
  ShieldCheck,
  AlertTriangle,
  Info,
  HelpCircle,
} from 'lucide-react';

import { CompatibilityResult, CompatibilityWarning } from '../lib/parts';

const VERDICT_TONE: Record<CompatibilityResult['confidence'], {
  label: string;
  bg: string;
  border: string;
  text: string;
  icon: React.ReactNode;
}> = {
  EXACT: {
    label: 'EXACTA',
    bg: 'bg-emerald-500/10',
    border: 'border-emerald-500/40',
    text: 'text-emerald-300',
    icon: <ShieldCheck size={14} />,
  },
  HIGH: {
    label: 'PROBABLE',
    bg: 'bg-cyan-500/10',
    border: 'border-cyan-500/40',
    text: 'text-cyan-300',
    icon: <ShieldCheck size={14} />,
  },
  MEDIUM: {
    label: 'PROBABLE',
    bg: 'bg-amber-500/10',
    border: 'border-amber-500/40',
    text: 'text-amber-300',
    icon: <AlertTriangle size={14} />,
  },
  LOW: {
    label: 'BAJA',
    bg: 'bg-orange-500/10',
    border: 'border-orange-500/40',
    text: 'text-orange-300',
    icon: <AlertTriangle size={14} />,
  },
  UNKNOWN: {
    label: 'INSUFICIENTE',
    bg: 'bg-steel-700/40',
    border: 'border-steel-500/40',
    text: 'text-steel-300',
    icon: <Info size={14} />,
  },
};

const SEVERITY_TONE: Record<CompatibilityWarning['severity'], {
  bg: string;
  border: string;
  text: string;
}> = {
  BLOCK: {
    bg: 'bg-red-500/10',
    border: 'border-red-500/40',
    text: 'text-red-300',
  },
  WARN: {
    bg: 'bg-amber-500/10',
    border: 'border-amber-500/40',
    text: 'text-amber-300',
  },
  INFO: {
    bg: 'bg-cyan-500/10',
    border: 'border-cyan-500/40',
    text: 'text-cyan-300',
  },
};

const SEVERITY_ICON: Record<CompatibilityWarning['severity'], React.ReactNode> = {
  BLOCK: <ShieldAlert size={16} className="mt-0.5 shrink-0" />,
  WARN: <AlertTriangle size={16} className="mt-0.5 shrink-0" />,
  INFO: <Info size={16} className="mt-0.5 shrink-0" />,
};

interface CompatibilityPanelProps {
  result: CompatibilityResult;
}

export function CompatibilityPanel({ result }: CompatibilityPanelProps) {
  const tone = VERDICT_TONE[result.confidence];

  // BLOCK warnings pin to top so the user sees them first.
  const sortedWarnings = [...result.warnings].sort((a, b) => {
    const order = { BLOCK: 0, WARN: 1, INFO: 2 } as const;
    return order[a.severity] - order[b.severity];
  });

  return (
    <div className="space-y-3" data-testid="compatibility-panel">
      {/* Verdict pill */}
      <div
        className={`flex items-center gap-2 rounded-lg border px-3 py-2 ${tone.bg} ${tone.border}`}
      >
        <span className={tone.text}>{tone.icon}</span>
        <div className="flex-1">
          <div className={`text-xs font-mono font-bold uppercase tracking-wide ${tone.text}`}>
            Compatibilidad {tone.label}
          </div>
          <p className="font-mono text-[10px] text-steel-300 mt-0.5 leading-snug">
            {describeVerdict(result)}
          </p>
        </div>
      </div>

      {/* Warnings */}
      {sortedWarnings.length > 0 && (
        <div className="space-y-2">
          {sortedWarnings.map((w, i) => {
            const t = SEVERITY_TONE[w.severity];
            return (
              <div
                key={`${w.code}-${i}`}
                className={`flex items-start gap-2 rounded-lg border p-3 text-xs ${t.bg} ${t.border} ${t.text}`}
              >
                {SEVERITY_ICON[w.severity]}
                <span className="leading-snug">{w.message}</span>
              </div>
            );
          })}
        </div>
      )}

      {/* Required confirmations */}
      {result.requiredConfirmations.length > 0 && (
        <div className="rounded-lg border border-steel-500/30 bg-steel-800/30 p-3">
          <div className="font-mono text-[10px] text-steel-300 uppercase tracking-wide mb-2">
            Para subir la confianza
          </div>
          <ul className="space-y-1">
            {result.requiredConfirmations.map((r, i) => (
              <li
                key={i}
                className="text-xs text-white flex items-start gap-2"
              >
                <span className="text-cyan-400 mt-0.5">▸</span>
                <span>{r}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Recommended questions for the repuestera */}
      {result.recommendedQuestions.length > 0 && (
        <div className="rounded-lg border border-cyan-500/30 bg-cyan-500/5 p-3">
          <div className="font-mono text-[10px] text-cyan-300 uppercase tracking-wide mb-2 flex items-center gap-1.5">
            <HelpCircle size={12} /> Preguntas para la repuestera
          </div>
          <ul className="space-y-1">
            {result.recommendedQuestions.map((q, i) => (
              <li key={i} className="text-xs text-cyan-100 flex items-start gap-2">
                <span className="text-cyan-400 mt-0.5">?</span>
                <span>{q}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

// Inline copy so the panel can still render gracefully when bundled standalone.
function describeVerdict(result: CompatibilityResult): string {
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
