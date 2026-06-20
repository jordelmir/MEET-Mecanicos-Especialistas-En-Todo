package com.elysium369.meet.data.supabase

import com.elysium369.meet.data.local.dao.RepairCaseDao
import com.elysium369.meet.data.local.entities.RepairCaseEntity
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class VotesUpdate(val votes: Int)

@Singleton
class RepairCaseRepository @Inject constructor(
    private val repairCaseDao: RepairCaseDao
) {
    // Local bookmarks/saved cases
    fun getSavedCases(): Flow<List<RepairCase>> {
        return repairCaseDao.getSavedCases().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getMyContributions(): Flow<List<RepairCase>> {
        return repairCaseDao.getMyCases().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun isBookmarked(caseId: String): Boolean {
        return repairCaseDao.getCaseById(caseId)?.isBookmarked == true
    }

    suspend fun toggleBookmark(repairCase: RepairCase) {
        val local = repairCaseDao.getCaseById(repairCase.id)
        if (local != null) {
            val newBookmarked = !local.isBookmarked
            if (!newBookmarked && !local.isMyContribution) {
                repairCaseDao.deleteCase(local)
            } else {
                repairCaseDao.updateBookmarkState(repairCase.id, newBookmarked)
            }
        } else {
            repairCaseDao.insertCase(repairCase.toEntity(isBookmarked = true, isMyContribution = false))
        }
    }

    // Remote queries via Supabase
    suspend fun searchCases(
        query: String,
        make: String = "",
        model: String = "",
        year: Int? = null,
        country: String = "",
        dtc: String = "",
        sortBy: String = "votes", // votes, success_rate, date
        onlyVerified: Boolean = false
    ): List<RepairCase> {
        return try {
            val postgrest = SupabaseManager.client.postgrest
            val response = postgrest["repair_cases"].select {
                filter {
                    if (onlyVerified) eq("verified", true)
                    if (make.isNotBlank()) ilike("vehicle_make", make)
                    if (model.isNotBlank()) ilike("vehicle_model", model)
                    if (dtc.isNotBlank()) ilike("dtc_code", dtc)
                    if (year != null) eq("year", year)
                    if (country.isNotBlank()) ilike("country", country)
                }
                when (sortBy) {
                    "success_rate" -> order("success_rate", Order.DESCENDING)
                    "date" -> order("created_at", Order.DESCENDING)
                    else -> order("votes", Order.DESCENDING)
                }
            }.decodeList<RepairCase>()

            // In-memory filter for text search query if any
            if (query.isBlank()) {
                response
            } else {
                val lowerQuery = query.lowercase()
                response.filter {
                    it.vehicle_make.lowercase().contains(lowerQuery) ||
                    it.vehicle_model.lowercase().contains(lowerQuery) ||
                    it.dtc_code.lowercase().contains(lowerQuery) ||
                    it.symptoms.lowercase().contains(lowerQuery) ||
                    it.solution.lowercase().contains(lowerQuery)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to search repair cases", e)
            emptyList()
        }
    }

    suspend fun getCaseById(caseId: String): RepairCase? {
        // Check local first
        repairCaseDao.getCaseById(caseId)?.let { return it.toDomain() }

        // Check remote
        return try {
            val postgrest = SupabaseManager.client.postgrest
            postgrest["repair_cases"].select {
                filter { eq("id", caseId) }
            }.decodeSingleOrNull<RepairCase>()
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to get case details", e)
            null
        }
    }

    suspend fun insertRepairCase(repairCase: RepairCase): Boolean {
        return try {
            val postgrest = SupabaseManager.client.postgrest
            postgrest["repair_cases"].insert(repairCase)
            
            // Cache locally as user contribution
            repairCaseDao.insertCase(repairCase.toEntity(isBookmarked = false, isMyContribution = true))
            true
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to insert repair case", e)
            false
        }
    }

    suspend fun upvoteCase(caseId: String): Boolean {
        return try {
            val currentCase = getCaseById(caseId) ?: return false
            val newVotes = currentCase.votes + 1
            
            val postgrest = SupabaseManager.client.postgrest
            postgrest["repair_cases"].update(VotesUpdate(newVotes)) {
                filter { eq("id", caseId) }
            }
            
            // Update local if cached
            val local = repairCaseDao.getCaseById(caseId)
            if (local != null) {
                repairCaseDao.insertCase(local.copy(votes = newVotes))
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to upvote case", e)
            false
        }
    }

    suspend fun downvoteCase(caseId: String): Boolean {
        return try {
            val currentCase = getCaseById(caseId) ?: return false
            val newVotes = (currentCase.votes - 1).coerceAtLeast(0)
            
            val postgrest = SupabaseManager.client.postgrest
            postgrest["repair_cases"].update(VotesUpdate(newVotes)) {
                filter { eq("id", caseId) }
            }
            
            // Update local if cached
            val local = repairCaseDao.getCaseById(caseId)
            if (local != null) {
                repairCaseDao.insertCase(local.copy(votes = newVotes))
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to downvote case", e)
            false
        }
    }

    suspend fun getCommentsForCase(caseId: String): List<RepairComment> {
        return try {
            val postgrest = SupabaseManager.client.postgrest
            postgrest["repair_comments"].select {
                filter { eq("case_id", caseId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<RepairComment>()
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to get comments", e)
            emptyList()
        }
    }

    suspend fun addComment(comment: RepairComment): Boolean {
        return try {
            val postgrest = SupabaseManager.client.postgrest
            postgrest["repair_comments"].insert(comment)
            true
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to add comment", e)
            false
        }
    }
}

fun RepairCaseEntity.toDomain() = RepairCase(
    id = id,
    vehicle_make = vehicleMake,
    vehicle_model = vehicleModel,
    year = year,
    engine = engine,
    country = country,
    dtc_code = dtcCode,
    symptoms = symptoms,
    solution = solution,
    cost = cost,
    time_spent = timeSpent,
    parts_used = partsUsed,
    verified = verified,
    votes = votes,
    success_rate = successRate,
    created_at = createdAt.toString()
)

fun RepairCase.toEntity(isBookmarked: Boolean, isMyContribution: Boolean) = RepairCaseEntity(
    id = id.ifBlank { java.util.UUID.randomUUID().toString() },
    vehicleMake = vehicle_make,
    vehicleModel = vehicle_model,
    year = year,
    engine = engine,
    country = country,
    dtcCode = dtc_code,
    symptoms = symptoms,
    solution = solution,
    cost = cost,
    timeSpent = time_spent,
    partsUsed = parts_used,
    verified = verified,
    votes = votes,
    successRate = success_rate,
    isBookmarked = isBookmarked,
    isMyContribution = isMyContribution,
    createdAt = created_at?.toLongOrNull() ?: System.currentTimeMillis()
)
