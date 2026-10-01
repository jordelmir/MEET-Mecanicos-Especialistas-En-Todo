package com.elysium.vanguard.core.search

import kotlin.math.max
import kotlin.math.min

/**
 * PHASE 1.9 — Fuzzy search engine for filenames.
 *
 * Implements a Smith-Waterman-flavoured scoring algorithm with:
 *   - subsequence match (not just prefix)
 *   - case-insensitive
 *   - bonus for consecutive matches ("cont" matches "contract" tighter than "cot")
 *   - penalty for early divergence
 *   - 0.0 means no match; 1.0 means exact match.
 *
 * The result list is sorted descending by score; ties broken by shorter name
 * first (more specific match).
 *
 * Why not Fuse4j or Lucene?
 *   - Both pull a 1+ MB native binary that we don't need yet. Phase 1 keeps
 *     the APK small; Phase 3 will swap in semantic search and a real index.
 */
class FuzzySearchEngine @javax.inject.Inject constructor() {

    data class Scored(
        val candidate: String,
        val score: Double,
        val matchedIndices: IntArray
    )

    /**
     * Score [candidate] against [query]. Higher is better; 0.0 means no match.
     *
     * @param minScore results below this threshold are filtered out (default 0.25)
     */
    fun score(query: String, candidate: String, minScore: Double = 0.25): Scored? {
        if (query.isEmpty()) return Scored(candidate, 1.0, IntArray(0))
        if (candidate.isEmpty()) return null

        val q = query.lowercase()
        val c = candidate.lowercase()

        // Fast-path exact match.
        if (q == c) return Scored(candidate, 1.0, IntArray(c.length) { it })

        // Subsequence check first (cheap rejection).
        if (!isSubsequence(q, c)) return null

        // DP table: dp[i][j] = best score aligning q[0..i] with c[0..j]
        val qlen = q.length
        val clen = c.length
        val dp = Array(qlen + 1) { DoubleArray(clen + 1) }
        val trace = Array(qlen + 1) { IntArray(clen + 1) } // 0 none, 1 diag, 2 up, 3 left

        for (i in 1..qlen) {
            var rowMax = 0.0
            for (j in 1..clen) {
                val matchScore = if (q[i - 1] == c[j - 1]) {
                    val base = 1.0
                    val consecBonus = if (j > 1 && c[j - 2] == q[i - 1]) 0.5 else 0.0
                    val firstCharBonus = if (i == 1 && j == 1) 0.4 else 0.0
                    val wordStartBonus = if (j == 1 || c[j - 2] in " _-./\\") 0.3 else 0.0
                    base + consecBonus + firstCharBonus + wordStartBonus
                } else 0.0

                val diag = dp[i - 1][j - 1] + matchScore
                val up = dp[i - 1][j] - 0.3 // gap in candidate
                val left = dp[i][j - 1] - 0.1 // gap in query

                val best = max(0.0, max(diag, max(up, left)))
                dp[i][j] = best
                trace[i][j] = when {
                    best == diag && diag > 0 -> 1
                    best == up -> 2
                    best == left -> 3
                    else -> 0
                }
                rowMax = max(rowMax, best)
            }
            // Early termination: if no match possible in this row, abort.
            if (rowMax == 0.0 && i > 1) {
                return null
            }
        }

        val rawScore = dp[qlen][clen]
        val normalised = rawScore / max(qlen.toDouble(), 1.0)
        if (normalised < minScore) return null

        val matchedIndices = backtrack(trace, qlen, clen)
        return Scored(candidate, normalised, matchedIndices)
    }

    /**
     * Score a list of candidates and return them sorted by score.
     */
    fun search(query: String, candidates: List<String>, limit: Int = 50, minScore: Double = 0.25): List<Scored> {
        return candidates
            .mapNotNull { score(query, it, minScore) }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.candidate.length })
            .take(limit)
    }

    private fun isSubsequence(query: String, candidate: String): Boolean {
        var qi = 0
        for (ci in candidate.indices) {
            if (qi < query.length && query[qi] == candidate[ci]) qi++
        }
        return qi == query.length
    }

    private fun backtrack(trace: Array<IntArray>, qi: Int, ci: Int): IntArray {
        var i = qi
        var j = ci
        val indices = mutableListOf<Int>()
        while (i > 0 && j > 0) {
            when (trace[i][j]) {
                1 -> {
                    indices += (j - 1)
                    i--
                    j--
                }
                2 -> i--
                3 -> j--
                else -> break
            }
        }
        return indices.reversed().toIntArray()
    }
}
