/**
 * Tests for lib/parts/ranking.ts
 *
 * The weights here mirror the SQL view
 * `part_quote_ranking_v1` in the foundation migration. If you change one,
 * change both.
 */

import { describe, expect, it } from 'vitest';

import { rankQuotes, scoreQuote, RankableQuote } from '../ranking';

const baseQuote: RankableQuote = {
  id: 'q1',
  price: 100,
  warrantyDays: 30,
  estimatedDeliveryHours: 24,
  compatibilityConfidence: 'MEDIUM',
  ratingAvg: 4.0,
};

describe('scoreQuote', () => {
  it('weights compatibility at 55%', () => {
    const exact = scoreQuote({
      ...baseQuote,
      compatibilityConfidence: 'EXACT',
    });
    const unknown = scoreQuote({
      ...baseQuote,
      compatibilityConfidence: 'UNKNOWN',
    });
    expect(exact - unknown).toBeCloseTo(0.55, 2);
  });

  it('weights reputation at 20%', () => {
    const topRated = scoreQuote({
      ...baseQuote,
      ratingAvg: 5.0,
    });
    const zeroRated = scoreQuote({
      ...baseQuote,
      ratingAvg: 0,
    });
    expect(topRated - zeroRated).toBeCloseTo(0.20, 2);
  });

  it('clamps rating to [0..1]', () => {
    const tooHigh = scoreQuote({
      ...baseQuote,
      ratingAvg: 99,
    });
    const capped = scoreQuote({
      ...baseQuote,
      ratingAvg: 5,
    });
    expect(tooHigh).toBeCloseTo(capped, 5);
  });
});

describe('rankQuotes', () => {
  it('returns an empty list on empty input', () => {
    expect(rankQuotes([])).toEqual([]);
  });

  it('sorts by composite score descending', () => {
    const ranked = rankQuotes([
      { ...baseQuote, id: 'low', compatibilityConfidence: 'LOW' },
      { ...baseQuote, id: 'high', compatibilityConfidence: 'HIGH' },
      { ...baseQuote, id: 'med', compatibilityConfidence: 'MEDIUM' },
    ]);
    expect(ranked.map((r) => r.id)).toEqual(['high', 'med', 'low']);
  });

  it('tags BEST_COMPAT to the leader when leader >= HIGH', () => {
    const ranked = rankQuotes([
      { ...baseQuote, id: 'a', compatibilityConfidence: 'EXACT' },
      { ...baseQuote, id: 'b', compatibilityConfidence: 'HIGH' },
    ]);
    expect(ranked[0].id).toBe('a');
    expect(ranked[0].primaryTag).toBe('BEST_COMPAT');
  });

  it('tags CHEAPEST to the lowest-priced candidate within 85% of the leader', () => {
    const ranked = rankQuotes([
      {
        ...baseQuote,
        id: 'best',
        compatibilityConfidence: 'HIGH',
        price: 200,
      },
      {
        ...baseQuote,
        id: 'cheap',
        compatibilityConfidence: 'MEDIUM',
        price: 50,
      },
      {
        ...baseQuote,
        id: 'reject',
        compatibilityConfidence: 'LOW',
        price: 30,
      },
    ]);
    const cheap = ranked.find((r) => r.id === 'cheap');
    expect(cheap?.primaryTag).toBe('CHEAPEST');
    // The cheap-tagged one must NOT be the same as the best_compat one.
    const best = ranked.find((r) => r.primaryTag === 'BEST_COMPAT');
    expect(best?.id).not.toBe('cheap');
  });

  it('does NOT tag anything when scores are below the floor', () => {
    const ranked = rankQuotes([
      { ...baseQuote, id: 'a', compatibilityConfidence: 'LOW' },
      { ...baseQuote, id: 'b', compatibilityConfidence: 'UNKNOWN' },
    ]);
    expect(ranked.every((r) => r.primaryTag === null)).toBe(true);
  });

  it('tags FASTEST to the shortest ETA candidate within threshold', () => {
    const ranked = rankQuotes([
      {
        ...baseQuote,
        id: 'best',
        compatibilityConfidence: 'HIGH',
        estimatedDeliveryHours: 12,
      },
      {
        ...baseQuote,
        id: 'fast',
        compatibilityConfidence: 'MEDIUM',
        estimatedDeliveryHours: 2,
      },
    ]);
    expect(ranked.find((r) => r.id === 'fast')?.primaryTag).toBe('FASTEST');
  });

  it('tags TOP_RATED to the highest-rated candidate within threshold', () => {
    const ranked = rankQuotes([
      {
        ...baseQuote,
        id: 'best',
        compatibilityConfidence: 'HIGH',
        ratingAvg: 3.5,
      },
      {
        ...baseQuote,
        id: 'rated',
        compatibilityConfidence: 'MEDIUM',
        ratingAvg: 4.9,
      },
    ]);
    expect(ranked.find((r) => r.id === 'rated')?.primaryTag).toBe('TOP_RATED');
  });
});
