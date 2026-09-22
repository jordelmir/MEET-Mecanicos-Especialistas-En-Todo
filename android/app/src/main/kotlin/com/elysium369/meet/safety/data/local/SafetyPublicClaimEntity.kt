package com.elysium369.meet.safety.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "safety_public_claims_local",
    primaryKeys = ["caseId", "claimId"],
)
data class SafetyPublicClaimEntity(
    val caseId: String,
    val claimId: String,

    val predicate: String,
    val claimState: String,

    val independentSourceCount: Int,
    val evidenceCount: Int,

    val civilSourceCount: Int,
    val journalisticSourceCount: Int,
    val publicRecordSourceCount: Int,
    val documentarySourceCount: Int,
    val institutionalSourceCount: Int,

    val serverVersion: Long,
)
