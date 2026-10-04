package com.elysium369.meet.safety.science.domain

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 14 — Remote Feature Gates backed by Supabase.
 *
 * Conservative defaults:
 *   - ALL gates disabled by default
 *   - Server disabled = feature OFF regardless of cache
 *   - Network failure = use last known state (cached)
 *   - No cached state = feature OFF
 *
 * NEVER allow a feature to be ON by default.
 * Only server can enable.
 */
@Singleton
class SupabaseFeatureGateRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : SafetyScienceFeatureGateRepository {

    // In-memory cache — defaults to all disabled
    private val cache = mutableMapOf<SafetyScienceGate, Boolean>()

    override suspend fun isEnabled(gate: SafetyScienceGate): Boolean {
        // Master gate check: if SAFETY_SCIENCE is off, nothing works
        if (gate != SafetyScienceGate.SAFETY_SCIENCE) {
            val masterEnabled = cache[SafetyScienceGate.SAFETY_SCIENCE] ?: false
            if (!masterEnabled) return false
        }
        return cache[gate] ?: false
    }

    override suspend fun refreshFromServer() {
        try {
            val response = supabase.postgrest["safety_scientific_feature_gates"]
                .select()
                .decodeList<FeatureGateRow>()

            // Apply server state — disabled overrides everything
            for (row in response) {
                val gate = try {
                    SafetyScienceGate.valueOf(row.gateName)
                } catch (_: IllegalArgumentException) {
                    continue // unknown gate, skip
                }
                cache[gate] = row.enabled
            }
        } catch (_: Exception) {
            // Network failure → keep cached state
            // If no cache exists, all gates remain OFF (conservative)
        }
    }

    @Serializable
    private data class FeatureGateRow(
        val gateName: String,
        val enabled: Boolean,
    )
}
