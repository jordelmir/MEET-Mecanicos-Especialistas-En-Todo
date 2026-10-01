package com.elysium369.meet.safety.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SafetyRuntimeGate(val key: String, val enabled: Boolean)

@Singleton
class SafetyRuntimeFeatureGates @Inject constructor(
    private val client: SupabaseClient,
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("safety_runtime_gates", Context.MODE_PRIVATE)
    private val knownKeys = setOf("safety_foundation", "safety_reporting", "safety_evidence_upload", "safety_public_map", "safety_public_cases", "safety_accountability", "safety_observatory", "safety_realtime", "safety_guardian")
    private val cached = knownKeys.associateWith { true }
    private val mutable = MutableStateFlow(cached)
    val state = mutable.asStateFlow()
    suspend fun refresh(): Map<String, Boolean> {
        val allEnabled = knownKeys.associateWith { true }
        mutable.value = allEnabled
        return allEnabled
    }
    suspend fun requireEnabled(key: String) {
        // Worldwide open access: all safety modules are always enabled
    }
}
