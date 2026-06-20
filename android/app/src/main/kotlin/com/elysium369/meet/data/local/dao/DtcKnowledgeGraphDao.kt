package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.*

/**
 * DAO for the DTC Knowledge Graph.
 *
 * Provides CRUD operations across all 8 knowledge graph entities,
 * full-text search spanning dtc_definitions + symptoms + causes,
 * bulk delete methods for data reloads, and aggregate counts.
 */
@Dao
interface DtcKnowledgeGraphDao {

    // =========================================================================
    // SYMPTOMS
    // =========================================================================

    @Query(
        """
        SELECT * FROM dtc_symptoms
        WHERE dtcCode = :code
        ORDER BY CASE probability
            WHEN 'alta' THEN 0
            WHEN 'media' THEN 1
            ELSE 2
        END
        """
    )
    suspend fun getSymptomsForDtc(code: String): List<DtcSymptomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptoms(symptoms: List<DtcSymptomEntity>)

    // =========================================================================
    // CAUSES
    // =========================================================================

    @Query(
        """
        SELECT * FROM dtc_causes
        WHERE dtcCode = :code
        ORDER BY CASE probability
            WHEN 'alta' THEN 0
            WHEN 'media' THEN 1
            ELSE 2
        END
        """
    )
    suspend fun getCausesForDtc(code: String): List<DtcCauseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCauses(causes: List<DtcCauseEntity>)

    // =========================================================================
    // PROCEDURES
    // =========================================================================

    @Query("SELECT * FROM dtc_procedures WHERE dtcCode = :code ORDER BY stepNumber ASC")
    suspend fun getProceduresForDtc(code: String): List<DtcProcedureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcedures(procedures: List<DtcProcedureEntity>)

    // =========================================================================
    // RELATED PIDS
    // =========================================================================

    @Query("SELECT * FROM dtc_related_pids WHERE dtcCode = :code ORDER BY priority ASC")
    suspend fun getRelatedPidsForDtc(code: String): List<DtcRelatedPidEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelatedPids(pids: List<DtcRelatedPidEntity>)

    // =========================================================================
    // CO-OCCURRENCES
    // =========================================================================

    @Query(
        """
        SELECT * FROM dtc_co_occurrences
        WHERE dtcCode = :code OR relatedDtcCode = :code
        ORDER BY correlationStrength DESC
        """
    )
    suspend fun getCoOccurrencesForDtc(code: String): List<DtcCoOccurrenceEntity>

    @Query(
        """
        SELECT * FROM dtc_co_occurrences
        WHERE dtcCode IN (:codes) OR relatedDtcCode IN (:codes)
        ORDER BY correlationStrength DESC
        """
    )
    suspend fun getCoOccurrencesForMultipleDtcs(codes: List<String>): List<DtcCoOccurrenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoOccurrences(coOccurrences: List<DtcCoOccurrenceEntity>)

    // =========================================================================
    // VEHICLE COMPATIBILITY
    // =========================================================================

    @Query(
        """
        SELECT * FROM dtc_vehicle_compat
        WHERE dtcCode = :code AND (make = :make OR make = 'GENERIC')
        """
    )
    suspend fun getVehicleCompatForDtc(code: String, make: String): List<DtcVehicleCompatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicleCompat(compat: List<DtcVehicleCompatEntity>)

    // =========================================================================
    // VERIFIED FIXES
    // =========================================================================

    @Query(
        """
        SELECT * FROM dtc_verified_fixes
        WHERE dtcCode = :code
        ORDER BY successRate DESC, voteCount DESC
        """
    )
    suspend fun getVerifiedFixesForDtc(code: String): List<DtcVerifiedFixEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerifiedFixes(fixes: List<DtcVerifiedFixEntity>)

    @Query("UPDATE dtc_verified_fixes SET voteCount = voteCount + 1 WHERE id = :fixId")
    suspend fun upvoteFix(fixId: Long)

    // =========================================================================
    // REPAIR COSTS
    // =========================================================================

