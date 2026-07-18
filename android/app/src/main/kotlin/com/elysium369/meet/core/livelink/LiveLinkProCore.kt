package com.elysium369.meet.core.livelink

import com.elysium369.meet.core.obd.ObdDataSource
import com.elysium369.meet.core.obd.TelemetrySample
import com.elysium369.meet.core.obd.TelemetryQuality
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

enum class LiveLinkMode {
    LOCAL_WIFI,
    REMOTE_READ_ONLY,
    REMOTE_ASSISTED,
    REMOTE_CONTROLLED_PRO,
    PERITO_MODE,
    FLEET_MODE,
}

enum class LiveLinkSessionState {
    DRAFT,
    CREATED,
    WAITING_REMOTE_JOIN,
    REMOTE_CONNECTED,
    ACTIVE,
    DEGRADED,
    PAUSED,
    EXPIRED,
    REVOKED,
    COMPLETED,
    ERROR,
}

enum class LiveLinkActorRole {
    OWNER,
    REMOTE_MECHANIC,
    PERITO,
    FLEET_MANAGER,
    SYSTEM,
}

enum class LiveLinkEventType {
    SESSION_CREATED,
    TOKEN_REGENERATED,
    REMOTE_JOINED,
    PERMISSION_CHANGED,
    TELEMETRY_SENT,
    CHAT_MESSAGE,
    SNAPSHOT_REQUESTED,
    SNAPSHOT_CAPTURED,
    REMOTE_REQUEST_CREATED,
    REMOTE_REQUEST_APPROVED,
    REMOTE_REQUEST_DENIED,
    CRITICAL_REQUEST_BLOCKED,
    SESSION_PAUSED,
    SESSION_REVOKED,
    SESSION_EXPIRED,
    SESSION_COMPLETED,
    REPORT_GENERATED,
    TRANSPORT_DEGRADED,
    SECURITY_WARNING,
}

enum class LiveLinkMessageType {
    TEXT,
    IMAGE,
    NOTE,
    COMMAND_SUGGESTION,
    CHECKLIST,
    SNAPSHOT,
    DTC,
    GRAPH,
}

enum class LiveLinkRemoteRequestType {
    CAPTURE_SNAPSHOT,
    READ_DTCS,
    READ_FREEZE_FRAME,
    START_LIVE_PIDS,
    STOP_LIVE_PIDS,
    CLEAR_DTCS,
    RUN_ACTIVE_TEST,
    RUN_SERVICE_RESET,
    GENERATE_REPORT,
    ADD_PHOTO,
    START_CAMERA,
}

enum class LiveLinkRequestStatus {
    PENDING_LOCAL_APPROVAL,
    APPROVED,
    DENIED,
    BLOCKED,
    EXPIRED,
    COMPLETED,
}

enum class LiveLinkRiskLevel {
    READ_ONLY,
    EVIDENCE_CAPTURE,
    PRIVACY,
    CRITICAL_CONTROL,
}

enum class LiveLinkTelemetryQuality {
    VALID,
    STALE,
    UNSUPPORTED,
    TIMEOUT,
    PARSE_ERROR,
    OUT_OF_RANGE,
    MANUAL,
    SIMULATED,
}

enum class LiveLinkDataSource {
    REAL_OBD,
    OFFLINE_KNOWLEDGE,
    SIMULATED_DEMO,
    MANUAL_INPUT,
    NO_REAL_OBD,
}

enum class LiveLinkSourceQuality {
    REAL_OBD,
    STALE_OBD,
    NO_REAL_OBD,
    MANUAL,
    SIMULATED,
    MIXED,
}

enum class LiveLinkTransportType {
    LOCAL_HTTP_SERVER,
    WEBSOCKET,
    SUPABASE_REALTIME,
    WEBRTC_DATA_CHANNEL,
}

@Serializable
data class LiveLinkSession(
    val sessionId: String,
    val ownerUserId: String,
    val vehicleId: String,
    val providerId: String? = null,
    val mode: LiveLinkMode,
    val title: String? = null,
    val state: LiveLinkSessionState,
    val accessTokenHash: String,
    val readOnly: Boolean,
    val chatEnabled: Boolean,
    val videoEnabled: Boolean,
    val audioEnabled: Boolean,
    val cameraEnabled: Boolean,
    val locationEnabled: Boolean,
    val historyEnabled: Boolean,
    val reportsEnabled: Boolean,
    val dtcEnabled: Boolean,
    val livePidsEnabled: Boolean,
    val createdAtMs: Long,
    val startedAtMs: Long? = null,
    val expiresAtMs: Long,
    val revokedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val updatedAtMs: Long = createdAtMs,
) {
    val isOpen: Boolean
        get() = state in setOf(
            LiveLinkSessionState.CREATED,
            LiveLinkSessionState.WAITING_REMOTE_JOIN,
            LiveLinkSessionState.REMOTE_CONNECTED,
            LiveLinkSessionState.ACTIVE,
            LiveLinkSessionState.DEGRADED,
            LiveLinkSessionState.PAUSED,
        )

    fun timeRemainingMs(nowMs: Long): Long = (expiresAtMs - nowMs).coerceAtLeast(0L)
}

