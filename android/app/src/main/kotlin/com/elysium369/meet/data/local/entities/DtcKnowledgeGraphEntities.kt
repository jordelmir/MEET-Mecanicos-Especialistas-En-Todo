package com.elysium369.meet.data.local.entities

import androidx.room.*

/**
 * DTC Knowledge Graph Entities
 *
 * These 8 entities normalize the flat DtcDefinitionEntity (table: dtc_definitions)
 * into a proper relational knowledge graph for the MEET Elite OBD2 diagnostic app.
 *
 * All entities reference dtc_definitions via dtcCode (no @ForeignKey to avoid cascade issues).
 */

// =============================================================================
// 1. DtcSymptomEntity — Symptoms associated with a DTC
// =============================================================================
@Entity(
    tableName = "dtc_symptoms",
    indices = [Index(value = ["dtcCode"])]
)
data class DtcSymptomEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "dtcCode")
    val dtcCode: String,

    @ColumnInfo(name = "manufacturer", defaultValue = "GENERIC")
    val manufacturer: String = "GENERIC",

    @ColumnInfo(name = "symptomEs")
    val symptomEs: String,

    @ColumnInfo(name = "symptomEn")
    val symptomEn: String? = null,

    @ColumnInfo(name = "probability")
    val probability: String, // "alta", "media", "baja"

    @ColumnInfo(name = "isDriverNoticeable", defaultValue = "1")
    val isDriverNoticeable: Boolean = true
)

// =============================================================================
// 2. DtcCauseEntity — Root causes associated with a DTC
// =============================================================================
@Entity(
    tableName = "dtc_causes",
    indices = [Index(value = ["dtcCode"])]
)
data class DtcCauseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "dtcCode")
    val dtcCode: String,

    @ColumnInfo(name = "manufacturer", defaultValue = "GENERIC")
    val manufacturer: String = "GENERIC",

    @ColumnInfo(name = "causeEs")
    val causeEs: String,

    @ColumnInfo(name = "causeEn")
    val causeEn: String? = null,

    @ColumnInfo(name = "probability")
    val probability: String, // "alta", "media", "baja"

    @ColumnInfo(name = "componentAffected")
    val componentAffected: String? = null,

    @ColumnInfo(name = "isElectronic", defaultValue = "0")
    val isElectronic: Boolean = false,

    @ColumnInfo(name = "isMechanical", defaultValue = "0")
    val isMechanical: Boolean = false
)

// =============================================================================
// 3. DtcProcedureEntity — Step-by-step diagnostic procedures
// =============================================================================
@Entity(
    tableName = "dtc_procedures",
    indices = [Index(value = ["dtcCode"])]
)
data class DtcProcedureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "dtcCode")
    val dtcCode: String,

    @ColumnInfo(name = "manufacturer", defaultValue = "GENERIC")
    val manufacturer: String = "GENERIC",

    @ColumnInfo(name = "stepNumber")
    val stepNumber: Int,

    @ColumnInfo(name = "titleEs")
    val titleEs: String,

    @ColumnInfo(name = "descriptionEs")
    val descriptionEs: String,

    @ColumnInfo(name = "toolRequired")
    val toolRequired: String? = null,

    @ColumnInfo(name = "expectedValue")
    val expectedValue: String? = null,

    @ColumnInfo(name = "estimatedMinutes", defaultValue = "15")
    val estimatedMinutes: Int = 15,

    @ColumnInfo(name = "difficulty", defaultValue = "medio")
    val difficulty: String = "medio", // "facil", "medio", "dificil"

    @ColumnInfo(name = "icon", defaultValue = "🔧")
    val icon: String = "🔧"
)

// =============================================================================
// 4. DtcRelatedPidEntity — OBD2 PIDs relevant to diagnosing a DTC
// =============================================================================
@Entity(
    tableName = "dtc_related_pids",
    indices = [Index(value = ["dtcCode"])]
)
data class DtcRelatedPidEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "dtcCode")
    val dtcCode: String,

    @ColumnInfo(name = "manufacturer", defaultValue = "GENERIC")
    val manufacturer: String = "GENERIC",

    @ColumnInfo(name = "pidCommand")
    val pidCommand: String, // e.g. "$01 04"

    @ColumnInfo(name = "pidNameEs")
    val pidNameEs: String,

    @ColumnInfo(name = "pidNameEn")
    val pidNameEn: String? = null,

    @ColumnInfo(name = "normalRange")
    val normalRange: String? = null,

    @ColumnInfo(name = "unit")
    val unit: String? = null,

    @ColumnInfo(name = "priority", defaultValue = "0")
    val priority: Int = 0 // lower = more important
)

