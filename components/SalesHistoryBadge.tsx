/**
 * Sales History Badge
 *
 * Renders a chip that summarizes a supplier's sales history. Used in the
 * ranking panel and in the repuestera panel to make trust signals visible
 * at a glance, without hiding them behind a "more info" link.
 *
 *   TRUSTED -> emerald
 *   WARM    -> cyan
 *   COLD    -> steel
 */

import React from 'react';
import { Award, Star, AlertCircle } from 'lucide-react';

import { tagQuote, SalesHistoryTag } from '../lib/parts';

interface SalesHistoryBadgeProps {
  ratingAvg: number;
  totalSales: number;
  claimRate: number;
}

const TAG_TONE: Record<SalesHistoryTag, {
  bg: string;
  border: string;
  text: string;
  icon: React.ReactNode;
  label: string;
}> = {
  TRUSTED: {
    bg: 'bg-emerald-500/10',
    border: 'border-emerald-500/40',
    text: 'text-emerald-300',
    icon: <Award size={12} />,
    label: 'CONFIABLE',
  },
  WARM: {
    bg: 'bg-cyan-500/10',
    border: 'border-cyan-500/40',
    text: 'text-cyan-300',
    icon: <Star size={12} />,
    label: 'NUEVO',
  },
  COLD: {
    bg: 'bg-steel-700/40',
    border: 'border-steel-500/40',
    text: 'text-steel-300',
    icon: <AlertCircle size={12} />,
    label: 'VERIFICAR',
  },
};

export function SalesHistoryBadge({
  ratingAvg,
  totalSales,
  claimRate,
}: SalesHistoryBadgeProps) {
  const tag: SalesHistoryTag = tagQuote({ ratingAvg, totalSales, claimRate });
  const t = TAG_TONE[tag];

  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border text-[10px] font-mono font-bold ${t.bg} ${t.border} ${t.text}`}
      title={`${totalSales} ventas, ${(claimRate * 100).toFixed(1)}% disputas, ${ratingAvg.toFixed(1)}★`}
      data-testid="sales-history-badge"
      data-tag={tag}
    >
      {t.icon}
      {t.label}
      <span className="ml-1 opacity-80">★ {ratingAvg.toFixed(1)}</span>
    </span>
  );
}