@Serializable
data class LiveLinkPermission(
    val sessionId: String,
    val canReadObdState: Boolean = true,
    val canReadDtc: Boolean = true,
    val canReadFreezeFrame: Boolean = true,
    val canReadLivePids: Boolean = true,
    val canReadReports: Boolean = true,
    val canUseChat: Boolean = true,
    val canRequestSnapshot: Boolean = true,
    val canUseVideo: Boolean = false,
    val canUseAudio: Boolean = false,
    val canUseCamera: Boolean = false,
    val canReadVehicleHistory: Boolean = false,
    val canReadApproxLocation: Boolean = false,
    val canReadExactLocation: Boolean = false,
    val canSeeFullVin: Boolean = false,
    val canSeeFullPlate: Boolean = false,
    val canClearDtcs: Boolean = false,
    val canRunActiveTests: Boolean = false,
    val canRunServiceResets: Boolean = false,
)

@Serializable
data class LiveLinkTelemetrySample(
    val pid: String,
    val name: String,
    val value: Double?,
    val unit: String,
    val quality: LiveLinkTelemetryQuality,
    val timestampMs: Long,
    val latencyMs: Long,
    val source: LiveLinkDataSource,
)

@Serializable
data class LiveLinkTelemetryPacket(
    val packetId: String,
    val sessionId: String,
    val timestampMs: Long,
    val connectionState: String,
    val adapterQuality: String,
    val sourceQuality: LiveLinkSourceQuality,
    val samples: List<LiveLinkTelemetrySample>,
    val activeDtcs: List<String>,
    val freezeFrameAvailable: Boolean,
    val degradedReason: String? = null,
) {
    val hasRealObdEvidence: Boolean
        get() = sourceQuality == LiveLinkSourceQuality.REAL_OBD ||
            samples.any { it.source == LiveLinkDataSource.REAL_OBD && it.quality == LiveLinkTelemetryQuality.VALID && it.value != null }
}

