package com.elysium369.meet.core.livelink

import com.elysium369.meet.core.obd.ObdDataSource
import com.elysium369.meet.core.obd.TelemetryQuality
import com.elysium369.meet.core.obd.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLinkProCoreTest {

    @Test
    fun `session creates hashed token and qr payload excludes vehicle pii`() {
        val engine = LiveLinkSessionEngine(nowMs = { 1_000L })
        val envelope = engine.createSession(
            CreateLiveLinkSessionInput(
                ownerUserId = "owner-1",
                vehicleId = "vehicle-1",
                durationMinutes = 30,
                readOnly = true,
                allowVideo = true,
            )
        )

        assertEquals(envelope.credentials.accessTokenHash, envelope.session.accessTokenHash)
        assertNotEquals(envelope.credentials.accessToken, envelope.session.accessTokenHash)
        assertTrue(LiveLinkTokenService.verifyToken(envelope.credentials.accessToken, envelope.session.accessTokenHash))
        assertEquals(1_801_000L, envelope.credentials.expiresAtMs)
        assertFalse(
            LiveLinkPrivacyGuard.sharePayloadContainsPersonalData(
                envelope.credentials.qrPayload,
                listOf("KMHCN46C18U123456", "ABC123", "9.9281,-84.0907", "010C")
            )
        )
        assertTrue(envelope.permissions.canReadLivePids)
        assertFalse(envelope.permissions.canSeeFullVin)
        assertFalse(envelope.permissions.canReadExactLocation)
        assertFalse(envelope.permissions.canClearDtcs)
    }

    @Test
    fun `obd disconnected maps to no real obd without fake zeros`() {
        val packet = LiveLinkTelemetryMapper.fromObdSamples(
            sessionId = "session-1",
            connectionState = "DISCONNECTED",
            adapterQuality = "UNKNOWN",
            samples = emptyList(),
            activeDtcs = emptyList(),
            freezeFrameAvailable = false,
            nowMs = 5_000L,
        )

        assertEquals(LiveLinkSourceQuality.NO_REAL_OBD, packet.sourceQuality)
        assertEquals("OBD sin enlace real", packet.degradedReason)
        assertTrue(packet.samples.isEmpty())
        assertFalse(packet.hasRealObdEvidence)
    }

    @Test
    fun `valid rpm zero is preserved as real obd value`() {
        val packet = LiveLinkTelemetryMapper.fromObdSamples(
            sessionId = "session-1",
            connectionState = "CONNECTED",
            adapterQuality = "GOOD",
            samples = listOf(sample(pid = "010C", name = "RPM", value = 0.0, unit = "rpm")),
            activeDtcs = listOf("P0230"),
            freezeFrameAvailable = true,
            nowMs = 5_000L,
        )

        assertEquals(LiveLinkSourceQuality.REAL_OBD, packet.sourceQuality)
        assertEquals(0.0, packet.samples.first().value!!, 0.001)
        assertEquals(LiveLinkTelemetryQuality.VALID, packet.samples.first().quality)
        assertTrue(packet.hasRealObdEvidence)
    }

    @Test
    fun `critical remote control requests are blocked in default live link`() {
        val engine = LiveLinkSessionEngine(nowMs = { 1_000L })
        val envelope = engine.createSession(
            CreateLiveLinkSessionInput(
                ownerUserId = "owner-1",
                vehicleId = "vehicle-1",
                mode = LiveLinkMode.REMOTE_READ_ONLY,
                readOnly = true,
            )
        )

        val result = engine.createRemoteRequest(
            session = envelope.session,
            permissions = envelope.permissions,
            type = LiveLinkRemoteRequestType.CLEAR_DTCS,
        )

        assertEquals(LiveLinkRequestStatus.BLOCKED, result.request.status)
        assertEquals(LiveLinkRiskLevel.CRITICAL_CONTROL, result.request.riskLevel)
        assertTrue(result.request.requiresLocalApproval)
        assertTrue(result.request.requiresDoubleConfirmation)
        assertEquals(LiveLinkEventType.CRITICAL_REQUEST_BLOCKED, result.event.type)
    }

    @Test
    fun `snapshot request requires local approval then report hashes evidence`() {
        val engine = LiveLinkSessionEngine(nowMs = { 1_000L })
        val envelope = engine.createSession(
            CreateLiveLinkSessionInput(ownerUserId = "owner-1", vehicleId = "vehicle-1")
        )
        val requestResult = engine.createRemoteRequest(
            session = envelope.session,
            permissions = envelope.permissions,
            type = LiveLinkRemoteRequestType.CAPTURE_SNAPSHOT,
        )
        val packet = LiveLinkTelemetryMapper.fromObdSamples(
            sessionId = envelope.session.sessionId,
            connectionState = "CONNECTED",
            adapterQuality = "GOOD",
            samples = listOf(sample(pid = "010D", name = "Speed", value = 42.0, unit = "km/h")),
            activeDtcs = listOf("P0230"),
            freezeFrameAvailable = false,
            nowMs = 1_100L,
        )

        assertEquals(LiveLinkRequestStatus.PENDING_LOCAL_APPROVAL, requestResult.request.status)

        val (approved, approvalEvent) = engine.resolveRequest(requestResult.request, approved = true)
        val (snapshot, snapshotEvent) = engine.captureSnapshot(packet)
        val (report, reportEvent) = engine.buildReport(
            session = envelope.session,
            packets = listOf(snapshot.telemetryPacket),
            events = listOf(envelope.createdEvent, requestResult.event, approvalEvent, snapshotEvent),
            requests = listOf(approved),
        )

        assertEquals(LiveLinkRequestStatus.APPROVED, approved.status)
        assertEquals(LiveLinkSourceQuality.REAL_OBD, report.sourceQuality)
        assertTrue(report.evidenceHash.length == 64)
        assertEquals(LiveLinkEventType.REPORT_GENERATED, reportEvent.type)
    }

    @Test
    fun `session expires automatically after mandatory ttl`() {
        var clock = 1_000L
        val engine = LiveLinkSessionEngine(nowMs = { clock })
        val envelope = engine.createSession(
            CreateLiveLinkSessionInput(ownerUserId = "owner-1", vehicleId = "vehicle-1", durationMinutes = 5)
        )

        clock = envelope.session.expiresAtMs
        val (expired, event) = engine.expireIfNeeded(envelope.session)

        assertEquals(LiveLinkSessionState.EXPIRED, expired.state)
        assertEquals(LiveLinkEventType.SESSION_EXPIRED, event?.type)
        assertFalse(expired.isOpen)
    }

    private fun sample(
        pid: String,
        name: String,
        value: Double?,
        unit: String,
        quality: TelemetryQuality = TelemetryQuality.VALID,
        source: ObdDataSource = ObdDataSource.REAL_OBD,
    ): TelemetrySample {
        return TelemetrySample(
            pid = pid,
            name = name,
            value = value,
            unit = unit,
            timestampMonotonicMs = 1_000L,
            source = source,
            quality = quality,
            latencyMs = 30L,
            rawResponse = "raw",
        )
    }
}