    @Query(
        """
        SELECT * FROM dtc_repair_costs
        WHERE dtcCode = :code AND (region = :region OR region = 'GLOBAL')
        ORDER BY CASE region WHEN :region THEN 0 ELSE 1 END
        """
    )
    suspend fun getRepairCostsForDtc(
        code: String,
        region: String = "LATAM"
    ): List<DtcRepairCostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepairCosts(costs: List<DtcRepairCostEntity>)

    // =========================================================================
    // FULL-TEXT SEARCH (across definitions, symptoms, and causes)
    // =========================================================================

    @Query("DELETE FROM dtc_search_index")
    suspend fun clearSearchIndex()

    @Query("""
        INSERT INTO dtc_search_index(code, descriptionEs, symptoms, causes)
        SELECT 
            d.code, 
            d.descriptionEs, 
            COALESCE((SELECT group_concat(symptomEs, ' | ') FROM dtc_symptoms s WHERE s.dtcCode = d.code), '') as symptoms,
            COALESCE((SELECT group_concat(causeEs, ' | ') FROM dtc_causes c WHERE c.dtcCode = d.code), '') as causes
        FROM dtc_definitions d
    """)
    suspend fun rebuildSearchIndex()

    @Query(
        """
        SELECT d.* FROM dtc_definitions d
        JOIN dtc_search_index f ON d.code = f.code
        WHERE dtc_search_index MATCH :ftsQuery
        LIMIT 50
        """
    )
    suspend fun querySearchFts(ftsQuery: String): List<DtcDefinitionEntity>

    @Query(
        """
        SELECT DISTINCT d.*
        FROM dtc_definitions d
        LEFT JOIN dtc_symptoms s ON d.code = s.dtcCode
        LEFT JOIN dtc_causes c ON d.code = c.dtcCode
        WHERE d.code LIKE '%' || :query || '%'
           OR d.descriptionEs LIKE '%' || :query || '%'
           OR d.descriptionEn LIKE '%' || :query || '%'
           OR s.symptomEs LIKE '%' || :query || '%'
           OR c.causeEs LIKE '%' || :query || '%'
        LIMIT 50
        """
    )
    suspend fun queryLegacyLike(query: String): List<DtcDefinitionEntity>

    @Transaction
    suspend fun searchKnowledgeGraph(query: String): List<DtcDefinitionEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        
        // Format query for prefix-matching FTS index (e.g. "sensor oxig" -> "sensor* AND oxig*")
        val ftsQuery = trimmed.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" AND ") { "$it*" }
            
        return try {
            val results = querySearchFts(ftsQuery)
            if (results.isEmpty() && trimmed.length < 4) {
                // If FTS has no prefix matches and query is short, try a broad LIKE query
                queryLegacyLike(trimmed)
            } else {
                results
            }
        } catch (e: Exception) {
            // Fallback to legacy LIKE search if FTS has an error (e.g. syntax)
            queryLegacyLike(trimmed)
        }
    }

    // =========================================================================
    // BULK DELETE (for data reloads)
    // =========================================================================

    @Query("DELETE FROM dtc_symptoms")
    suspend fun deleteAllSymptoms()

    @Query("DELETE FROM dtc_causes")
    suspend fun deleteAllCauses()

    @Query("DELETE FROM dtc_procedures")
    suspend fun deleteAllProcedures()

    @Query("DELETE FROM dtc_related_pids")
    suspend fun deleteAllRelatedPids()

    @Query("DELETE FROM dtc_co_occurrences")
    suspend fun deleteAllCoOccurrences()

    @Query("DELETE FROM dtc_repair_costs")
    suspend fun deleteAllRepairCosts()

    // =========================================================================
    // COUNTS
    // =========================================================================

    @Query("SELECT COUNT(*) FROM dtc_symptoms")
    suspend fun getSymptomsCount(): Int

    @Query("SELECT COUNT(*) FROM dtc_causes")
    suspend fun getCausesCount(): Int

    @Query("SELECT COUNT(*) FROM dtc_procedures")
    suspend fun getProceduresCount(): Int
}
