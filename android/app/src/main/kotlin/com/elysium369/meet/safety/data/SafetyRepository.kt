package com.elysium369.meet.safety.data

import android.content.Context
import com.elysium369.meet.data.remote.SupabaseModule
import androidx.room.withTransaction
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.safety.crypto.SafetyDigest
import com.elysium369.meet.safety.crypto.SafetyPayloadAad
import com.elysium369.meet.safety.crypto.AeadBlob
import com.elysium369.meet.safety.crypto.SafetyPayloadCipher
import com.elysium369.meet.safety.data.local.SafetyCommandOutboxDao
import com.elysium369.meet.safety.data.local.SafetyCommandOutboxEntity
import com.elysium369.meet.safety.data.local.SafetyLocalPayloadEntity
import com.elysium369.meet.safety.data.local.SafetyPayloadDao
import com.elysium369.meet.safety.data.local.SafetyReportDao
import com.elysium369.meet.safety.data.local.SafetyReportEntity
import com.elysium369.meet.safety.domain.CreateSafetyReportPayload
import com.elysium369.meet.safety.domain.SafetyCommandType
import com.elysium369.meet.safety.domain.SafetyOutboxStatus
import com.elysium369.meet.safety.domain.SafetyChallengeKind
import com.elysium369.meet.safety.domain.SafetyChallengePayload
import com.elysium369.meet.safety.work.SafetyCommandScheduler
import com.elysium369.meet.safety.work.SafetyEvidenceScheduler
import com.elysium369.meet.safety.evidence.SafetyEvidenceDao
import kotlinx.coroutines.flow.flatMapLatest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import com.elysium369.meet.safety.data.remote.SafetyCommandGateway
import com.elysium369.meet.safety.domain.SafetyGatewayResult
import javax.inject.Provider
import kotlinx.serialization.SerialName

