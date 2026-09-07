package com.elysium369.meet.core.memory

/**
 * ASTRA V6 §17 — Dreaming Engine (Memory Consolidation).
 *
 * Inspired by OpenClaw's biological sleep model:
 * - Light Sleep: Ingest + deduplicate recent entries
 * - REM Sleep: Pattern recognition, recurring themes, entity frequency
 * - Deep Sleep: Score candidates with 6 signals → promote to long-term
 *
 * This engine runs periodically (background cron or on-demand) to transform
 * noisy short-term observations into durable, curated long-term knowledge.
 *
 * The LLM influence is bounded: scoring and promotion gates are deterministic.
 * AI is only used for pattern recognition (REM phase), never for gate decisions.
 */

// ─── Promotion Scoring (6 Signals) ───

/**
 * Six weighted signals for memory promotion scoring.
 * Adapted from OpenClaw's Deep Sleep promotion algorithm.
 *
 * A memory entry must cross the PROMOTION_THRESHOLD to be elevated
 * from short-term to curated long-term (MEMORY.md equivalent).
 */
data class PromotionScore(
    /** How relevant is this memory to the user's active goals/domains? (0.0-1.0) */
    val relevance: Double = 0.0,
    /** How many times has this information been referenced/accessed? */
    val frequencyScore: Double = 0.0,
    /** Has it been queried from different contexts/domains? (0.0-1.0) */
    val queryDiversity: Double = 0.0,
    /** How recently was it created/updated? Decays over time. (0.0-1.0) */
    val recency: Double = 0.0,
    /** Has it survived a previous consolidation pass without being superseded? (0.0-1.0) */
    val consolidationSurvival: Double = 0.0,
    /** Does it connect to multiple other memory entries (rich concept)? (0.0-1.0) */
    val conceptualRichness: Double = 0.0,
) {
    /**
     * Weighted composite score.
     * Weights empirically tuned: relevance and frequency dominate.
     */
    val composite: Double
        get() = (relevance * 0.25) +
            (frequencyScore * 0.20) +
            (queryDiversity * 0.15) +
            (recency * 0.15) +
            (consolidationSurvival * 0.10) +
            (conceptualRichness * 0.15)

    val meetsPromotionThreshold: Boolean
        get() = composite >= PROMOTION_THRESHOLD

    companion object {
        /** Minimum composite score for promotion to long-term memory */
        const val PROMOTION_THRESHOLD = 0.45
    }
}

// ─── Consolidation Phase Results ───

data class DeduplicationResult(
    val entriesScanned: Int,
    val duplicatesFound: Int,
    val duplicatesMerged: Int,
    val entriesAfter: Int,
)

data class PatternRecognitionResult(
    val patternsDetected: List<DetectedPattern>,
    val recurringEntities: List<RecurringEntity>,
    val suggestedInsights: List<SuggestedInsight>,
)

data class DetectedPattern(
    val description: String,
    val entryIds: List<String>,
    val confidence: Double,
    val patternType: PatternType,
)

enum class PatternType {
    TEMPORAL_RECURRENCE,    // Same event/action repeating over time
    ENTITY_CLUSTER,         // Group of related entities frequently co-occurring
    BEHAVIORAL_ROUTINE,     // User does X then Y then Z regularly
    KNOWLEDGE_GAP,          // Repeated queries about unknown topic
    SKILL_PROGRESSION,      // Improving at something over time
}

data class RecurringEntity(
    val entityKey: String,
    val occurrenceCount: Int,
    val contexts: List<String>,
)

data class SuggestedInsight(
    val description: String,
    val sourceEntryIds: List<String>,
    val suggestedMemoryClass: MemoryClass,
    val confidence: Double,
)

data class PromotionResult(
    val candidatesEvaluated: Int,
    val promoted: Int,
    val rejected: Int,
    val scores: List<Pair<String, PromotionScore>>,
)

// ─── Dreaming Engine ───

