package com.elysium369.meet.data.supabase

import com.elysium369.meet.data.local.dao.RepairCaseDao
import com.elysium369.meet.data.local.dao.RepairNetworkAddonsDao
import com.elysium369.meet.data.local.entities.RepairCaseEntity
import com.elysium369.meet.data.local.entities.RepairVoteEntity
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
    private val repairCaseDao: RepairCaseDao,
    private val repairNetworkAddonsDao: RepairNetworkAddonsDao
) {
    private val defaultUserId: String
        get() = "local_user_" + android.os.Build.SERIAL.hashCode().toString(16)
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
        val remoteCases = try {
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

            response
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to search repair cases", e)
            emptyList()
        }

        val localCases = repairCaseDao.getAllCases().map { it.toDomain() }
        return (remoteCases + localCases)
            .distinctBy { it.id.ifBlank { "${it.dtc_code}:${it.vehicle_make}:${it.vehicle_model}:${it.solution.hashCode()}" } }
            .filter { repairCase ->
                val matchesQuery = query.isBlank() || run {
                    val lowerQuery = query.lowercase()
                    repairCase.vehicle_make.lowercase().contains(lowerQuery) ||
                        repairCase.vehicle_model.lowercase().contains(lowerQuery) ||
                        repairCase.dtc_code.lowercase().contains(lowerQuery) ||
                        repairCase.symptoms.lowercase().contains(lowerQuery) ||
                        repairCase.solution.lowercase().contains(lowerQuery) ||
                        repairCase.parts_used.lowercase().contains(lowerQuery)
                }
                val matchesMake = make.isBlank() || repairCase.vehicle_make.contains(make, ignoreCase = true)
                val matchesModel = model.isBlank() || repairCase.vehicle_model.contains(model, ignoreCase = true)
                val matchesDtc = dtc.isBlank() || repairCase.dtc_code.equals(dtc, ignoreCase = true)
                val matchesYear = year == null || repairCase.year == year
                val matchesCountry = country.isBlank() || repairCase.country.contains(country, ignoreCase = true)
                val matchesVerified = !onlyVerified || repairCase.verified
                matchesQuery && matchesMake && matchesModel && matchesDtc && matchesYear && matchesCountry && matchesVerified
            }
            .let { merged ->
                when (sortBy) {
                    "success_rate" -> merged.sortedWith(
                        compareByDescending<RepairCase> { it.success_rate }
                            .thenByDescending { it.votes }
                            .thenByDescending { it.created_at?.toLongOrNull() ?: 0L }
                    )
                    "date" -> merged.sortedByDescending { it.created_at?.toLongOrNull() ?: 0L }
                    else -> merged.sortedWith(
                        compareByDescending<RepairCase> { it.votes }
                            .thenByDescending { it.success_rate }
                            .thenByDescending { it.created_at?.toLongOrNull() ?: 0L }
                    )
                }
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
        repairCaseDao.insertCase(repairCase.toEntity(isBookmarked = false, isMyContribution = true))
        return try {
            val postgrest = SupabaseManager.client.postgrest
            postgrest["repair_cases"].insert(repairCase)
            true
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to insert repair case", e)
            true
        }
    }

    /**
     * Idempotent upvote: checks RepairVoteEntity to prevent duplicate votes.
     * If user already upvoted, returns false (no-op).
     * If user previously downvoted, switches the vote.
     */
    suspend fun upvoteCase(caseId: String): Boolean {
        return try {
            val userId = defaultUserId
            val existingVote = repairNetworkAddonsDao.getVoteForCaseByUser(caseId, userId)
            if (existingVote != null && existingVote.voteType == "UP") {
                // Already upvoted — idempotent no-op
                return false
            }

            val local = repairCaseDao.getCaseById(caseId) ?: return false
            val delta = if (existingVote?.voteType == "DOWN") 2 else 1 // Switching from DOWN adds 2
            val newVotes = local.votes + delta
            repairCaseDao.insertCase(local.copy(votes = newVotes))

            // Record or update the vote
            repairNetworkAddonsDao.insertVote(
                RepairVoteEntity(id = "${caseId}_${userId}", caseId = caseId, userId = userId, voteType = "UP")
            )

            runCatching {
                val postgrest = SupabaseManager.client.postgrest
                postgrest["repair_cases"].update(VotesUpdate(newVotes)) { filter { eq("id", caseId) } }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("RepairCaseRepository", "Failed to upvote case", e)
            false
        }
    }

    /**
     * Idempotent downvote: checks RepairVoteEntity to prevent duplicate votes.
     * If user already downvoted, returns false (no-op).
     * If user previously upvoted, switches the vote.
     */
    suspend fun downvoteCase(caseId: String): Boolean {
        return try {
            val userId = defaultUserId
            val existingVote = repairNetworkAddonsDao.getVoteForCaseByUser(caseId, userId)
            if (existingVote != null && existingVote.voteType == "DOWN") {
                // Already downvoted — idempotent no-op
                return false
            }

            val local = repairCaseDao.getCaseById(caseId) ?: return false
            val delta = if (existingVote?.voteType == "UP") 2 else 1 // Switching from UP subtracts 2
            val newVotes = (local.votes - delta).coerceAtLeast(0)
            repairCaseDao.insertCase(local.copy(votes = newVotes))

            // Record or update the vote
            repairNetworkAddonsDao.insertVote(
                RepairVoteEntity(id = "${caseId}_${userId}", caseId = caseId, userId = userId, voteType = "DOWN")
            )

            runCatching {
                val postgrest = SupabaseManager.client.postgrest
                postgrest["repair_cases"].update(VotesUpdate(newVotes)) { filter { eq("id", caseId) } }
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
