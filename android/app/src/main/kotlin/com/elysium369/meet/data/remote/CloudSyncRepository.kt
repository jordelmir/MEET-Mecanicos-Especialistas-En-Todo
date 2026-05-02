package com.elysium369.meet.data.remote

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CloudSyncRepository — Supabase-backed cloud data layer for MEET OBD2.
 * Handles OEM PID vault, DTC lookups, and scan session uploads.
 */
object CloudSyncRepository {
    
    private val client get() = SupabaseModule.client

    // ========================================================================
    // OEM PID VAULT
    // ========================================================================
    
    @Serializable
    data class OemPid(
        val id: String = "",
        val make: String = "",
        val model: String? = null,
        val year_start: Int? = null,
        val year_end: Int? = null,
        val ecu_name: String = "",
        val ecu_header: String = "",
        val pid_hex: String = "",
        val pid_name: String = "",
        val description: String? = null,
        val formula: String? = null,
        val unit: String? = null,
        val min_val: Double = 0.0,
        val max_val: Double = 100.0,
        val service_mode: String = "01",
        val protocol: String = "CAN",
        val category: String = "sensor",
        val is_pro_only: Boolean = false
    )

    /**
     * Fetch all OEM PIDs for a specific vehicle make.
     * Returns PIDs filtered by make, optionally by model and year.
     */
    suspend fun getOemPidsForMake(make: String, model: String? = null, year: Int? = null): List<OemPid> =
        withContext(Dispatchers.IO) {
            try {
                val result = client.postgrest["oem_pids"]
                    .select {
                        filter {
                            eq("make", make)
                            if (model != null) eq("model", model)
                            if (year != null) {
                                lte("year_start", year)
                                gte("year_end", year)
                            }
                        }
                    }
                result.decodeList<OemPid>()
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Get all unique makes available in the OEM vault.
     */
    suspend fun getAvailableMakes(): List<String> = withContext(Dispatchers.IO) {
        try {
            val result = client.postgrest["oem_pids"]
                .select(columns = Columns.list("make"))
            val decoded = result.decodeList<Map<String, String>>()
            decoded.mapNotNull { it["make"] }.distinct().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ========================================================================
    // DTC DEFINITIONS
    // ========================================================================
    
    @Serializable
    data class DtcDefinition(
        val id: String = "",
        val make: String = "",
        val code: String = "",
        val description: String = "",
        val category: String = "general",
        val is_pro_only: Boolean = false
    )

    /**
     * Look up a DTC code from the cloud database.
     * First tries make-specific, then falls back to Universal.
     */
    suspend fun lookupDtc(code: String, make: String? = null): DtcDefinition? =
        withContext(Dispatchers.IO) {
            try {
                // Try make-specific first
                if (make != null) {
                    val specific = client.postgrest["dtcs"]
                        .select {
                            filter {
                                eq("code", code)
                                eq("make", make)
                            }
                        }
                    val results = specific.decodeList<DtcDefinition>()
                    if (results.isNotEmpty()) return@withContext results.first()
                }
                // Fallback to universal
                val universal = client.postgrest["dtcs"]
                    .select {
                        filter {
                            eq("code", code)
                            eq("make", "Universal")
                        }
                    }
                val results = universal.decodeList<DtcDefinition>()
                results.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }

    // ========================================================================
    // SCAN SESSION SYNC
    // ========================================================================
    
    @Serializable
    data class ScanSession(
        val user_id: String,
        val make: String,
        val model: String? = null,
        val year: Int? = null,
        val vin: String? = null,
        val scan_type: String = "full",
        val scan_data: String = "{}" // JSONB as string
    )

    /**
     * Upload a completed scan session to the cloud.
     * Returns true on success.
     */
    suspend fun uploadScanSession(session: ScanSession): Boolean =
        withContext(Dispatchers.IO) {
            try {
                client.postgrest["scans"].insert(session)
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Get scan history for a user.
     */
    suspend fun getScanHistory(userId: String): List<ScanSession> =
        withContext(Dispatchers.IO) {
            try {
                val result = client.postgrest["scans"]
                    .select {
                        filter { eq("user_id", userId) }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }
                result.decodeList<ScanSession>()
            } catch (e: Exception) {
                emptyList()
            }
        }

    // ========================================================================
    // SUBSCRIPTION CHECK
    // ========================================================================
    
    /**
     * Check if user has an active PRO subscription.
     * Returns "free", "pro", or "elite".
     */
    suspend fun getUserPlan(userId: String): String = withContext(Dispatchers.IO) {
        try {
            val result = client.postgrest["subscriptions"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "active")
                    }
                }
            @Serializable
            data class SubInfo(val plan: String)
            val sub = result.decodeSingleOrNull<SubInfo>()
            sub?.plan ?: "free"
        } catch (e: Exception) {
            "free"
        }
    }
}