class DreamingEngine(
    private val provider: MemoryProvider,
) {

    /**
     * Phase 1: Light Sleep — Ingest and deduplicate.
     * Scans recent entries, finds near-duplicates, merges them.
     */
    suspend fun lightSleep(namespace: String): DeduplicationResult {
        val entries = provider.query(
            MemoryQuery(
                ownerId = "", // will be set by provider
                namespace = namespace,
                onlyActive = true,
            )
        )

        var duplicatesMerged = 0
        val seen = mutableMapOf<String, MemoryEntry>()

        for (entry in entries) {
            val normalizedKey = entry.key.trim().lowercase()
            val existing = seen[normalizedKey]

            if (existing != null && existing.memoryClass == entry.memoryClass) {
                // Near-duplicate found — keep the newer one, supersede the older
                val (older, newer) = if (entry.createdAtEpochMs > existing.createdAtEpochMs) {
                    existing to entry
                } else {
                    entry to existing
                }

                provider.supersede(older.id, newer.copy(
                    version = newer.version + 1,
                    evidenceRefs = (newer.evidenceRefs + older.evidenceRefs).distinct(),
                ))
                seen[normalizedKey] = newer
                duplicatesMerged++
            } else {
                seen[normalizedKey] = entry
            }
        }

        return DeduplicationResult(
            entriesScanned = entries.size,
            duplicatesFound = duplicatesMerged,
            duplicatesMerged = duplicatesMerged,
            entriesAfter = entries.size - duplicatesMerged,
        )
    }

    /**
     * Phase 2: REM Sleep — Pattern recognition.
     * Detects recurring themes, frequently referenced entities,
     * and potential insights from the memory corpus.
     *
     * This phase MAY use AI assistance for pattern recognition,
     * but the output is always structured data (not free text).
     */
    suspend fun remSleep(namespace: String): PatternRecognitionResult {
        val entries = provider.query(
            MemoryQuery(
                ownerId = "",
                namespace = namespace,
                onlyActive = true,
            )
        )

        // Detect recurring entities (keys that appear in multiple entries)
        val entityCounts = mutableMapOf<String, MutableList<String>>()
        for (entry in entries) {
            for (tag in entry.tags) {
                entityCounts.getOrPut(tag) { mutableListOf() }.add(entry.id)
            }
        }

        val recurringEntities = entityCounts
            .filter { it.value.size >= 2 }
            .map { (key, ids) ->
                RecurringEntity(
                    entityKey = key,
                    occurrenceCount = ids.size,
                    contexts = ids.take(5),
                )
            }
            .sortedByDescending { it.occurrenceCount }

        // Detect patterns by memory class clustering
        val classClusters = entries.groupBy { it.memoryClass }
        val patterns = mutableListOf<DetectedPattern>()

        for ((memClass, classEntries) in classClusters) {
            if (classEntries.size >= 3) {
                patterns.add(DetectedPattern(
                    description = "Cluster of ${classEntries.size} entries in ${memClass.description}",
                    entryIds = classEntries.map { it.id },
                    confidence = (classEntries.size.toDouble() / entries.size).coerceAtMost(1.0),
                    patternType = PatternType.ENTITY_CLUSTER,
                ))
            }
        }

        // Detect knowledge gaps (LOW confidence entries)
        val lowConfidence = entries.filter { it.confidence == MemoryConfidence.LOW }
        if (lowConfidence.size >= 2) {
            patterns.add(DetectedPattern(
                description = "Knowledge gap: ${lowConfidence.size} low-confidence entries need verification",
                entryIds = lowConfidence.map { it.id },
                confidence = 0.7,
                patternType = PatternType.KNOWLEDGE_GAP,
            ))
        }

        return PatternRecognitionResult(
            patternsDetected = patterns,
            recurringEntities = recurringEntities,
            suggestedInsights = emptyList(), // AI-generated insights go here
        )
    }

    /**
     * Phase 3: Deep Sleep — Promotion scoring.
     * Evaluates each candidate with 6 deterministic signals.
     * Entries crossing the threshold are promoted to long-term memory.
     *
     * INVARIANT: Scoring is 100% deterministic. No AI influence on gate decisions.
     */
    suspend fun deepSleep(namespace: String): PromotionResult {
        val entries = provider.query(
            MemoryQuery(
                ownerId = "",
                namespace = namespace,
                onlyActive = true,
            )
        )

        val allEntries = entries
        val scores = mutableListOf<Pair<String, PromotionScore>>()
        var promoted = 0
        var rejected = 0

        for (entry in allEntries) {
            val score = scoreEntry(entry, allEntries)
            scores.add(entry.id to score)

            if (score.meetsPromotionThreshold) {
                // Promote: upgrade confidence to HIGH if not already AUTHORITATIVE
                if (entry.confidence.ordinal > MemoryConfidence.HIGH.ordinal) {
                    provider.supersede(entry.id, entry.copy(
                        confidence = MemoryConfidence.HIGH,
                        version = entry.version + 1,
                    ))
                }
                promoted++
            } else {
                rejected++
            }
        }

        return PromotionResult(
            candidatesEvaluated = allEntries.size,
            promoted = promoted,
            rejected = rejected,
            scores = scores,
        )
    }

    /**
     * Scores a memory entry with 6 deterministic signals.
     */
    internal fun scoreEntry(
        entry: MemoryEntry,
        allEntries: List<MemoryEntry>,
    ): PromotionScore {
        val now = System.currentTimeMillis()

        // 1. Relevance: AUTHORITATIVE/HIGH > MODERATE > LOW
        val relevance = when (entry.confidence) {
            MemoryConfidence.AUTHORITATIVE -> 1.0
            MemoryConfidence.HIGH -> 0.8
            MemoryConfidence.MODERATE -> 0.5
            MemoryConfidence.LOW -> 0.2
            MemoryConfidence.CONTESTED -> 0.1
            MemoryConfidence.SUPERSEDED -> 0.0
        }

        // 2. Frequency: entries with more evidence are more valuable
        val frequencyScore = (entry.evidenceRefs.size.toDouble() / 5.0).coerceAtMost(1.0)

        // 3. Query diversity: entries with diverse tags span multiple contexts
        val queryDiversity = (entry.tags.size.toDouble() / 4.0).coerceAtMost(1.0)

        // 4. Recency: exponential decay over 90 days
        val ageMs = now - entry.updatedAtEpochMs
        val ninetyDaysMs = 90L * 24 * 60 * 60 * 1000
        val recency = Math.exp(-ageMs.toDouble() / ninetyDaysMs)

        // 5. Consolidation survival: higher version = survived more passes
        val consolidationSurvival = ((entry.version - 1).toDouble() / 3.0).coerceAtMost(1.0)

        // 6. Conceptual richness: connected to other entries via shared tags
        val sharedTagCount = allEntries.count { other ->
            other.id != entry.id && other.tags.any { it in entry.tags }
        }
        val conceptualRichness = (sharedTagCount.toDouble() / 5.0).coerceAtMost(1.0)

        return PromotionScore(
            relevance = relevance,
            frequencyScore = frequencyScore,
            queryDiversity = queryDiversity,
            recency = recency,
            consolidationSurvival = consolidationSurvival,
            conceptualRichness = conceptualRichness,
        )
    }

    /**
     * Full dreaming cycle: Light Sleep → REM Sleep → Deep Sleep.
     * Returns comprehensive consolidation results.
     */
    suspend fun dream(namespace: String): DreamResult {
        val startMs = System.currentTimeMillis()

        val dedup = lightSleep(namespace)
        val patterns = remSleep(namespace)
        val promotion = deepSleep(namespace)

        val endMs = System.currentTimeMillis()

        return DreamResult(
            namespace = namespace,
            deduplication = dedup,
            patternRecognition = patterns,
            promotion = promotion,
            durationMs = endMs - startMs,
        )
    }
}

data class DreamResult(
    val namespace: String,
    val deduplication: DeduplicationResult,
    val patternRecognition: PatternRecognitionResult,
    val promotion: PromotionResult,
    val durationMs: Long,
)
