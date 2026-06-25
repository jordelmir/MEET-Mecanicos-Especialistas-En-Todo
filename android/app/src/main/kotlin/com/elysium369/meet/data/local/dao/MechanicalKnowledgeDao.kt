package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.data.local.entities.AutomotiveChemicalEntity
import com.elysium369.meet.data.local.entities.ComponentRebuildGuideEntity
import com.elysium369.meet.data.local.entities.MechanicalProcedureEntity
import com.elysium369.meet.data.local.entities.MeetKnowledgeMatrixEntity
import com.elysium369.meet.data.local.entities.SafetyProtocolEntity
import com.elysium369.meet.data.local.entities.SymptomGuideEntity
import com.elysium369.meet.data.local.entities.ToolUsageGuideEntity
import com.elysium369.meet.data.local.entities.TrenchKnowledgeEntity

@Dao
interface MechanicalKnowledgeDao {
    @Query("SELECT COUNT(*) FROM symptom_guides")
    suspend fun symptomGuideCount(): Int

    @Query("SELECT * FROM symptom_guides WHERE symptomId = :symptomId LIMIT 1")
    suspend fun getSymptomGuide(symptomId: String): SymptomGuideEntity?

    @Query("""
        SELECT * FROM symptom_guides
        WHERE lower(title) LIKE '%' || lower(:query) || '%'
           OR lower(searchKeywords) LIKE '%' || lower(:query) || '%'
           OR lower(relatedDtcs) LIKE '%' || lower(:query) || '%'
        ORDER BY CASE dangerLevel WHEN 'high' THEN 0 WHEN 'medium' THEN 1 ELSE 2 END, title
        LIMIT :limit
    """)
    suspend fun searchSymptomGuides(query: String, limit: Int = 12): List<SymptomGuideEntity>

    @Query("""
        SELECT * FROM mechanical_procedures
        WHERE lower(title) LIKE '%' || lower(:query) || '%'
           OR lower(componentId) LIKE '%' || lower(:query) || '%'
           OR lower(system) LIKE '%' || lower(:query) || '%'
           OR lower(searchKeywords) LIKE '%' || lower(:query) || '%'
        ORDER BY difficulty ASC, title
        LIMIT :limit
    """)
    suspend fun searchMechanicalProcedures(query: String, limit: Int = 12): List<MechanicalProcedureEntity>

    @Query("""
        SELECT * FROM component_rebuild_guides
        WHERE lower(componentId) LIKE '%' || lower(:query) || '%'
           OR lower(searchKeywords) LIKE '%' || lower(:query) || '%'
        LIMIT :limit
    """)
    suspend fun searchRebuildGuides(query: String, limit: Int = 12): List<ComponentRebuildGuideEntity>

    @Query("""
        SELECT * FROM trench_knowledge
        WHERE lower(title) LIKE '%' || lower(:query) || '%'
           OR lower(searchKeywords) LIKE '%' || lower(:query) || '%'
        ORDER BY CASE riskLevel WHEN 'critical' THEN 0 WHEN 'high' THEN 1 ELSE 2 END, title
        LIMIT :limit
    """)
    suspend fun searchTrenchKnowledge(query: String, limit: Int = 12): List<TrenchKnowledgeEntity>

    @Query("""
        SELECT * FROM automotive_chemistry
        WHERE lower(name) LIKE '%' || lower(:query) || '%'
           OR lower(category) LIKE '%' || lower(:query) || '%'
           OR lower(searchKeywords) LIKE '%' || lower(:query) || '%'
        ORDER BY name
        LIMIT :limit
    """)
    suspend fun searchChemicals(query: String, limit: Int = 12): List<AutomotiveChemicalEntity>

    @Query("""
        SELECT * FROM tool_usage_guides
        WHERE lower(name) LIKE '%' || lower(:query) || '%'
           OR lower(toolId) LIKE '%' || lower(:query) || '%'
           OR lower(searchKeywords) LIKE '%' || lower(:query) || '%'
        ORDER BY name
        LIMIT :limit
    """)
    suspend fun searchTools(query: String, limit: Int = 12): List<ToolUsageGuideEntity>

    @Query("""
        SELECT * FROM safety_protocols
        WHERE lower(title) LIKE '%' || lower(:query) || '%'
           OR lower(system) LIKE '%' || lower(:query) || '%'
           OR lower(mandatoryBefore) LIKE '%' || lower(:query) || '%'
           OR lower(searchKeywords) LIKE '%' || lower(:query) || '%'
        ORDER BY title
        LIMIT :limit
    """)
    suspend fun searchSafetyProtocols(query: String, limit: Int = 12): List<SafetyProtocolEntity>

    @Query("""
        SELECT * FROM meet_knowledge_matrix
        WHERE (:dtcCode IS NOT NULL AND dtcCode = :dtcCode)
           OR lower(componentName) LIKE '%' || lower(:query) || '%'
           OR lower(systemCategory) LIKE '%' || lower(:query) || '%'
           OR lower(layerDiagnosticsJson) LIKE '%' || lower(:query) || '%'
           OR lower(layerRebuildSpecsJson) LIKE '%' || lower(:query) || '%'
           OR lower(layerTrenchKnowledgeJson) LIKE '%' || lower(:query) || '%'
           OR lower(layerAdvancedEngJson) LIKE '%' || lower(:query) || '%'
        ORDER BY CASE urgencyLevel WHEN 'critical' THEN 0 WHEN 'high' THEN 1 WHEN 'pronto' THEN 2 WHEN 'soon' THEN 2 ELSE 3 END, componentName
        LIMIT :limit
    """)
    suspend fun searchKnowledgeMatrix(query: String, dtcCode: String? = null, limit: Int = 12): List<MeetKnowledgeMatrixEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSymptomGuides(items: List<SymptomGuideEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMechanicalProcedures(items: List<MechanicalProcedureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRebuildGuides(items: List<ComponentRebuildGuideEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrenchKnowledge(items: List<TrenchKnowledgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChemicals(items: List<AutomotiveChemicalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTools(items: List<ToolUsageGuideEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSafetyProtocols(items: List<SafetyProtocolEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnowledgeMatrix(items: List<MeetKnowledgeMatrixEntity>)
}
