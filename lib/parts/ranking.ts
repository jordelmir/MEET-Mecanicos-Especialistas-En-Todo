/**
 * Quote Ranking Engine — pure, no I/O.
 *
 * Mirrors the SQL view `part_quote_ranking_v1` from the foundation migration,
 * but computes the score at the React-edge so we can show live updates without
 * a round trip. The exact weights mirror the SQL view exactly so that what
 * the screen shows matches what the DB computed.
 *
 * Inputs are quote-shaped (the candidate list) plus a vehicle fingerprint
 * that is used to weight compat (more evidence on the vehicle -> higher
 * confidence bonus).
 */

import {
  CompatibilityConfidence,
  VehicleFingerprint,
} from './types';

export interface RankableQuote {
  id: string;
  price: number;
  warrantyDays: number;
  estimatedDeliveryHours: number;
  compatibilityConfidence: CompatibilityConfidence;
  ratingAvg: number;
  photoUrls?: string[];
}

export interface RankedQuote extends RankableQuote {
  compositeScore: number;
  /** Marketing-friendly tag for the wizard UI. */
  primaryTag: 'BEST_COMPAT' | 'CHEAPEST' | 'FASTEST' | 'TOP_RATED' | null;
}

const WEIGHT_COMPAT = 0.55;
const WEIGHT_REPUTATION = 0.20;
const WEIGHT_DELIVERY = 0.15;
const WEIGHT_WARRANTY = 0.10;

function compatScore(c: CompatibilityConfidence): number {
  switch (c) {
    case 'EXACT':
      return 1.0;
    case 'HIGH':
      return 0.8;
    case 'MEDIUM':
      return 0.55;
    case 'LOW':
      return 0.25;
    case 'UNKNOWN':
    default:
      return 0;
  }
}

function warrantyScore(w: number): number {
  if (w >= 90) return 1.0;
  if (w >= 30) return 0.6;
  if (w > 0) return 0.3;
  return 0;
}

/**
 * Mirror of the SQL view weights. Keep in sync with
 * `supabase/migrations/20260704000000_parts_marketplace_foundation.sql`.
 */
export function scoreQuote(
  q: RankableQuote,
  _vehicle?: VehicleFingerprint,
): number {
  const sCompat = compatScore(q.compatibilityConfidence);
  const sRep = Math.max(0, Math.min(1, q.ratingAvg / 5));
  const sDel = Math.max(0, 1 - q.estimatedDeliveryHours / 168);
  const sWar = warrantyScore(q.warrantyDays);
  return (
    sCompat * WEIGHT_COMPAT +
    sRep * WEIGHT_REPUTATION +
    sDel * WEIGHT_DELIVERY +
    sWar * WEIGHT_WARRANTY
  );
}

/**
 * Rank the candidates. Tags are mutually exclusive per item:
 *   BEST_COMPAT  -> highest composite score AND compat >= HIGH.
 *   CHEAPEST     -> lowest price among alternatives within 50% of the leader.
 *   FASTEST      -> lowest ETA   among alternatives within 50% of the leader.
 *   TOP_RATED    -> highest rating among alternatives within 50% of the leader.
 *
 * "Alternative" here means: composite >= 50% of the leader AND compat is
 * MEDIUM or better. We intentionally do not recommend LOW / UNKNOWN
 * confidence items as alternatives to a HIGH/EXACT leader — that would
 * break the safety posture.
 */
export function rankQuotes(
  candidates: RankableQuote[],
  vehicle?: VehicleFingerprint,
): RankedQuote[] {
  if (candidates.length === 0) return [];

  const scored = candidates.map((q) => ({
    ...q,
    compositeScore: scoreQuote(q, vehicle),
    primaryTag: null as RankedQuote['primaryTag'],
  }));

  scored.sort((a, b) => b.compositeScore - a.compositeScore);

  const top = scored[0];
  const threshold = top.compositeScore * 0.5;

  const isWorthyAlternative = (q: (typeof scored)[number]) =>
    q.compositeScore >= threshold &&
    (q.compatibilityConfidence === 'EXACT' ||
      q.compatibilityConfidence === 'HIGH' ||
      q.compatibilityConfidence === 'MEDIUM');

  // BEST_COMPAT
  if (
    top.compatibilityConfidence === 'EXACT' ||
    top.compatibilityConfidence === 'HIGH'
  ) {
    scored[0].primaryTag = 'BEST_COMPAT';
  }

  const alternatives = scored.filter(isWorthyAlternative);

  // CHEAPEST among alternatives
  if (alternatives.length > 0) {
    const cheapest = alternatives.reduce((min, q) =>
      q.price < min.price ? q : min,
    );
    if (cheapest.id !== top.id) cheapest.primaryTag = 'CHEAPEST';
  }
  // FASTEST among alternatives
  if (alternatives.length > 0) {
    const fastest = alternatives.reduce((min, q) =>
      q.estimatedDeliveryHours < min.estimatedDeliveryHours ? q : min,
    );
    if (fastest.id !== top.id && fastest.primaryTag === null) {
      fastest.primaryTag = 'FASTEST';
    }
  }
  // TOP_RATED among alternatives
  if (alternatives.length > 0) {
    const topRated = alternatives.reduce((max, q) =>
      q.ratingAvg > max.ratingAvg ? q : max,
    );
    if (topRated.id !== top.id && topRated.primaryTag === null) {
      topRated.primaryTag = 'TOP_RATED';
    }
  }

  return scored;
}
