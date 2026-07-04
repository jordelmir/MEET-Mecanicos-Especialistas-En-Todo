/**
 * Quote Ranking Panel
 *
 * Renders the result of `rankQuotes(...)` from `lib/parts/ranking.ts` with
 * the per-item primary tag (BEST_COMPAT, CHEAPEST, FASTEST, TOP_RATED).
 *
 * Tags are rendered as colored badges so the user can scan options at a
 * glance. The verbatim-risk ribbon is shown for fuel pumps and other
 * safety-critical parts, paired with the CompatibilityPanel above.
 */

import React from 'react';
import { Award, DollarSign, Clock, Star } from 'lucide-react';

import { RankedQuote } from '../lib/parts';

const TAG_LABEL: Record<NonNullable<RankedQuote['primaryTag']>, {
  text: string;
  bg: string;
  border: string;
  text_color: string;
  icon: React.ReactNode;
}> = {
  BEST_COMPAT: {
    text: 'MEJOR COMPATIBILIDAD',
    bg: 'bg-emerald-500/10',
    border: 'border-emerald-500/40',
    text_color: 'text-emerald-300',
    icon: <Award size={12} />,
  },
  CHEAPEST: {
    text: 'MÁS BARATO',
    bg: 'bg-cyan-500/10',
    border: 'border-cyan-500/40',
    text_color: 'text-cyan-300',
    icon: <DollarSign size={12} />,
  },
  FASTEST: {
    text: 'ENTREGA RÁPIDA',
    bg: 'bg-amber-500/10',
    border: 'border-amber-500/40',
    text_color: 'text-amber-300',
    icon: <Clock size={12} />,
  },
  TOP_RATED: {
    text: 'MEJOR CALIFICADO',
    bg: 'bg-fuchsia-500/10',
    border: 'border-fuchsia-500/40',
    text_color: 'text-fuchsia-300',
    icon: <Star size={12} />,
  },
};

interface QuoteRankingPanelProps {
  ranked: RankedQuote[];
  onAccept?: (quoteId: string) => void;
}

export function QuoteRankingPanel({ ranked, onAccept }: QuoteRankingPanelProps) {
  if (ranked.length === 0) {
    return (
      <div className="rounded-lg border border-steel-500/30 bg-steel-800/30 p-6 text-center">
        <p className="text-xs text-steel-300 font-mono">
          Aún no hay cotizaciones. Las repuesteras visibles son notificadas en
          cuanto publicas la solicitud.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-2" data-testid="quote-ranking-panel">
      {ranked.map((q, i) => {
        const tag = q.primaryTag ? TAG_LABEL[q.primaryTag] : null;
        return (
          <div
            key={q.id}
            className={`rounded-lg border bg-steel-800/40 p-3 transition-all ${
              q.primaryTag === 'BEST_COMPAT'
                ? 'border-emerald-500/40 shadow-[0_0_20px_rgba(16,185,129,0.1)]'
                : 'border-steel-500/30'
            }`}
          >
            <div className="flex items-start justify-between gap-2 mb-2">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="font-mono text-[10px] text-steel-300">
                  #{i + 1}
                </span>
                {tag && (
                  <span
                    className={`flex items-center gap-1 px-2 py-0.5 rounded-full border text-[10px] font-mono font-bold ${tag.bg} ${tag.border} ${tag.text_color}`}
                  >
                    {tag.icon}
                    {tag.text}
                  </span>
                )}
              </div>
              <div className="text-right">
                <div className="text-base font-bold text-white leading-none">
                  ${q.price.toFixed(0)}
                </div>
                <div className="font-mono text-[10px] text-steel-300 mt-0.5">
                  {q.estimatedDeliveryHours}h entrega
                </div>
              </div>
            </div>

            <div className="flex items-center gap-3 text-[11px] text-steel-300 font-mono mb-2">
              <span>★ {q.ratingAvg.toFixed(1)}</span>
              {q.warrantyDays > 0 && (
                <span>· {q.warrantyDays} días garantía</span>
              )}
              <span>· {q.compatibilityConfidence}</span>
            </div>

            {onAccept && (
              <button
                onClick={() => onAccept(q.id)}
                className="w-full text-xs font-mono font-bold uppercase tracking-wider rounded-lg bg-forge-500 hover:bg-forge-600 text-black py-2 transition-all"
              >
                Aceptar cotización
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
