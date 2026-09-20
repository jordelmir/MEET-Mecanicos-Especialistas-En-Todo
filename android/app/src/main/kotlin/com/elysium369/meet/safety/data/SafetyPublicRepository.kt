package com.elysium369.meet.safety.data

import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.observability.MeetTelemetry
import com.elysium369.meet.safety.data.local.SafetyPublicCaseEntity
import com.elysium369.meet.safety.data.local.SafetyPublicClaimEntity
import com.elysium369.meet.safety.data.local.SafetyPublicDao
import com.elysium369.meet.safety.data.local.SafetyPublicPointEntity
import com.elysium369.meet.safety.data.local.SafetyPublicTimelineEntity
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import androidx.room.withTransaction
import com.elysium369.meet.data.local.MeetDatabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SafetyPublicPointWire(
    val id: String,

    val category: String,

    @SerialName("display_latitude")
    val displayLatitude: Double,

    @SerialName("display_longitude")
    val displayLongitude: Double,

    @SerialName("geo_disclosure")
    val geoDisclosure: String,

    @SerialName("location_accuracy_meters")
    val locationAccuracyMeters: Int? = null,

    val label: String,

    @SerialName("claim_state")
    val claimState: String,

    @SerialName("independent_source_count")
    val independentSourceCount: Int,

    @SerialName("civil_source_count")
    val civilSourceCount: Int = 0,

    @SerialName("journalistic_source_count")
    val journalisticSourceCount: Int = 0,

    @SerialName("public_record_source_count")
    val publicRecordSourceCount: Int = 0,

    @SerialName("documentary_source_count")
    val documentarySourceCount: Int = 0,

    @SerialName("institutional_source_count")
    val institutionalSourceCount: Int = 0,

    @SerialName("country_code")
    val countryCode: String? = null,

    @SerialName("admin1_code")
    val admin1Code: String? = null,

    @SerialName("admin2_code")
    val admin2Code: String? = null,

    @SerialName("public_h3_cell")
    val publicH3Cell: String? = null,

    @SerialName("first_documented_at")
    val firstDocumentedAt: String? = null,

    @SerialName("last_reviewed_at")
    val lastReviewedAt: String,

    @SerialName("published_at")
    val publishedAt: String,

    @SerialName("server_version")
    val serverVersion: Long,
) {
    fun toEntity(syncedAt: Long): SafetyPublicPointEntity =
        SafetyPublicPointEntity(
            publicPointId = id,
            category = category,
            displayLatitude = displayLatitude,
            displayLongitude = displayLongitude,
            geoDisclosure = geoDisclosure,
            locationAccuracyMeters = locationAccuracyMeters,
            label = label,
            claimState = claimState,
            independentSourceCount = independentSourceCount,
            civilSourceCount = civilSourceCount,
            journalisticSourceCount = journalisticSourceCount,
            publicRecordSourceCount = publicRecordSourceCount,
            documentarySourceCount = documentarySourceCount,
            institutionalSourceCount = institutionalSourceCount,
            countryCode = countryCode,
            admin1Code = admin1Code,
            admin2Code = admin2Code,
            publicH3Cell = publicH3Cell,
            firstDocumentedAt = firstDocumentedAt?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            lastReviewedAt = runCatching { Instant.parse(lastReviewedAt).toEpochMilli() }.getOrNull() ?: syncedAt,
            publishedAt = runCatching { Instant.parse(publishedAt).toEpochMilli() }.getOrNull() ?: syncedAt,
            serverVersion = serverVersion,
            syncedAt = syncedAt,
        )
}

@Serializable
data class SafetyPublicCaseWire(
    @SerialName("case_id")
    val caseId: String,

    @SerialName("case_type")
    val caseType: String,

    val title: String,

    @SerialName("public_summary")
    val publicSummary: String = "",

    val lifecycle: String,

    @SerialName("confidence_score")
    val confidenceScore: Float = 0f,

    @SerialName("event_count")
    val eventCount: Int = 0,

    @SerialName("claim_count")
    val claimCount: Int = 0,

    @SerialName("source_count")
    val sourceCount: Int = 0,

    @SerialName("evidence_count")
    val evidenceCount: Int = 0,

    @SerialName("published_at")
    val publishedAt: String,

    @SerialName("last_updated_at")
    val lastUpdatedAt: String = "",

    @SerialName("server_version")
    val serverVersion: Long = 1,
) {
    fun toEntity(): SafetyPublicCaseEntity =
        SafetyPublicCaseEntity(
            caseId = caseId,
            caseType = caseType,
            title = title,
            publicSummary = publicSummary,
            lifecycle = lifecycle,
            confidenceScore = confidenceScore,
            eventCount = eventCount,
            claimCount = claimCount,
            sourceCount = sourceCount,
            evidenceCount = evidenceCount,
            publishedAt = runCatching { Instant.parse(publishedAt).toEpochMilli() }.getOrNull() ?: 0L,
            lastUpdatedAt = runCatching { Instant.parse(lastUpdatedAt).toEpochMilli() }.getOrNull() ?: 0L,
            serverVersion = serverVersion,
        )
}

