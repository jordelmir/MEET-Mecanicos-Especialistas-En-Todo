package com.elysium369.meet.core.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class DiagnosticStackDescriptor(
    val transport: DiagnosticTransport,
    val applicationProtocol: DiagnosticApplicationProtocol,
    val supportsPhysicalAddressing: Boolean,
    val supportsFunctionalAddressing: Boolean,
)

data class DiagnosticStrategyPlan(
    val primaryRequests: List<String>,
    val fallbackRequests: List<String> = emptyList(),
    val unsupportedReason: String? = null,
) {
    val isSupported: Boolean get() = unsupportedReason == null && primaryRequests.isNotEmpty()
}

interface DiagnosticStrategy {
    val applicationProtocol: DiagnosticApplicationProtocol
    fun compileDtcPlan(mode: DiagnosticScanMode): DiagnosticStrategyPlan
}

object SaeObdDiagnosticStrategy : DiagnosticStrategy {
    override val applicationProtocol = DiagnosticApplicationProtocol.SAE_OBD
    override fun compileDtcPlan(mode: DiagnosticScanMode): DiagnosticStrategyPlan =
        DiagnosticStrategyPlan(
            primaryRequests = when (mode) {
                DiagnosticScanMode.QUICK -> listOf("03")
                DiagnosticScanMode.CLEAR_VERIFY -> listOf("03", "07")
                DiagnosticScanMode.FULL_VEHICLE -> listOf("03", "07", "0A")
            },
        )
}

object UdsDiagnosticStrategy : DiagnosticStrategy {
    override val applicationProtocol = DiagnosticApplicationProtocol.UDS
    override fun compileDtcPlan(mode: DiagnosticScanMode): DiagnosticStrategyPlan =
        DiagnosticStrategyPlan(primaryRequests = listOf("1902FF"), fallbackRequests = listOf("19020D"))
}

object DoIpUdsDiagnosticStrategy : DiagnosticStrategy by UdsDiagnosticStrategy

class UnsupportedDiagnosticStrategy(
    override val applicationProtocol: DiagnosticApplicationProtocol,
    private val reason: String,
) : DiagnosticStrategy {
    override fun compileDtcPlan(mode: DiagnosticScanMode): DiagnosticStrategyPlan =
        DiagnosticStrategyPlan(primaryRequests = emptyList(), unsupportedReason = reason)
}

object DiagnosticStrategyRegistry {
    fun forStack(stack: DiagnosticStackDescriptor): DiagnosticStrategy = when {
        stack.transport == DiagnosticTransport.DOIP && stack.applicationProtocol == DiagnosticApplicationProtocol.UDS ->
            DoIpUdsDiagnosticStrategy
        stack.applicationProtocol == DiagnosticApplicationProtocol.UDS -> UdsDiagnosticStrategy
        stack.applicationProtocol == DiagnosticApplicationProtocol.SAE_OBD -> SaeObdDiagnosticStrategy
        stack.applicationProtocol == DiagnosticApplicationProtocol.KWP2000 ->
            UnsupportedDiagnosticStrategy(stack.applicationProtocol, "KWP strategy requires a source-backed capability pack")
        stack.applicationProtocol == DiagnosticApplicationProtocol.OBD_ON_UDS ->
            UnsupportedDiagnosticStrategy(stack.applicationProtocol, "OBD-on-UDS capability not established")
        stack.applicationProtocol == DiagnosticApplicationProtocol.OEM ->
            UnsupportedDiagnosticStrategy(stack.applicationProtocol, "OEM strategy requires verified ECU-specific data")
        else -> UnsupportedDiagnosticStrategy(stack.applicationProtocol, "Application protocol has not been established")
    }
}

object DiagnosticProtocolRegistry {
    fun resolve(detectedProtocol: String, isDoIpMode: Boolean): DiagnosticStackDescriptor {
        val normalized = detectedProtocol.trim().uppercase()
        return when {
            isDoIpMode || normalized.contains("DOIP") || normalized.contains("13400") ->
                DiagnosticStackDescriptor(DiagnosticTransport.DOIP, DiagnosticApplicationProtocol.UDS, true, false)
            normalized.contains("CAN") || normalized.contains("15765") ->
                // ISO-TP/CAN identifies transport, not whether the application
                // payload is SAE OBD, UDS, OBD-on-UDS or OEM-specific.
                DiagnosticStackDescriptor(DiagnosticTransport.CAN, DiagnosticApplicationProtocol.UNKNOWN, true, true)
            normalized.contains("KWP") ->
                DiagnosticStackDescriptor(DiagnosticTransport.K_LINE, DiagnosticApplicationProtocol.KWP2000, false, false)
            normalized.contains("9141") ->
                DiagnosticStackDescriptor(DiagnosticTransport.K_LINE, DiagnosticApplicationProtocol.SAE_OBD, false, false)
            else -> DiagnosticStackDescriptor(
                DiagnosticTransport.UNKNOWN,
                DiagnosticApplicationProtocol.SAE_OBD,
                false,
                false,
            )
        }
    }
}

/**
 * The single application-facing acquisition authority. UI/domain features use
 * this engine rather than calling the temporary ObdSession facade directly.
 * Findings can only emerge from the typed scan report produced by that facade
 * and its DiagnosticFindingFactory boundary.
 */
class DiagnosticAcquisitionEngine @Inject constructor(
    private val compatibilitySession: ObdSession,
) {
    suspend fun scan(mode: DiagnosticScanMode): DtcScanReport =
        compatibilitySession.readProfessionalDtcScan(mode)
}

/**
 * Domain-facing authority for destructive diagnostic-memory operations.
 * Keeping this boundary separate from acquisition prevents UI/use cases from
 * growing new direct dependencies on the temporary ObdSession facade.
 */
class DiagnosticMemoryEngine @Inject constructor(
    private val compatibilitySession: ObdSession,
) {
    suspend fun clear(plan: ClearVerificationPlan): ClearDtcResult =
        compatibilitySession.clearDtcs(plan)
}

/** Single-owner actor for every command that touches the physical vehicle bus. */
class PhysicalBusActor {
    private val mutex = Mutex()
    private val _owner = MutableStateFlow(PhysicalBusOwner.IDLE)
    val owner: StateFlow<PhysicalBusOwner> = _owner.asStateFlow()
    val currentOwner: PhysicalBusOwner get() = _owner.value

    suspend fun <T> withLease(
        owner: PhysicalBusOwner,
        onAcquire: () -> Unit,
        onRelease: () -> Unit,
        block: suspend () -> T,
    ): T = mutex.withLock {
        _owner.value = owner
        onAcquire()
        try {
            block()
        } finally {
            _owner.value = PhysicalBusOwner.IDLE
            onRelease()
        }
    }
}
