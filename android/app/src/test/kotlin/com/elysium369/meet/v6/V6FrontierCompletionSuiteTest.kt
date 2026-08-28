package com.elysium369.meet.v6

import com.elysium369.meet.ai.edge.DeviceCapabilitySnapshot
import com.elysium369.meet.ai.edge.EdgeAiRuntimeV2
import com.elysium369.meet.ai.edge.EdgeModelTier
import com.elysium369.meet.buslab.CanBusFrameAnalyzer
import com.elysium369.meet.buslab.DbcMessageDefinition
import com.elysium369.meet.buslab.DbcSignalDefinition
import com.elysium369.meet.core.transport.RawCanFrame
import com.elysium369.meet.extensions.WasmDiagnosticExtensionRuntime
import com.elysium369.meet.extensions.WasmExecutionResult
import com.elysium369.meet.extensions.WasmExtensionManifest
import com.elysium369.meet.instrumentation.InstrumentationLabV2
import com.elysium369.meet.research.ResearchFrontierV2
import org.junit.Assert.*
import org.junit.Test

class V6FrontierCompletionSuiteTest {

    // ----------------------------------------------------
    // 1. Vehicle Bus Lab CAN & DBC Decoder
    // ----------------------------------------------------
    @Test
    fun busLab_decodeCanSignalsAndBusLoad() {
        val msgDef = DbcMessageDefinition(
            arbitrationId = 0x201,
            name = "ENGINE_RPM_SPEED",
            signals = listOf(
                DbcSignalDefinition("RPM", startBit = 0, bitLength = 16, scale = 0.25, unit = "rpm"),
                DbcSignalDefinition("SPEED", startBit = 16, bitLength = 8, scale = 1.0, unit = "km/h")
            )
        )

        // Payload with RPM = 800 (raw = 3200 = 0x0C80) and Speed = 60 km/h (0x3C)
        val rawBytes = byteArrayOf(0x80.toByte(), 0x0C, 0x3C, 0x00, 0x00, 0x00, 0x00, 0x00)
        val frame = RawCanFrame(
            arbitrationId = 0x201,
            isExtended = false,
            isFd = false,
            data = rawBytes,
            timestampNanos = 1_000_000L,
        )

        val signals = CanBusFrameAnalyzer.decodeSignals(frame, msgDef)
        assertEquals(2, signals.size)

        val busLoad = CanBusFrameAnalyzer.computeBusLoad(framesInWindow = 500, windowDurationMs = 1000L, nominalBitrate = 500_000)
        assertEquals(11.0f, busLoad, 0.5f)
    }

    // ----------------------------------------------------
    // 2. Instrumentation Lab DSP & Thermal Vision
    // ----------------------------------------------------
    @Test
    fun instrumentationLab_acousticAndThermalAnalysis() {
        // Acoustic knock signal test
        val audioSamples = FloatArray(1024) { i ->
            if (i % 8 == 0) 0.9f else 0.1f // High amplitude oscillation
        }
        val acoustic = InstrumentationLabV2.analyzeAcousticFrame(audioSamples, sampleRateHz = 44100)
        assertTrue(acoustic.peakMagnitudeDb > -2.0f)

        // Thermal hotspot detection
        val temps = floatArrayOf(45f, 50f, 62f, 105f, 55f)
        val thermal = InstrumentationLabV2.analyzeThermalFrame(temps, hotspotThresholdC = 95f)
        assertTrue(thermal.isHotspotDetected)
        assertEquals(105f, thermal.maxTempC, 0.01f)
        assertEquals(60f, thermal.deltaTempC, 0.01f)
    }

    // ----------------------------------------------------
    // 3. Edge AI Runtime Profiler
    // ----------------------------------------------------
    @Test
    fun edgeAi_selectsAppropriateModelTier() {
        // High thermal throttle -> tiny model
        val throttled = DeviceCapabilitySnapshot(
            totalRamMb = 8000,
            availableRamMb = 4000,
            isNpuSupported = true,
            isGpuAccelerated = true,
            batteryPercent = 10, // Critical
            isThermalThrottling = true,
        )
        assertEquals(EdgeModelTier.TINY_QUANTIZED_LOCAL, EdgeAiRuntimeV2.selectModelTier(throttled, requiresDeepReasoning = false))

        // Flagship device healthy -> balanced local SLM
        val flagship = DeviceCapabilitySnapshot(
            totalRamMb = 12000,
            availableRamMb = 6000,
            isNpuSupported = true,
            isGpuAccelerated = true,
            batteryPercent = 85,
            isThermalThrottling = false,
        )
        assertEquals(EdgeModelTier.BALANCED_SLM_LOCAL, EdgeAiRuntimeV2.selectModelTier(flagship, requiresDeepReasoning = false))

        // Deep reasoning request -> remote cloud
        assertEquals(EdgeModelTier.CLOUD_REASONING_REMOTE, EdgeAiRuntimeV2.selectModelTier(flagship, requiresDeepReasoning = true))
    }

    // ----------------------------------------------------
    // 4. WASM Diagnostic Extension Sandbox
    // ----------------------------------------------------
    @Test
    fun wasmSandbox_securityViolationsBlocked() {
        val trustedSigs = setOf("valid-sig-sha256")

        // 1. Untrusted signature fails
        val untrustedManifest = WasmExtensionManifest("ext-1", "Hacker", "1.0", "bad-sig")
        val r1 = WasmDiagnosticExtensionRuntime.validateAndExecute(untrustedManifest, byteArrayOf(1, 2, 3), trustedSigs)
        assertTrue(r1 is WasmExecutionResult.SecurityViolation)

        // 2. Active ECU write attempt fails
        val ecuWriteManifest = WasmExtensionManifest("ext-2", "OEM", "1.0", "valid-sig-sha256", hasEcuWritePermission = true)
        val r2 = WasmDiagnosticExtensionRuntime.validateAndExecute(ecuWriteManifest, byteArrayOf(1, 2, 3), trustedSigs)
        assertTrue(r2 is WasmExecutionResult.SecurityViolation)

        // 3. Valid read-only transformation passes
        val validManifest = WasmExtensionManifest("ext-3", "OEM", "1.0", "valid-sig-sha256", hasEcuWritePermission = false)
        val r3 = WasmDiagnosticExtensionRuntime.validateAndExecute(validManifest, byteArrayOf(1, 2, 3), trustedSigs)
        assertTrue(r3 is WasmExecutionResult.Success)
    }

    // ----------------------------------------------------
    // 5. Research Frontier Modules
    // ----------------------------------------------------
    @Test
    fun researchFrontier_isStrictlyNonAuthoritative() {
        val res = ResearchFrontierV2.applyDifferentialPrivacyLaplace(trueValue = 100.0, epsilon = 1.0, sensitivity = 1.0)
        assertTrue(res.isExperimental)
        assertFalse("Research module can never carry diagnostic authority", res.isDiagnosticAuthority)
    }
}
