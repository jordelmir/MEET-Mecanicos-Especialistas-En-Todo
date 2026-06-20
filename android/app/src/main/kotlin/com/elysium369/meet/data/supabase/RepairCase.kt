package com.elysium369.meet.data.supabase

import kotlinx.serialization.Serializable

@Serializable
data class RepairCase(
    val id: String = "",
    val vehicle_make: String,
    val vehicle_model: String,
    val year: Int,
    val engine: String,
    val country: String,
    val dtc_code: String,
    val symptoms: String,
    val solution: String,
    val cost: Double,
    val time_spent: Int, // in minutes
    val parts_used: String = "", // comma-separated or JSON list
    val verified: Boolean = false,
    val votes: Int = 0,
    val success_rate: Double = 100.0,
    val created_at: String? = null
)

@Serializable
data class RepairComment(
    val id: String = "",
    val case_id: String,
    val user_id: String,
    val author_name: String,
    val author_reputation: String, // Usuario, Contribuidor, Experto, Mecánico certificado, Master
    val comment_body: String,
    val created_at: String? = null
)

@Serializable
data class RepairPart(
    val id: String = "",
    val case_id: String,
    val part_name: String,
    val part_number: String? = null,
    val estimated_price: Double = 0.0
)

@Serializable
data class RepairVote(
    val id: String = "",
    val case_id: String,
    val user_id: String,
    val vote_type: String // "up" or "down"
)

@Serializable
data class RepairVerification(
    val id: String = "",
    val case_id: String,
    val verifier_id: String,
    val verifier_name: String,
    val verifier_title: String,
    val verified_at: String? = null
)