@Singleton
class SafetyPublicRepository @Inject constructor(
    private val dao: SafetyPublicDao,
    private val database: MeetDatabase,
) {

    private val refreshMutex = Mutex()

    private val client get() = SupabaseModule.client

    fun observePoints(): Flow<List<SafetyPublicPointEntity>> =
        dao.observePoints()

    fun observeCases(): Flow<List<SafetyPublicCaseEntity>> =
        dao.observeCases()

    fun observeCase(caseId: String): Flow<SafetyPublicCaseEntity?> =
        dao.observeCase(caseId)

    fun observeTimeline(caseId: String): Flow<List<SafetyPublicTimelineEntity>> =
        dao.observeTimeline(caseId)

    fun observeClaims(caseId: String): Flow<List<SafetyPublicClaimEntity>> =
        dao.observeClaims(caseId)

    /** Fetch all pages before changing the cache; failed refreshes retain offline data. */
    suspend fun refreshPoints() = refreshMutex.withLock {
        val owner = requireSession()
        val remote = pages { start, end ->
            client.postgrest["safety_public_points"].select {
                order("id", Order.ASCENDING)
                range(start, end)
            }.decodeList<SafetyPublicPointWire>()
        }
        check(requireSession() == owner)
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.removeMissingPoints(remote.map { it.id })
            for (row in remote) {
                require(row.serverVersion > 0)
                val old = dao.getPoint(row.id)
                if (old == null || row.serverVersion > old.serverVersion) {
                    dao.upsertPoint(row.toEntity(now))
                }
            }
        }
        recordRefresh("public_points", remote.size)
    }

    suspend fun refreshCases() = refreshMutex.withLock {
        val owner = requireSession()
        val remote = pages { start, end ->
            client.postgrest["safety_public_case_projection"].select {
                order("case_id", Order.ASCENDING)
                range(start, end)
            }.decodeList<SafetyPublicCaseWire>()
        }
        check(requireSession() == owner)
        database.withTransaction {
            dao.removeMissingCases(remote.map { it.caseId })
            dao.removeOrphanTimeline()
            dao.removeOrphanClaims()
            for (row in remote) {
                require(row.serverVersion > 0)
                val old = dao.getCase(row.caseId)
                if (old == null || row.serverVersion > old.serverVersion) {
                    dao.upsertCases(listOf(row.toEntity()))
                }
            }
        }
        recordRefresh("public_cases", remote.size)
    }

    suspend fun refreshCaseDetail(caseId: String) = refreshMutex.withLock {
        val owner = requireSession()
        val cases = client.postgrest["safety_public_case_projection"].select {
            filter { eq("case_id", caseId) }
        }.decodeList<SafetyPublicCaseWire>()
        val timeline = pages { start, end ->
            client.postgrest["safety_public_case_timeline_projection"].select {
                filter { eq("case_id", caseId) }
                order("milestone_id", Order.ASCENDING)
                range(start, end)
            }.decodeList<SafetyPublicTimelineWire>()
        }
        val claims = pages { start, end ->
            client.postgrest["safety_public_case_claim_projection"].select {
                filter { eq("case_id", caseId) }
                order("claim_id", Order.ASCENDING)
                range(start, end)
            }.decodeList<SafetyPublicClaimWire>()
        }
        check(requireSession() == owner)
        database.withTransaction {
            val row = cases.singleOrNull()
            dao.clearTimeline(caseId)
            dao.clearClaims(caseId)
            if (row == null) {
                dao.deleteCase(caseId)
            } else {
                require(row.serverVersion > 0)
                val old = dao.getCase(caseId)
                if (old == null || row.serverVersion >= old.serverVersion) dao.upsertCases(listOf(row.toEntity()))
                dao.upsertTimeline(timeline.map { it.toEntity() })
                dao.upsertClaims(claims.map { it.toEntity() })
            }
        }
    }

    /** Events only wake an authenticated full refresh, including reconnects and version gaps. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun realtimeWakeUps(): Flow<Unit> = flow {
        val owner = requireSession()
        val channel = client.channel("safety-public-$owner-${UUID.randomUUID()}")
        val changes = listOf("safety_public_points", "safety_public_case_projection",
            "safety_public_case_timeline_projection", "safety_public_case_claim_projection")
            .map { name -> channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = name
            }.map { Unit } }
        try {
            channel.subscribe()
            emit(Unit)
            emitAll(merge(*changes.toTypedArray()))
        } finally {
            withContext(NonCancellable) { withTimeoutOrNull(5_000) { channel.unsubscribe() } }
        }
    }.retryWhen { error, _ ->
        if (error is CancellationException) throw error
        delay(5_000)
        true
    }

    private fun requireSession(): String = checkNotNull(client.auth.currentUserOrNull()?.id)

    private suspend fun <T> pages(fetch: suspend (Long, Long) -> List<T>): List<T> {
        val rows = mutableListOf<T>()
        var start = 0L
        do {
            val page = fetch(start, start + 499)
            rows.addAll(page)
            start += page.size
        } while (page.size == 500)
        return rows
    }

    private fun recordRefresh(operation: String, count: Int) = MeetTelemetry.event(
        "safety.public.refresh", mapOf("operation" to operation, "resultCode" to "OK", "itemCount" to count),
    )

}
