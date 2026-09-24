package com.elysium369.meet.safety.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "safety_public_points_local",
    indices = [
        Index("category"),
        Index("publishedAt"),
        Index("serverVersion"),
    ],
)
data class SafetyPublicPointEntity(
    @PrimaryKey
    val publicPointId: String,

    val category: String,

    val displayLatitude: Double,
    val displayLongitude: Double,

    val geoDisclosure: String,

    val locationAccuracyMeters: Int?,

    val label: String,

    val claimState: String,

    val independentSourceCount: Int,

    val civilSourceCount: Int,
    val journalisticSourceCount: Int,
    val publicRecordSourceCount: Int,
    val documentarySourceCount: Int,
    val institutionalSourceCount: Int,

    val countryCode: String?,
    val admin1Code: String?,
    val admin2Code: String?,
    val publicH3Cell: String?,

    val firstDocumentedAt: Long?,
    val lastReviewedAt: Long,
    val publishedAt: Long,

    val serverVersion: Long,

    val syncedAt: Long,

    val victimCountDocumented: Int = 0,
    val victimFemaleCount: Int = 0,
    val victimMaleCount: Int = 0,
    val victimUnknownSexCount: Int = 0,
)