@Singleton
class SafetyRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val database: MeetDatabase,
    private val reportDao: SafetyReportDao,
    private val payloadDao: SafetyPayloadDao,
    private val outboxDao: SafetyCommandOutboxDao,
    private val principalKernel: ActivePrincipalKernel,
    private val cipher: SafetyPayloadCipher,
    private val evidenceDao: SafetyEvidenceDao,
    private val gateway: SafetyCommandGateway,
    private val publicRepository: Provider<SafetyPublicRepository>,
    private val evidenceRepository: Provider<com.elysium369.meet.safety.evidence.SafetyEvidenceRepository>,
    private val updateDao: com.elysium369.meet.safety.data.local.SafetyReportUpdateDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    suspend fun createReport(
        payload: CreateSafetyReportPayload,
        reportId: String = UUID.randomUUID().toString(),
        evidenceIds: List<String> = emptyList(),
    ): String {
        validate(payload)

        val principal = principalKernel.current()
        require(runCatching { UUID.fromString(principal.id) }.isSuccess) {
            "SAFETY_AUTHENTICATED_PRINCIPAL_REQUIRED"
        }

        UUID.fromString(reportId)
        val payloadId = UUID.randomUUID().toString()
        val idempotencyKey = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val canonical = json.encodeToString(CreateSafetyReportPayload.serializer(), payload)
            .toByteArray(Charsets.UTF_8)

        val aad = SafetyPayloadAad.report(
            principalId = principal.id,
            reportId = reportId,
            payloadId = payloadId,
        )
        val encrypted = cipher.encryptAead(plaintext = canonical, associatedData = aad)
        val digest = SafetyDigest.reportPayload(canonical)

        val occurredAtMs = payload.occurredAtIso?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        }

        try {
            database.withTransaction {
                check(principalKernel.current().id == principal.id) { "SAFETY_PRINCIPAL_CHANGED" }
                evidenceIds.forEach { id ->
                    val evidence = evidenceDao.get(id, principal.id)
                    check(evidence != null && evidence.reportId == reportId && evidence.uploadState == "STAGED") { "SAFETY_EVIDENCE_NOT_STAGED" }
                }
                payloadDao.insertOrThrow(
                    SafetyLocalPayloadEntity(
                        payloadId = payloadId,
                        ciphertext = encrypted.toWire(),
                        sha256 = digest,
                        createdAt = now,
                        redactedAt = null,
                    ),
                )

                reportDao.insertOrThrow(
                    SafetyReportEntity(
                        reportId = reportId,
                        ownerUserId = principal.id,
                        category = payload.category.name,
                        payloadId = payloadId,
                        occurredAt = occurredAtMs,
                        localState = "LOCAL_ONLY",
                        serverState = null,
                        serverVersion = 0,
                        syncState = "QUEUED",
                        createdAt = now,
                        updatedAt = now,
                    ),
                )

                outboxDao.insertOrThrow(
                    SafetyCommandOutboxEntity(
                        idempotencyKey = idempotencyKey,
                        aggregateId = reportId,
                        actorSessionUserId = principal.id,
                        commandType = SafetyCommandType.CREATE_REPORT.name,
                        expectedVersion = 0,
                        payloadVersion = 1,
                        payloadId = payloadId,
                        clientPayloadSha256 = digest,
                        status = SafetyOutboxStatus.PENDING.name,
                        attemptCount = 0,
                        nextAttemptAt = now,
                        leaseStartedAt = null,
                        lastErrorCode = null,
                        lastErrorMessage = null,
                        correlationId = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }

        SafetyCommandScheduler.enqueueNow(context)
        SafetyEvidenceScheduler.enqueue(context)

        // Immediate online synchronization & public projections auto-refresh
        try {
            val command = outboxDao.get(idempotencyKey)
            if (command != null) {
                val payloadString = canonical.decodeToString()
                val result = gateway.execute(command, payloadString)
                android.util.Log.i("ElysiumSafetySync", "Gateway result for $reportId: $result")
                when (result) {
                    is SafetyGatewayResult.Accepted -> {
                        val completedAt = System.currentTimeMillis()
                        reportDao.applyServerAcknowledgement(
                            reportId = reportId,
                            serverState = result.state,
                            serverVersion = result.serverVersion,
                            now = completedAt,
                        )
                        outboxDao.markAcknowledgedDirect(
                            key = idempotencyKey,
                            correlationId = result.correlationId,
                            now = completedAt,
                        )
                        // Auto-refresh public projections so all Safety sections update immediately
                        runCatching { publicRepository.get().refreshPoints() }
                        runCatching { publicRepository.get().refreshCases() }
                        android.util.Log.i("ElysiumSafetySync", "Report $reportId SYNCED ONLINE successfully, serverVersion=${result.serverVersion}")
                    }
                    is SafetyGatewayResult.Rejected -> {
                        android.util.Log.e("ElysiumSafetySync", "Report $reportId REJECTED by server: code=${result.code} message=${result.message} retryable=${result.retryable}")
                    }
                    is SafetyGatewayResult.TransportFailure -> {
                        android.util.Log.e("ElysiumSafetySync", "Report $reportId TRANSPORT FAILURE: code=${result.code} message=${result.message}")
                    }
                }
            } else {
                android.util.Log.w("ElysiumSafetySync", "Outbox command not found for key=$idempotencyKey")
            }
        } catch (e: Exception) {
            android.util.Log.e("ElysiumSafetySync", "Immediate sync failed for report $reportId, WorkManager will retry", e)
        }

        return reportId
    }

    suspend fun submitCounterclaim(
        caseId: String,
        claimId: String?,
        kind: SafetyChallengeKind,
        narrative: String,
        sourceUrl: String?,
    ): String {
        UUID.fromString(caseId)
        claimId?.let(UUID::fromString)
        val normalizedNarrative = narrative.trim()
        require(normalizedNarrative.length in 10..5_000) { "SAFETY_INVALID_CHALLENGE_LENGTH" }
        val normalizedUrl = sourceUrl?.trim()?.takeIf(String::isNotEmpty)
        normalizedUrl?.let {
            val uri = android.net.Uri.parse(it)
            require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()) {
                "SAFETY_INVALID_SOURCE_URL"
            }
        }
        val principal = principalKernel.current()
        require(runCatching { UUID.fromString(principal.id) }.isSuccess) {
            "SAFETY_AUTHENTICATED_PRINCIPAL_REQUIRED"
        }
        val payload = SafetyChallengePayload(caseId, claimId, kind, normalizedNarrative, normalizedUrl)
        val payloadId = UUID.randomUUID().toString()
        val commandId = UUID.randomUUID().toString()
        val bytes = json.encodeToString(SafetyChallengePayload.serializer(), payload).toByteArray()
        val digest = SafetyDigest.reportPayload(bytes)
        val encrypted = cipher.encryptAead(
            bytes,
            SafetyPayloadAad.report(principal.id, caseId, payloadId),
        )
        val now = System.currentTimeMillis()
        database.withTransaction {
            check(principalKernel.current().id == principal.id) { "SAFETY_PRINCIPAL_CHANGED" }
            payloadDao.insertOrThrow(SafetyLocalPayloadEntity(payloadId, encrypted.toWire(), digest, now, null))
            outboxDao.insertOrThrow(
                SafetyCommandOutboxEntity(
                    commandId, caseId, principal.id, SafetyCommandType.SUBMIT_COUNTERCLAIM.name,
                    0, 1, payloadId, digest, SafetyOutboxStatus.PENDING.name, 0, now,
                    null, null, null, null, now, now,
                ),
            )
        }
        SafetyCommandScheduler.enqueueNow(context)
        return commandId
    }

    fun observeReport(reportId: String): Flow<SafetyReportEntity?> = reportDao.observeById(reportId)

    suspend fun refreshMyReports(): Result<Unit> = runCatching {
        val principal = principalKernel.current()
        if (runCatching { UUID.fromString(principal.id) }.isFailure) return@runCatching
        val rows = SupabaseModule.client.postgrest["safety_reports"]
            .select {
                filter {
                    eq("reporter_user_id", principal.id)
                    neq("state", "WITHDRAWN")
                }
            }
            .decodeList<RemoteSafetyReportRow>()

        val entities = rows.map { row ->
            val occurredMs = row.occurredAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            val createdMs = row.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: System.currentTimeMillis()
            val updatedMs = row.updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: createdMs
            SafetyReportEntity(
                reportId = row.id,
                ownerUserId = row.reporterUserId,
                category = row.category,
                payloadId = row.id,
                occurredAt = occurredMs,
                localState = "SYNCED_ONLINE",
                serverState = row.state,
                serverVersion = row.stateVersion,
                syncState = "SYNCED",
                createdAt = createdMs,
                updatedAt = updatedMs,
            )
        }
        val activeRemoteIds = rows.map { it.id }.toSet()
        val publicDao = database.safetyPublicDao()
        database.withTransaction {
            val now = System.currentTimeMillis()
            val localReports = reportDao.listByUser(principal.id)
            for (local in localReports) {
                if (local.syncState == "SYNCED" && !activeRemoteIds.contains(local.reportId)) {
                    payloadDao.redact(local.payloadId, now)
                    reportDao.deleteOwned(local.reportId, principal.id)
                    publicDao.deletePoint(local.reportId)
                    publicDao.deleteCase(local.reportId)
                    publicDao.clearTimeline(local.reportId)
                    publicDao.clearClaims(local.reportId)
                    evidenceDao.deleteByReportId(local.reportId, principal.id)
                }
            }
            if (entities.isNotEmpty()) {
                reportDao.upsertAll(entities)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeMyReports(): Flow<List<SafetyReportEntity>> = principalKernel.activePrincipal.flatMapLatest { principal ->
        reportDao.observeByUser(principal.id)
    }

    /** Exact coordinates remain encrypted at rest and are exposed only to their local owner. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeMyPrivateMapPoints(): Flow<List<SafetyPrivateMapPoint>> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            reportDao.observePrivateMapRows(principal.id).mapLatest { rows ->
                rows.mapNotNull { row ->
                    runCatching {
                        val bytes = cipher.decryptAead(
                            AeadBlob.fromWire(row.ciphertext),
                            SafetyPayloadAad.report(row.ownerUserId, row.reportId, row.payloadId),
                        )
                        check(SafetyDigest.verify(bytes, row.payloadSha256))
                        val payload = json.decodeFromString(CreateSafetyReportPayload.serializer(), bytes.decodeToString())
                        val latitude = payload.latitude ?: return@runCatching null
                        val longitude = payload.longitude ?: return@runCatching null
                        SafetyPrivateMapPoint(
                            reportId = row.reportId,
                            category = row.category,
                            latitude = latitude,
                            longitude = longitude,
                            accuracyMeters = payload.accuracyMeters,
                            occurredAt = row.occurredAt ?: row.createdAt,
                            serverState = row.serverState,
                            syncState = row.syncState,
                            serverVersion = row.serverVersion,
                            narrative = payload.narrative,
                            sourceRelation = payload.sourceRelation.name,
                            locationSource = payload.locationSource.name,
                            victimCount = payload.reportedVictimCount,
                            victimFemale = payload.reportedVictimFemale,
                            victimMale = payload.reportedVictimMale,
                            createdAt = row.createdAt,
                            videoUrls = payload.videoUrls,
                        )
                    }.getOrNull()
                }
            }
        }

    fun resumePendingUploads() {
        SafetyCommandScheduler.enqueueNow(context)
        SafetyEvidenceScheduler.enqueue(context)
    }

    suspend fun listReports(): List<SafetyReportEntity> {
        val principal = principalKernel.current()
        return reportDao.listByUser(principal.id)
    }

    suspend fun pendingCount(): Int {
        val principal = principalKernel.current()
        return outboxDao.pendingCount(principal.id)
    }

    suspend fun totalReportCount(): Int {
        val principal = principalKernel.current()
        return reportDao.countByUser(principal.id)
    }

    /** Owner-authorized withdrawal. Server redacts private content and cascades removal from all public projections. */
    suspend fun withdrawReport(reportId: String) {
        UUID.fromString(reportId)
        val principal = principalKernel.current()
        val report = checkNotNull(reportDao.get(reportId)) { "SAFETY_REPORT_NOT_FOUND" }
        check(report.ownerUserId == principal.id) { "SAFETY_REPORT_NOT_OWNED" }
        if (report.syncState == "SYNCED" || report.serverVersion > 0L) {
            val response = SupabaseModule.client.postgrest.rpc(
                "safety_withdraw_report_v1",
                buildJsonObject {
                    put("p_report_id", reportId)
                    put("p_idempotency_key", UUID.randomUUID().toString())
                },
            ).decodeAs<JsonObject>()
            check(response["state"]?.jsonPrimitive?.contentOrNull == "WITHDRAWN") { "SAFETY_WITHDRAWAL_NOT_ACKNOWLEDGED" }
        }
        val publicDao = database.safetyPublicDao()
        database.withTransaction {
            val now = System.currentTimeMillis()
            if (report.syncState != "SYNCED") outboxDao.cancelPendingReport(reportId, principal.id, now)
            payloadDao.redact(report.payloadId, now)
            check(reportDao.deleteOwned(reportId, principal.id) == 1)
            publicDao.deletePoint(reportId)
            publicDao.deleteCase(reportId)
            publicDao.clearTimeline(reportId)
            publicDao.clearClaims(reportId)
            evidenceDao.deleteByReportId(reportId, principal.id)
        }
        evidenceRepository.get().clearReportLocalEvidence(reportId, principal.id)
        publicRepository.get().evictReport(reportId)
    }

    suspend fun reconcileMyReports() {
        val principal = principalKernel.current()
        val remote = SupabaseModule.client.postgrest["safety_reports"].select {
            filter { eq("reporter_user_id", principal.id) }
        }.decodeList<SafetyRemoteReportState>()
        val remoteById = remote.associateBy { it.id }
        val publicDao = database.safetyPublicDao()
        database.withTransaction {
            reportDao.listByUser(principal.id).forEach { local ->
                val authoritative = remoteById[local.reportId]
                if (authoritative?.state == "WITHDRAWN") {
                    payloadDao.redact(local.payloadId, System.currentTimeMillis())
                    reportDao.deleteOwned(local.reportId, principal.id)
                    updateDao.deleteForReport(local.reportId)
                    publicDao.deletePoint(local.reportId)
                    publicDao.deleteCase(local.reportId)
                    publicDao.clearTimeline(local.reportId)
                    publicDao.clearClaims(local.reportId)
                    evidenceDao.deleteByReportId(local.reportId, principal.id)
                }
            }
        }
    }

    /**
     * Agrega una actualización o nuevo avistamiento inmutable a un reporte existente.
     * Principio Append-Only: No altera registros previos.
     * Restricción: Videos exclusivamente como URLs externas (videoUrls).
     * Límite narrativo: Hasta 100,000 caracteres para expedientes exhaustivos.
     */
    suspend fun addReportUpdate(
        reportId: String,
        occurredAt: Long,
        locationLabel: String,
        clothingAndFeatures: String,
        narrative: String,
        videoUrls: List<String>,
        latitude: Double? = null,
        longitude: Double? = null,
        attachmentsCount: Int = 0,
    ): com.elysium369.meet.safety.data.local.SafetyReportUpdateEntity {
        val cleanNarrative = narrative.trim()
        require(cleanNarrative.isNotBlank() && cleanNarrative.length <= 100_000) {
            "SAFETY_INVALID_UPDATE_NARRATIVE_LENGTH"
        }
        val cleanLocation = locationLabel.trim().ifBlank { "Ubicación del reporte" }
        val cleanFeatures = clothingAndFeatures.trim()
        val validatedVideoUrls = videoUrls.map { it.trim() }.filter { it.isNotBlank() }.map {
            if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }.distinct()

        val now = System.currentTimeMillis()
        val updateId = UUID.randomUUID().toString()
        val entity = com.elysium369.meet.safety.data.local.SafetyReportUpdateEntity(
            updateId = updateId,
            reportId = reportId,
            occurredAt = occurredAt,
            recordedAt = now,
            locationLabel = cleanLocation,
            latitude = latitude,
            longitude = longitude,
            clothingAndFeatures = cleanFeatures,
            narrative = cleanNarrative,
            videoUrlsJson = com.elysium369.meet.safety.data.local.SafetyReportUpdateEntity.encodeVideoUrls(validatedVideoUrls),
            syncState = "SYNCED",
            serverVersion = 1L,
            createdAt = now,
        )

        database.withTransaction {
            updateDao.insert(entity)
            reportDao.touchUpdatedAt(reportId, now)
            // Proyectar de inmediato en la línea de tiempo pública del caso para visibilidad de toda la comunidad
            runCatching {
                val publicTimeline = com.elysium369.meet.safety.data.local.SafetyPublicTimelineEntity(
                    caseId = reportId,
                    milestoneId = updateId,
                    eventType = "SIGHTING_UPDATE",
                    publicSummary = "[$cleanLocation] $cleanFeatures: $cleanNarrative".take(1000),
                    occurredAt = occurredAt,
                    recordedAt = now,
                    sourceCount = 1,
                    evidenceCount = validatedVideoUrls.size + attachmentsCount,
                    serverVersion = 1L,
                )
                database.safetyPublicDao().upsertTimeline(listOf(publicTimeline))
            }
        }

        // Auto-refrescar proyecciones públicas
        runCatching { publicRepository.get().refreshPoints() }
        runCatching { publicRepository.get().refreshCases() }

        return entity
    }

    fun observeReportUpdates(reportId: String): Flow<List<com.elysium369.meet.safety.data.local.SafetyReportUpdateEntity>> =
        updateDao.observeUpdatesForReport(reportId)

    fun observeAllReportUpdates(): Flow<List<com.elysium369.meet.safety.data.local.SafetyReportUpdateEntity>> =
        updateDao.observeAllUpdates()

    private fun validate(payload: CreateSafetyReportPayload) {
        require(payload.narrative.trim().length in 10..100_000) {
            "SAFETY_INVALID_NARRATIVE_LENGTH"
        }
        payload.occurredAtIso?.let { require(runCatching { Instant.parse(it) }.isSuccess) { "SAFETY_INVALID_OCCURRED_AT" } }
        payload.latitude?.let {
            require(it in -90.0..90.0) { "SAFETY_INVALID_LATITUDE" }
        }
        payload.longitude?.let {
            require(it in -180.0..180.0) { "SAFETY_INVALID_LONGITUDE" }
        }
        payload.accuracyMeters?.let { require(it.isFinite() && it > 0) { "SAFETY_INVALID_ACCURACY" } }
        require((payload.latitude == null) == (payload.longitude == null)) {
            "SAFETY_PARTIAL_LOCATION"
        }
    }
}

data class SafetyPrivateMapPoint(
    val reportId: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val occurredAt: Long,
    val serverState: String?,
    val syncState: String,
    val serverVersion: Long = 0,
    val narrative: String = "",
    val sourceRelation: String = "UNKNOWN",
    val locationSource: String = "NONE",
    val victimCount: Int? = null,
    val victimFemale: Int? = null,
    val victimMale: Int? = null,
    val createdAt: Long = occurredAt,
    val videoUrls: List<String> = emptyList(),
) {
    val markerId: String get() = "private:$reportId"
}

@Serializable
private data class SafetyRemoteReportState(
    val id: String,
    val state: String,
    @SerialName("state_version") val stateVersion: Long,
)

@Serializable
private data class RemoteSafetyReportRow(
    val id: String,
    @SerialName("reporter_user_id") val reporterUserId: String,
    val category: String,
    val state: String = "RECEIVED",
    @SerialName("state_version") val stateVersion: Long = 1,
    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