@Serializable
data class LiveLinkEvent(
    val eventId: String,
    val sessionId: String,
    val type: LiveLinkEventType,
    val actorRole: LiveLinkActorRole,
    val actorId: String? = null,
    val message: String,
    val createdAtMs: Long,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class LiveLinkChatMessage(
    val messageId: String,
    val sessionId: String,
    val authorRole: LiveLinkActorRole,
    val authorId: String? = null,
    val type: LiveLinkMessageType = LiveLinkMessageType.TEXT,
    val body: String,
    val createdAtMs: Long,
    val attachmentUrl: String? = null,
)

@Serializable
data class LiveLinkRemoteRequest(
    val requestId: String,
    val sessionId: String,
    val type: LiveLinkRemoteRequestType,
    val requestedByRole: LiveLinkActorRole,
    val requestedById: String? = null,
    val status: LiveLinkRequestStatus,
    val riskLevel: LiveLinkRiskLevel,
    val requiresLocalApproval: Boolean,
    val requiresDoubleConfirmation: Boolean,
    val reason: String,
    val createdAtMs: Long,
    val resolvedAtMs: Long? = null,
)

@Serializable
data class LiveLinkSnapshot(
    val snapshotId: String,
    val sessionId: String,
    val capturedAtMs: Long,
    val telemetryPacket: LiveLinkTelemetryPacket,
    val notes: String = "",
)

@Serializable
data class LiveLinkReport(
    val reportId: String,
    val sessionId: String,
    val createdAtMs: Long,
    val startedAtMs: Long?,
    val endedAtMs: Long?,
    val mode: LiveLinkMode,
    val state: LiveLinkSessionState,
    val activeDtcs: List<String>,
    val snapshotCount: Int,
    val eventCount: Int,
    val remoteRequestCount: Int,
    val sourceQuality: LiveLinkSourceQuality,
    val evidenceHash: String,
)

@Serializable
data class LiveLinkQrPayload(
    val sessionId: String,
    val accessToken: String,
    val expiresAtMs: Long,
    val mode: LiveLinkMode,
)

@Serializable
data class LiveLinkAccessCredentials(
    val accessToken: String,
    val accessTokenHash: String,
    val displayCode: String,
    val expiresAtMs: Long,
    val shareUrl: String,
    val qrPayload: LiveLinkQrPayload,
)

@Serializable
data class LiveLinkShareEnvelope(
    val session: LiveLinkSession,
    val permissions: LiveLinkPermission,
    val credentials: LiveLinkAccessCredentials,
    val createdEvent: LiveLinkEvent,
)

@Serializable
data class LiveLinkAnalyticsEvent(
    val name: String,
    val sessionId: String,
    val mode: LiveLinkMode,
    val state: LiveLinkSessionState,
    val createdAtMs: Long,
    val durationMinutes: Int? = null,
    val sourceQuality: LiveLinkSourceQuality? = null,
)

data class CreateLiveLinkSessionInput(
    val ownerUserId: String,
    val vehicleId: String,
    val providerId: String? = null,
    val mode: LiveLinkMode = LiveLinkMode.REMOTE_READ_ONLY,
    val title: String? = null,
    val durationMinutes: Int = 30,
    val readOnly: Boolean = true,
    val allowVideo: Boolean = false,
    val allowAudio: Boolean = false,
    val allowCamera: Boolean = false,
    val allowLocation: Boolean = false,
    val allowVehicleHistory: Boolean = false,
    val allowReports: Boolean = true,
    val allowDtc: Boolean = true,
    val allowLivePids: Boolean = true,
)

data class RemoteRequestDecision(
    val allowed: Boolean,
    val riskLevel: LiveLinkRiskLevel,
    val requiresLocalApproval: Boolean,
    val requiresDoubleConfirmation: Boolean,
    val reason: String,
)

data class RemoteRequestResult(
    val request: LiveLinkRemoteRequest,
    val event: LiveLinkEvent,
)

interface LiveLinkTransport {
    val type: LiveLinkTransportType

    suspend fun sendTelemetry(packet: LiveLinkTelemetryPacket): Boolean
    suspend fun sendEvent(event: LiveLinkEvent): Boolean
    suspend fun close()
}

object LiveLinkBackendContract {
    val requiredTables: List<String> = listOf(
        "live_link_sessions",
        "live_link_permissions",
        "live_link_events",
        "live_link_messages",
        "live_link_remote_requests",
        "live_link_snapshots",
        "live_link_reports",
    )

    val analyticsForbiddenFields: Set<String> = setOf(
        "vin",
        "plate",
        "exact_location",
        "chat_body",
        "api_key",
        "access_token",
    )
}

object LiveLinkTokenService {
    private const val TOKEN_LENGTH = 32
    private const val DISPLAY_CODE_LENGTH = 6
    private const val TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    private val secureRandom = SecureRandom()

    fun createCredentials(
        sessionId: String,
        mode: LiveLinkMode,
        durationMinutes: Int,
        nowMs: Long,
        baseUrl: String = "https://meet.elysium369.com/livelink",
    ): LiveLinkAccessCredentials {
        val token = generateAccessToken()
        val expiresAtMs = nowMs + durationMinutes.coerceIn(5, 240) * 60_000L
        val qrPayload = LiveLinkQrPayload(
            sessionId = sessionId,
            accessToken = token,
            expiresAtMs = expiresAtMs,
            mode = mode,
        )
        return LiveLinkAccessCredentials(
            accessToken = token,
            accessTokenHash = hashToken(token),
            displayCode = generateDisplayCode(),
            expiresAtMs = expiresAtMs,
            shareUrl = buildShareUrl(baseUrl, qrPayload),
            qrPayload = qrPayload,
        )
    }

    fun generateAccessToken(length: Int = TOKEN_LENGTH): String {
        return buildString(length.coerceAtLeast(18)) {
            repeat(length.coerceAtLeast(18)) {
                append(TOKEN_ALPHABET[secureRandom.nextInt(TOKEN_ALPHABET.length)])
            }
        }
    }

    fun hashToken(token: String): String = sha256(token.trim())

    fun verifyToken(rawToken: String, storedHash: String): Boolean {
        return hashToken(rawToken) == storedHash
    }

    fun isExpired(expiresAtMs: Long, nowMs: Long): Boolean = nowMs >= expiresAtMs

    fun buildShareUrl(baseUrl: String, payload: LiveLinkQrPayload): String {
        val params = listOf(
            "session_id" to payload.sessionId,
            "token" to payload.accessToken,
            "expires_at" to payload.expiresAtMs.toString(),
            "mode" to payload.mode.name,
        ).joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "${baseUrl.trimEnd('/')}?$params"
    }

    private fun generateDisplayCode(): String {
        val value = secureRandom.nextInt(900_000) + 100_000
        return value.toString().padStart(DISPLAY_CODE_LENGTH, '0')
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

object LiveLinkPermissionPolicy {
    private val criticalRequests = setOf(
        LiveLinkRemoteRequestType.CLEAR_DTCS,
        LiveLinkRemoteRequestType.RUN_ACTIVE_TEST,
        LiveLinkRemoteRequestType.RUN_SERVICE_RESET,
    )

    fun defaultPermissions(session: LiveLinkSession): LiveLinkPermission {
        return LiveLinkPermission(
            sessionId = session.sessionId,
            canReadObdState = true,
            canReadDtc = session.dtcEnabled,
            canReadFreezeFrame = session.dtcEnabled,
            canReadLivePids = session.livePidsEnabled,
            canReadReports = session.reportsEnabled,
            canUseChat = session.chatEnabled,
            canRequestSnapshot = true,
            canUseVideo = session.videoEnabled,
            canUseAudio = session.audioEnabled,
            canUseCamera = session.cameraEnabled,
            canReadVehicleHistory = session.historyEnabled,
            canReadApproxLocation = session.locationEnabled,
            canReadExactLocation = false,
            canSeeFullVin = false,
            canSeeFullPlate = false,
            canClearDtcs = false,
            canRunActiveTests = false,
            canRunServiceResets = false,
        )
    }

    fun decisionFor(
        requestType: LiveLinkRemoteRequestType,
        session: LiveLinkSession,
        permissions: LiveLinkPermission,
    ): RemoteRequestDecision {
        if (!session.isOpen) {
            return RemoteRequestDecision(
                allowed = false,
                riskLevel = LiveLinkRiskLevel.READ_ONLY,
                requiresLocalApproval = false,
                requiresDoubleConfirmation = false,
                reason = "Sesion no esta abierta.",
            )
        }

        if (requestType in criticalRequests) {
            val flagAllows = when (requestType) {
                LiveLinkRemoteRequestType.CLEAR_DTCS -> permissions.canClearDtcs
                LiveLinkRemoteRequestType.RUN_ACTIVE_TEST -> permissions.canRunActiveTests
                LiveLinkRemoteRequestType.RUN_SERVICE_RESET -> permissions.canRunServiceResets
                else -> false
            }
            val controlledMode = session.mode == LiveLinkMode.REMOTE_CONTROLLED_PRO && !session.readOnly
            return if (flagAllows && controlledMode) {
                RemoteRequestDecision(
                    allowed = true,
                    riskLevel = LiveLinkRiskLevel.CRITICAL_CONTROL,
                    requiresLocalApproval = true,
                    requiresDoubleConfirmation = true,
                    reason = "Accion critica permitida solo con confirmacion local doble.",
                )
            } else {
                RemoteRequestDecision(
                    allowed = false,
                    riskLevel = LiveLinkRiskLevel.CRITICAL_CONTROL,
                    requiresLocalApproval = true,
                    requiresDoubleConfirmation = true,
                    reason = "Bloqueado: LiveLink es lectura por defecto y no permite control remoto critico.",
                )
            }
        }

        return when (requestType) {
            LiveLinkRemoteRequestType.CAPTURE_SNAPSHOT -> permissions.canRequestSnapshot.toDecision(
                LiveLinkRiskLevel.EVIDENCE_CAPTURE,
                localApproval = true,
                reasonIfAllowed = "Snapshot requiere aprobacion local.",
                reasonIfBlocked = "Snapshot no permitido por permisos de sesion.",
            )
            LiveLinkRemoteRequestType.READ_DTCS -> permissions.canReadDtc.toDecision(
                LiveLinkRiskLevel.READ_ONLY,
                reasonIfAllowed = "Lectura DTC permitida.",
                reasonIfBlocked = "DTC no compartidos en esta sesion.",
            )
            LiveLinkRemoteRequestType.READ_FREEZE_FRAME -> permissions.canReadFreezeFrame.toDecision(
                LiveLinkRiskLevel.READ_ONLY,
                reasonIfAllowed = "Freeze frame permitido.",
                reasonIfBlocked = "Freeze frame no compartido.",
            )
            LiveLinkRemoteRequestType.START_LIVE_PIDS,
            LiveLinkRemoteRequestType.STOP_LIVE_PIDS -> permissions.canReadLivePids.toDecision(
                LiveLinkRiskLevel.READ_ONLY,
                reasonIfAllowed = "PIDs live permitidos.",
                reasonIfBlocked = "PIDs live no compartidos.",
            )
            LiveLinkRemoteRequestType.GENERATE_REPORT -> permissions.canReadReports.toDecision(
                LiveLinkRiskLevel.EVIDENCE_CAPTURE,
                localApproval = true,
                reasonIfAllowed = "Reporte remoto requiere aprobacion local.",
                reasonIfBlocked = "Reportes no compartidos.",
            )
            LiveLinkRemoteRequestType.ADD_PHOTO,
            LiveLinkRemoteRequestType.START_CAMERA -> permissions.canUseCamera.toDecision(
                LiveLinkRiskLevel.PRIVACY,
                localApproval = true,
                reasonIfAllowed = "Camara requiere aprobacion local.",
                reasonIfBlocked = "Camara no autorizada.",
            )
            else -> RemoteRequestDecision(
                allowed = false,
                riskLevel = LiveLinkRiskLevel.READ_ONLY,
                requiresLocalApproval = false,
                requiresDoubleConfirmation = false,
                reason = "Solicitud no soportada.",
            )
        }
    }

    private fun Boolean.toDecision(
        risk: LiveLinkRiskLevel,
        localApproval: Boolean = false,
        reasonIfAllowed: String,
        reasonIfBlocked: String,
    ): RemoteRequestDecision {
        return if (this) {
            RemoteRequestDecision(
                allowed = true,
                riskLevel = risk,
                requiresLocalApproval = localApproval,
                requiresDoubleConfirmation = false,
                reason = reasonIfAllowed,
            )
        } else {
            RemoteRequestDecision(
                allowed = false,
                riskLevel = risk,
                requiresLocalApproval = false,
                requiresDoubleConfirmation = false,
                reason = reasonIfBlocked,
            )
        }
    }
}

class LiveLinkSessionEngine(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    fun createSession(input: CreateLiveLinkSessionInput): LiveLinkShareEnvelope {
        val now = nowMs()
        val sessionId = UUID.randomUUID().toString()
        val duration = input.durationMinutes.coerceIn(5, 240)
        val credentials = LiveLinkTokenService.createCredentials(
            sessionId = sessionId,
            mode = input.mode,
            durationMinutes = duration,
            nowMs = now,
        )
        val session = LiveLinkSession(
            sessionId = sessionId,
            ownerUserId = input.ownerUserId,
            vehicleId = input.vehicleId,
            providerId = input.providerId,
            mode = input.mode,
            title = input.title,
            state = LiveLinkSessionState.WAITING_REMOTE_JOIN,
            accessTokenHash = credentials.accessTokenHash,
            readOnly = input.readOnly,
            chatEnabled = true,
            videoEnabled = input.allowVideo,
            audioEnabled = input.allowAudio,
            cameraEnabled = input.allowCamera,
            locationEnabled = input.allowLocation,
            historyEnabled = input.allowVehicleHistory,
            reportsEnabled = input.allowReports,
            dtcEnabled = input.allowDtc,
            livePidsEnabled = input.allowLivePids,
            createdAtMs = now,
            startedAtMs = now,
            expiresAtMs = credentials.expiresAtMs,
        )
        val permissions = LiveLinkPermissionPolicy.defaultPermissions(session)
        val event = event(
            sessionId = sessionId,
            type = LiveLinkEventType.SESSION_CREATED,
            actorRole = LiveLinkActorRole.OWNER,
            message = "Sesion LiveLink creada con token hash y vencimiento obligatorio.",
            metadata = mapOf(
                "mode" to session.mode.name,
                "duration_minutes" to duration.toString(),
                "read_only" to session.readOnly.toString(),
            ),
        )
        return LiveLinkShareEnvelope(
            session = session,
            permissions = permissions,
            credentials = credentials,
            createdEvent = event,
        )
    }

    fun markRemoteJoined(session: LiveLinkSession, actorId: String? = null): Pair<LiveLinkSession, LiveLinkEvent> {
        val now = nowMs()
        val next = session.copy(
            state = LiveLinkSessionState.REMOTE_CONNECTED,
            updatedAtMs = now,
        )
        return next to event(
            sessionId = session.sessionId,
            type = LiveLinkEventType.REMOTE_JOINED,
            actorRole = LiveLinkActorRole.REMOTE_MECHANIC,
            actorId = actorId,
            message = "Remoto conectado a LiveLink.",
        )
    }

    fun activate(session: LiveLinkSession): LiveLinkSession {
        val now = nowMs()
        return session.copy(state = LiveLinkSessionState.ACTIVE, updatedAtMs = now)
    }

    fun revoke(session: LiveLinkSession, actorRole: LiveLinkActorRole = LiveLinkActorRole.OWNER): Pair<LiveLinkSession, LiveLinkEvent> {
        val now = nowMs()
        val next = session.copy(
            state = LiveLinkSessionState.REVOKED,
            revokedAtMs = now,
            endedAtMs = now,
            updatedAtMs = now,
        )
        return next to event(
            sessionId = session.sessionId,
            type = LiveLinkEventType.SESSION_REVOKED,
            actorRole = actorRole,
            message = "Acceso remoto revocado por el usuario.",
        )
    }

    fun complete(session: LiveLinkSession): Pair<LiveLinkSession, LiveLinkEvent> {
        val now = nowMs()
        val next = session.copy(
            state = LiveLinkSessionState.COMPLETED,
            endedAtMs = now,
            updatedAtMs = now,
        )
        return next to event(
            sessionId = session.sessionId,
            type = LiveLinkEventType.SESSION_COMPLETED,
            actorRole = LiveLinkActorRole.OWNER,
            message = "Sesion LiveLink finalizada.",
        )
    }

    fun expireIfNeeded(session: LiveLinkSession): Pair<LiveLinkSession, LiveLinkEvent?> {
        val now = nowMs()
        if (!session.isOpen || !LiveLinkTokenService.isExpired(session.expiresAtMs, now)) {
            return session to null
        }
        val next = session.copy(
            state = LiveLinkSessionState.EXPIRED,
            endedAtMs = now,
            updatedAtMs = now,
        )
        return next to event(
            sessionId = session.sessionId,
            type = LiveLinkEventType.SESSION_EXPIRED,
            actorRole = LiveLinkActorRole.SYSTEM,
            message = "Sesion LiveLink expiro automaticamente.",
        )
    }

    fun createRemoteRequest(
        session: LiveLinkSession,
        permissions: LiveLinkPermission,
        type: LiveLinkRemoteRequestType,
        requestedByRole: LiveLinkActorRole = LiveLinkActorRole.REMOTE_MECHANIC,
        requestedById: String? = null,
    ): RemoteRequestResult {
        val now = nowMs()
        val decision = LiveLinkPermissionPolicy.decisionFor(type, session, permissions)
        val request = LiveLinkRemoteRequest(
            requestId = UUID.randomUUID().toString(),
            sessionId = session.sessionId,
            type = type,
            requestedByRole = requestedByRole,
            requestedById = requestedById,
            status = if (decision.allowed) {
                if (decision.requiresLocalApproval) LiveLinkRequestStatus.PENDING_LOCAL_APPROVAL else LiveLinkRequestStatus.APPROVED
            } else {
                LiveLinkRequestStatus.BLOCKED
            },
            riskLevel = decision.riskLevel,
            requiresLocalApproval = decision.requiresLocalApproval,
            requiresDoubleConfirmation = decision.requiresDoubleConfirmation,
            reason = decision.reason,
            createdAtMs = now,
            resolvedAtMs = if (decision.allowed && !decision.requiresLocalApproval) now else null,
        )
        val eventType = when {
            !decision.allowed && decision.riskLevel == LiveLinkRiskLevel.CRITICAL_CONTROL -> LiveLinkEventType.CRITICAL_REQUEST_BLOCKED
            !decision.allowed -> LiveLinkEventType.SECURITY_WARNING
            type == LiveLinkRemoteRequestType.CAPTURE_SNAPSHOT -> LiveLinkEventType.SNAPSHOT_REQUESTED
            else -> LiveLinkEventType.REMOTE_REQUEST_CREATED
        }
        return RemoteRequestResult(
            request = request,
            event = event(
                sessionId = session.sessionId,
                type = eventType,
                actorRole = requestedByRole,
                actorId = requestedById,
                message = decision.reason,
                metadata = mapOf("request_type" to type.name, "request_status" to request.status.name),
            ),
        )
    }

    fun resolveRequest(
        request: LiveLinkRemoteRequest,
        approved: Boolean,
        actorRole: LiveLinkActorRole = LiveLinkActorRole.OWNER,
    ): Pair<LiveLinkRemoteRequest, LiveLinkEvent> {
        val now = nowMs()
        val next = request.copy(
            status = if (approved) LiveLinkRequestStatus.APPROVED else LiveLinkRequestStatus.DENIED,
            resolvedAtMs = now,
        )
        return next to event(
            sessionId = request.sessionId,
            type = if (approved) LiveLinkEventType.REMOTE_REQUEST_APPROVED else LiveLinkEventType.REMOTE_REQUEST_DENIED,
            actorRole = actorRole,
            message = if (approved) "Solicitud remota aprobada localmente." else "Solicitud remota denegada localmente.",
            metadata = mapOf("request_id" to request.requestId, "request_type" to request.type.name),
        )
    }

    fun captureSnapshot(packet: LiveLinkTelemetryPacket, notes: String = ""): Pair<LiveLinkSnapshot, LiveLinkEvent> {
        val now = nowMs()
        val snapshot = LiveLinkSnapshot(
            snapshotId = UUID.randomUUID().toString(),
            sessionId = packet.sessionId,
            capturedAtMs = now,
            telemetryPacket = packet,
            notes = notes,
        )
        return snapshot to event(
            sessionId = packet.sessionId,
            type = LiveLinkEventType.SNAPSHOT_CAPTURED,
            actorRole = LiveLinkActorRole.OWNER,
            message = "Snapshot de evidencia capturado.",
            metadata = mapOf(
                "source_quality" to packet.sourceQuality.name,
                "dtc_count" to packet.activeDtcs.size.toString(),
                "sample_count" to packet.samples.size.toString(),
            ),
        )
    }

    fun buildReport(
        session: LiveLinkSession,
        packets: List<LiveLinkTelemetryPacket>,
        events: List<LiveLinkEvent>,
        requests: List<LiveLinkRemoteRequest>,
    ): Pair<LiveLinkReport, LiveLinkEvent> {
        val now = nowMs()
        val lastPacket = packets.maxByOrNull { it.timestampMs }
        val sourceQuality = summarizeSourceQuality(packets)
        val report = LiveLinkReport(
            reportId = UUID.randomUUID().toString(),
            sessionId = session.sessionId,
            createdAtMs = now,
            startedAtMs = session.startedAtMs,
            endedAtMs = session.endedAtMs ?: now,
            mode = session.mode,
            state = session.state,
            activeDtcs = packets.flatMap { it.activeDtcs }.distinct().ifEmpty { lastPacket?.activeDtcs.orEmpty() },
            snapshotCount = packets.size,
            eventCount = events.size,
            remoteRequestCount = requests.size,
            sourceQuality = sourceQuality,
            evidenceHash = evidenceHash(session, packets, events, requests),
        )
        return report to event(
            sessionId = session.sessionId,
            type = LiveLinkEventType.REPORT_GENERATED,
            actorRole = LiveLinkActorRole.OWNER,
            message = "Reporte LiveLink generado con hash de evidencia.",
            metadata = mapOf("report_id" to report.reportId, "evidence_hash" to report.evidenceHash),
        )
    }

    private fun event(
        sessionId: String,
        type: LiveLinkEventType,
        actorRole: LiveLinkActorRole,
        message: String,
        actorId: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): LiveLinkEvent {
        return LiveLinkEvent(
            eventId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            type = type,
            actorRole = actorRole,
            actorId = actorId,
            message = message,
            createdAtMs = nowMs(),
            metadata = metadata,
        )
    }

    private fun summarizeSourceQuality(packets: List<LiveLinkTelemetryPacket>): LiveLinkSourceQuality {
        if (packets.isEmpty()) return LiveLinkSourceQuality.NO_REAL_OBD
        val qualities = packets.map { it.sourceQuality }.toSet()
        return when {
            qualities == setOf(LiveLinkSourceQuality.REAL_OBD) -> LiveLinkSourceQuality.REAL_OBD
            qualities == setOf(LiveLinkSourceQuality.NO_REAL_OBD) -> LiveLinkSourceQuality.NO_REAL_OBD
            qualities == setOf(LiveLinkSourceQuality.SIMULATED) -> LiveLinkSourceQuality.SIMULATED
            qualities == setOf(LiveLinkSourceQuality.MANUAL) -> LiveLinkSourceQuality.MANUAL
            qualities.any { it == LiveLinkSourceQuality.REAL_OBD } -> LiveLinkSourceQuality.MIXED
            else -> qualities.first()
        }
    }

    private fun evidenceHash(
        session: LiveLinkSession,
        packets: List<LiveLinkTelemetryPacket>,
        events: List<LiveLinkEvent>,
        requests: List<LiveLinkRemoteRequest>,
    ): String {
        val canonical = buildString {
            append(session.sessionId).append('|')
            append(session.mode.name).append('|')
            append(session.createdAtMs).append('|')
            packets.sortedBy { it.timestampMs }.forEach { packet ->
                append(packet.timestampMs).append(':')
                append(packet.sourceQuality.name).append(':')
                append(packet.activeDtcs.sorted().joinToString(",")).append(':')
                append(packet.samples.sortedBy { it.pid }.joinToString(",") { "${it.pid}:${it.value}:${it.quality}" })
                append('|')
            }
            events.sortedBy { it.createdAtMs }.forEach { append(it.type.name).append(':').append(it.createdAtMs).append('|') }
            requests.sortedBy { it.createdAtMs }.forEach { append(it.type.name).append(':').append(it.status.name).append('|') }
        }
        return sha256(canonical)
    }
}

object LiveLinkTelemetryMapper {
    fun fromObdSamples(
        sessionId: String,
        connectionState: String,
        adapterQuality: String,
        samples: Collection<TelemetrySample>,
        activeDtcs: List<String>,
        freezeFrameAvailable: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): LiveLinkTelemetryPacket {
        val distinctSamples = samples
            .distinctBy { it.pid.uppercase(Locale.US) }
            .sortedBy { it.pid }
            .map { it.toLiveLinkSample(nowMs) }
        val sourceQuality = summarizeSamples(distinctSamples)
        return LiveLinkTelemetryPacket(
            packetId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            timestampMs = nowMs,
            connectionState = connectionState,
            adapterQuality = adapterQuality,
            sourceQuality = sourceQuality,
            samples = distinctSamples,
            activeDtcs = activeDtcs.distinct(),
            freezeFrameAvailable = freezeFrameAvailable,
            degradedReason = if (sourceQuality == LiveLinkSourceQuality.NO_REAL_OBD) "OBD sin enlace real" else null,
        )
    }

    private fun TelemetrySample.toLiveLinkSample(nowMs: Long): LiveLinkTelemetrySample {
        return LiveLinkTelemetrySample(
            pid = pid,
            name = name,
            value = value,
            unit = unit,
            quality = quality.toLiveLinkQuality(),
            timestampMs = nowMs,
            latencyMs = latencyMs,
            source = source.toLiveLinkSource(),
        )
    }

    private fun TelemetryQuality.toLiveLinkQuality(): LiveLinkTelemetryQuality = when (this) {
        TelemetryQuality.VALID -> LiveLinkTelemetryQuality.VALID
        TelemetryQuality.STALE -> LiveLinkTelemetryQuality.STALE
        TelemetryQuality.UNSUPPORTED -> LiveLinkTelemetryQuality.UNSUPPORTED
        TelemetryQuality.TIMEOUT -> LiveLinkTelemetryQuality.TIMEOUT
        TelemetryQuality.PARSE_ERROR -> LiveLinkTelemetryQuality.PARSE_ERROR
        TelemetryQuality.OUT_OF_RANGE -> LiveLinkTelemetryQuality.OUT_OF_RANGE
        TelemetryQuality.MANUAL -> LiveLinkTelemetryQuality.MANUAL
        TelemetryQuality.SIMULATED -> LiveLinkTelemetryQuality.SIMULATED
    }

    private fun ObdDataSource.toLiveLinkSource(): LiveLinkDataSource = when (this) {
        ObdDataSource.REAL_OBD -> LiveLinkDataSource.REAL_OBD
        ObdDataSource.OFFLINE_KNOWLEDGE -> LiveLinkDataSource.OFFLINE_KNOWLEDGE
        ObdDataSource.SIMULATED_DEMO -> LiveLinkDataSource.SIMULATED_DEMO
        ObdDataSource.MANUAL_INPUT -> LiveLinkDataSource.MANUAL_INPUT
        ObdDataSource.NO_REAL_OBD -> LiveLinkDataSource.NO_REAL_OBD
    }

    private fun summarizeSamples(samples: List<LiveLinkTelemetrySample>): LiveLinkSourceQuality {
        if (samples.isEmpty()) return LiveLinkSourceQuality.NO_REAL_OBD
        val validReal = samples.any {
            it.source == LiveLinkDataSource.REAL_OBD &&
                it.quality == LiveLinkTelemetryQuality.VALID &&
                it.value != null
        }
        val onlySimulated = samples.all { it.source == LiveLinkDataSource.SIMULATED_DEMO || it.quality == LiveLinkTelemetryQuality.SIMULATED }
        val onlyManual = samples.all { it.source == LiveLinkDataSource.MANUAL_INPUT || it.quality == LiveLinkTelemetryQuality.MANUAL }
        val onlyNoReal = samples.all { it.source == LiveLinkDataSource.NO_REAL_OBD || it.value == null }
        return when {
            validReal && samples.all { it.source == LiveLinkDataSource.REAL_OBD } -> LiveLinkSourceQuality.REAL_OBD
            validReal -> LiveLinkSourceQuality.MIXED
            onlySimulated -> LiveLinkSourceQuality.SIMULATED
            onlyManual -> LiveLinkSourceQuality.MANUAL
            onlyNoReal -> LiveLinkSourceQuality.NO_REAL_OBD
            else -> LiveLinkSourceQuality.STALE_OBD
        }
    }
}

object LiveLinkFrequencyPolicy {
    fun intervalMs(
        mode: LiveLinkMode,
        averageLatencyMs: Long,
        localNetworkStable: Boolean = true,
    ): Long {
        val base = when (mode) {
            LiveLinkMode.LOCAL_WIFI -> if (localNetworkStable) 100L else 250L
            LiveLinkMode.REMOTE_READ_ONLY,
            LiveLinkMode.REMOTE_ASSISTED,
            LiveLinkMode.PERITO_MODE,
            LiveLinkMode.FLEET_MODE -> 1_000L
            LiveLinkMode.REMOTE_CONTROLLED_PRO -> 250L
        }
        return when {
            averageLatencyMs >= 2_000L -> (base * 4).coerceAtLeast(1_000L)
            averageLatencyMs >= 1_000L -> (base * 2).coerceAtLeast(500L)
            else -> base
        }
    }
}

object LiveLinkPrivacyGuard {
    fun maskVin(vin: String?): String? {
        val clean = vin?.trim().orEmpty()
        if (clean.isBlank()) return null
        return "VIN-****${clean.takeLast(6)}"
    }

    fun maskPlate(plate: String?): String? {
        val clean = plate?.trim().orEmpty()
        if (clean.isBlank()) return null
        return "***${clean.takeLast(2)}"
    }

    fun sharePayloadContainsPersonalData(payload: LiveLinkQrPayload, sensitiveValues: List<String?>): Boolean {
        val joined = listOf(
            payload.sessionId,
            payload.accessToken,
            payload.expiresAtMs.toString(),
            payload.mode.name,
        ).joinToString("|")
        return sensitiveValues
            .filterNotNull()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .any { joined.contains(it, ignoreCase = true) }
    }
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
