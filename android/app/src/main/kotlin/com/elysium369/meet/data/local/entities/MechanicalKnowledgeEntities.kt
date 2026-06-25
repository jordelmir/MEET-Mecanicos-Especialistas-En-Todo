package com.elysium369.meet.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meet_knowledge_matrix",
    indices = [
        Index(value = ["dtcCode", "componentName"], unique = true),
        Index(value = ["dtcCode"]),
        Index(value = ["componentName"]),
        Index(value = ["systemCategory"]),
        Index(value = ["urgencyLevel"])
    ]
)
data class MeetKnowledgeMatrixEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dtcCode: String? = null,
    val componentName: String? = null,
    val systemCategory: String? = null,
    val urgencyLevel: String? = null,
    val layerDiagnosticsJson: String = "{}",
    val layerRebuildSpecsJson: String = "{}",
    val layerTrenchKnowledgeJson: String = "{}",
    val layerAdvancedEngJson: String = "{}",
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mechanical_procedures",
    indices = [
        Index(value = ["componentId"]),
        Index(value = ["system"]),
        Index(value = ["difficulty"])
    ]
)
data class MechanicalProcedureEntity(
    @PrimaryKey
    val componentId: String,
    val system: String,
    val title: String,
    val difficulty: Int,
    val estimatedTimeHours: Double,
    val searchKeywords: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "component_rebuild_guides", indices = [Index(value = ["componentId"])])
data class ComponentRebuildGuideEntity(
    @PrimaryKey
    val componentId: String,
    val rebuildPossible: Boolean,
    val searchKeywords: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "symptom_guides",
    indices = [
        Index(value = ["symptomId"]),
        Index(value = ["dangerLevel"])
    ]
)
data class SymptomGuideEntity(
    @PrimaryKey
    val symptomId: String,
    val title: String,
    val dangerLevel: String,
    val searchKeywords: String,
    val relatedDtcs: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trench_knowledge", indices = [Index(value = ["scenarioId"]), Index(value = ["riskLevel"])])
data class TrenchKnowledgeEntity(
    @PrimaryKey
    val scenarioId: String,
    val title: String,
    val riskLevel: String,
    val searchKeywords: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automotive_chemistry", indices = [Index(value = ["chemicalId"]), Index(value = ["category"])])
data class AutomotiveChemicalEntity(
    @PrimaryKey
    val chemicalId: String,
    val category: String,
    val name: String,
    val searchKeywords: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tool_usage_guides", indices = [Index(value = ["toolId"])])
data class ToolUsageGuideEntity(
    @PrimaryKey
    val toolId: String,
    val name: String,
    val searchKeywords: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "safety_protocols", indices = [Index(value = ["protocolId"]), Index(value = ["system"])])
data class SafetyProtocolEntity(
    @PrimaryKey
    val protocolId: String,
    val system: String,
    val title: String,
    val mandatoryBefore: String,
    val searchKeywords: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)