// =============================================================================
// 5. DtcCoOccurrenceEntity — DTCs that commonly appear together
// =============================================================================
@Entity(
    tableName = "dtc_co_occurrences",
    indices = [
        Index(value = ["dtcCode"]),
        Index(value = ["relatedDtcCode"])
    ]
)
data class DtcCoOccurrenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "dtcCode")
    val dtcCode: String,

    @ColumnInfo(name = "relatedDtcCode")
    val relatedDtcCode: String,

    @ColumnInfo(name = "correlationStrength", defaultValue = "0.5")
    val correlationStrength: Float = 0.5f, // 0.0 to 1.0

    @ColumnInfo(name = "combinedDiagnosisEs")
    val combinedDiagnosisEs: String? = null, // e.g. "P0171 + P0174 = Fuga de vacío probable"

    @ColumnInfo(name = "combinedDiagnosisEn")
    val combinedDiagnosisEn: String? = null
)

// =============================================================================
// 6. DtcVehicleCompatEntity — Vehicle-specific applicability for a DTC
// =============================================================================
@Entity(
    tableName = "dtc_vehicle_compat",
    indices = [
        Index(value = ["dtcCode"]),
        Index(value = ["manufacturer"])
    ]
)
data class DtcVehicleCompatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "dtcCode")
    val dtcCode: String,

    @ColumnInfo(name = "manufacturer")
    val manufacturer: String,

    @ColumnInfo(name = "make")
    val make: String,

    @ColumnInfo(name = "model")
    val model: String? = null,

    @ColumnInfo(name = "yearFrom")
    val yearFrom: Int? = null,

    @ColumnInfo(name = "yearTo")
    val yearTo: Int? = null,

    @ColumnInfo(name = "engineType")
    val engineType: String? = null,

    @ColumnInfo(name = "specialNotesEs")
    val specialNotesEs: String? = null
)

// =============================================================================
// 7. DtcVerifiedFixEntity — Community/TSB/OEM verified repairs
// =============================================================================
@Entity(
    tableName = "dtc_verified_fixes",
    indices = [Index(value = ["dtcCode"])]
)
data class DtcVerifiedFixEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "dtcCode")
    val dtcCode: String,

    @ColumnInfo(name = "manufacturer", defaultValue = "GENERIC")
    val manufacturer: String = "GENERIC",

    @ColumnInfo(name = "fixDescriptionEs")
    val fixDescriptionEs: String,

    @ColumnInfo(name = "fixDescriptionEn")
    val fixDescriptionEn: String? = null,

    @ColumnInfo(name = "successRate", defaultValue = "0.0")
    val successRate: Float = 0.0f, // 0.0 to 1.0

    @ColumnInfo(name = "voteCount", defaultValue = "0")
    val voteCount: Int = 0,

    @ColumnInfo(name = "partRequired")
    val partRequired: String? = null,

    @ColumnInfo(name = "estimatedCostUsd")
    val estimatedCostUsd: Float? = null,

    @ColumnInfo(name = "difficultyLevel", defaultValue = "medio")
    val difficultyLevel: String = "medio",

    @ColumnInfo(name = "source")
    val source: String? = null, // e.g. "comunidad", "tsb", "oem"

    @ColumnInfo(name = "addedAt")
    val addedAt: Long = System.currentTimeMillis()
)

// =============================================================================
// 8. DtcRepairCostEntity — Regional repair cost estimates
// =============================================================================
@Entity(
    tableName = "dtc_repair_costs",
    indices = [Index(value = ["dtcCode"])]
)
data class DtcRepairCostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "dtcCode")
    val dtcCode: String,

    @ColumnInfo(name = "manufacturer", defaultValue = "GENERIC")
    val manufacturer: String = "GENERIC",

    @ColumnInfo(name = "region", defaultValue = "LATAM")
    val region: String = "LATAM", // LATAM, US, EU

    @ColumnInfo(name = "minCostUsd")
    val minCostUsd: Float,

    @ColumnInfo(name = "maxCostUsd")
    val maxCostUsd: Float,

    @ColumnInfo(name = "laborHours")
    val laborHours: Float? = null,

    @ColumnInfo(name = "partsDescription")
    val partsDescription: String? = null,

    @ColumnInfo(name = "currency", defaultValue = "USD")
    val currency: String = "USD",

    @ColumnInfo(name = "source")
    val source: String? = null,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long
)

// =============================================================================
// 9. DtcSearchIndexEntity — FTS Search Index for DTCs, Symptoms, and Causes
// =============================================================================
@Fts4
@Entity(tableName = "dtc_search_index")
data class DtcSearchIndexEntity(
    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "descriptionEs")
    val descriptionEs: String,

    @ColumnInfo(name = "symptoms")
    val symptoms: String,

    @ColumnInfo(name = "causes")
    val causes: String
)

