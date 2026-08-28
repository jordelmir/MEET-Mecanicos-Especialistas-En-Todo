package com.elysium369.meet.core.telemetry

import org.junit.Assert.*
import org.junit.Test

class TelemetryAndRegimeEngineTest {

    @Test
    fun binarySegmentCompressionAndMerkleVerification() {
        val sampleFrames = (1..100).map { i ->
            TelemetryBinaryFrame(
                timestampMs = 1000L + i * 50L,
                pidHash = 0x010C,
                value = 800f + (i % 20) * 10f,
            )
        }

        val segment = CompressedTelemetryStorageEngineV2.buildSegment(sampleFrames)
        assertEquals(100, segment.sampleCount)
        assertTrue(segment.compressedPayload.isNotEmpty())
        assertTrue(segment.merkleRootHash.isNotEmpty())

        val (isValid, decompressedFrames) = CompressedTelemetryStorageEngineV2.decompressAndVerifySegment(segment)
        assertTrue("Segment Merkle root verification must pass", isValid)
        assertEquals(100, decompressedFrames.size)
        assertEquals(sampleFrames.first().timestampMs, decompressedFrames.first().timestampMs)
        assertEquals(sampleFrames.first().value, decompressedFrames.first().value, 0.001f)
        assertEquals(sampleFrames.last().value, decompressedFrames.last().value, 0.001f)
    }

    @Test
    fun regimeClassificationEvidenceBounds() {
        // 1. Idle
        val idleSnapshot = RegimeSensorSnapshot(
            rpm = 750f,
            speedKmh = 0f,
            throttlePercent = 0f,
            engineLoadPercent = 20f,
            coolantTempC = 90f,
            sessionElapsedSeconds = 600,
        )
        val idleObs = PredictiveRegimeEngineV2.classifyRegime(idleSnapshot)
        assertEquals(VehicleOperatingRegime.IDLE, idleObs.regime)
        assertTrue(idleObs.confidence >= 90.0)

        // 2. Wide Open Throttle
        val wotSnapshot = RegimeSensorSnapshot(
            rpm = 4500f,
            speedKmh = 90f,
            throttlePercent = 88f,
            engineLoadPercent = 92f,
            coolantTempC = 92f,
            sessionElapsedSeconds = 650,
        )
        val wotObs = PredictiveRegimeEngineV2.classifyRegime(wotSnapshot)
        assertEquals(VehicleOperatingRegime.WIDE_OPEN_THROTTLE_ACCELERATION, wotObs.regime)

        // 3. Cold Start
        val coldSnapshot = RegimeSensorSnapshot(
            rpm = 1200f,
            speedKmh = 0f,
            throttlePercent = 0f,
            engineLoadPercent = 35f,
            coolantTempC = 25f,
            sessionElapsedSeconds = 40,
        )
        val coldObs = PredictiveRegimeEngineV2.classifyRegime(coldSnapshot)
        assertEquals(VehicleOperatingRegime.COLD_START_WARMUP, coldObs.regime)

        // 4. Missing sensor evidence
        val missingSnapshot = RegimeSensorSnapshot(
            rpm = null,
            speedKmh = 50f,
            throttlePercent = 10f,
            engineLoadPercent = 30f,
            coolantTempC = 85f,
            sessionElapsedSeconds = 200,
        )
        val missingObs = PredictiveRegimeEngineV2.classifyRegime(missingSnapshot)
        assertEquals(VehicleOperatingRegime.UNKNOWN_INSUFFICIENT_TELEMETRY, missingObs.regime)
        assertEquals(0.0, missingObs.confidence, 0.001)
    }
}
