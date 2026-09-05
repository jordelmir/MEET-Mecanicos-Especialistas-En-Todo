package com.elysium369.meet.vss

import com.elysium369.meet.authority.VerificationLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

sealed class VssSetResult {
    data class Success(val path: String, val updatedSnapshot: VssSignalSnapshot) : VssSetResult()
    data class Unauthorized(val reason: String) : VssSetResult()
    data class InvalidPathOrType(val reason: String) : VssSetResult()
}

/**
 * VehicleSignalGraph — Central in-memory reactive signal store implementing COVESA VSS 4.1
 * and VISS 3.0 query/subscription semantics.
 */
@Singleton
class VehicleSignalGraph @Inject constructor() {

    private val signalStore = ConcurrentHashMap<String, VssSignalSnapshot>()
    private val signalUpdateFlow = MutableSharedFlow<VssSignalSnapshot>(extraBufferCapacity = 256)
    private val sequenceCounter = AtomicLong(1L)

    /**
     * Translates and ingests an OBD-II Mode 01 PID into the standardized VSS graph.
     */
    fun ingestObdPid(
        pid: String,
        numericValue: Double,
        rawHex: String = "",
        ecuAddress: String = "7E0",
        timestampEpochMs: Long = System.currentTimeMillis(),
    ): VssSignalSnapshot? {
        val normalizedPid = pid.trim().uppercase()
        val vssPath = when (normalizedPid) {
            "010D", "0D" -> VssStandardPaths.VEHICLE_SPEED
            "010C", "0C" -> VssStandardPaths.ENGINE_SPEED
            "0105", "05" -> VssStandardPaths.ENGINE_ECT
            "010B", "0B" -> VssStandardPaths.ENGINE_MAP
            "0110", "10" -> VssStandardPaths.ENGINE_MAF
            "0111", "11" -> VssStandardPaths.ENGINE_TPS
            "0106", "06" -> VssStandardPaths.ENGINE_STFT_B1
            "0107", "07" -> VssStandardPaths.ENGINE_LTFT_B1
            "0142", "42" -> VssStandardPaths.BATTERY_VOLTAGE
            else -> return null
        }

        val metadata = VssStandardPaths.CATALOG[vssPath] ?: return null
        val provenance = SignalProvenance(
            protocol = SignalProtocol.OBD2_STANDARD,
            rawIdentifier = normalizedPid,
            rawPayloadHex = rawHex,
            ecuAddress = ecuAddress,
            capturedAtEpochMs = timestampEpochMs,
            sampleSequence = sequenceCounter.getAndIncrement(),
        )

        val snapshot = VssSignalSnapshot(
            path = vssPath,
            value = numericValue.toFloat(),
            metadata = metadata,
            provenance = provenance,
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            timestampEpochMs = timestampEpochMs,
        )

        storeAndPublish(snapshot)
        return snapshot
    }

    /**
     * Ingests active Diagnostic Trouble Codes (DTCs) into Vehicle.OBD.DTCList.
     */
    fun ingestDtcs(
        dtcs: List<String>,
        rawHex: String = "",
        ecuAddress: String = "7E0",
        timestampEpochMs: Long = System.currentTimeMillis(),
    ): VssSignalSnapshot {
        val metadata = VssStandardPaths.CATALOG[VssStandardPaths.OBD_DTC_LIST]!!
        val provenance = SignalProvenance(
            protocol = SignalProtocol.OBD2_STANDARD,
            rawIdentifier = "MODE_03_07",
            rawPayloadHex = rawHex,
            ecuAddress = ecuAddress,
            capturedAtEpochMs = timestampEpochMs,
            sampleSequence = sequenceCounter.getAndIncrement(),
        )

        val snapshot = VssSignalSnapshot(
            path = VssStandardPaths.OBD_DTC_LIST,
            value = dtcs,
            metadata = metadata,
            provenance = provenance,
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            timestampEpochMs = timestampEpochMs,
        )

        storeAndPublish(snapshot)
        return snapshot
    }

    /**
     * Ingests GPS telemetry into standardized VSS location paths.
     */
    fun ingestLocation(
        latitude: Double,
        longitude: Double,
        heading: Float? = null,
        altitude: Double? = null,
        speedKph: Float? = null,
        timestampEpochMs: Long = System.currentTimeMillis(),
    ) {
        val seq = sequenceCounter.getAndIncrement()
        val provenance = SignalProvenance(
            protocol = SignalProtocol.OEM_TELEMATICS_API,
            rawIdentifier = "GPS_FUSED",
            rawPayloadHex = "",
            capturedAtEpochMs = timestampEpochMs,
            sampleSequence = seq,
        )

        VssStandardPaths.CATALOG[VssStandardPaths.LOCATION_LATITUDE]?.let {
            storeAndPublish(VssSignalSnapshot(VssStandardPaths.LOCATION_LATITUDE, latitude, it, provenance, VerificationLevel.PHYSICALLY_VERIFIED, timestampEpochMs))
        }
        VssStandardPaths.CATALOG[VssStandardPaths.LOCATION_LONGITUDE]?.let {
            storeAndPublish(VssSignalSnapshot(VssStandardPaths.LOCATION_LONGITUDE, longitude, it, provenance, VerificationLevel.PHYSICALLY_VERIFIED, timestampEpochMs))
        }
        if (heading != null) {
            VssStandardPaths.CATALOG[VssStandardPaths.LOCATION_HEADING]?.let {
                storeAndPublish(VssSignalSnapshot(VssStandardPaths.LOCATION_HEADING, heading, it, provenance, VerificationLevel.PHYSICALLY_VERIFIED, timestampEpochMs))
            }
        }
        if (altitude != null) {
            VssStandardPaths.CATALOG[VssStandardPaths.LOCATION_ALTITUDE]?.let {
                storeAndPublish(VssSignalSnapshot(VssStandardPaths.LOCATION_ALTITUDE, altitude, it, provenance, VerificationLevel.PHYSICALLY_VERIFIED, timestampEpochMs))
            }
        }
        if (speedKph != null && !signalStore.containsKey(VssStandardPaths.VEHICLE_SPEED)) {
            // Fallback GPS speed if OBD speed is unavailable
            VssStandardPaths.CATALOG[VssStandardPaths.VEHICLE_SPEED]?.let {
                storeAndPublish(VssSignalSnapshot(VssStandardPaths.VEHICLE_SPEED, speedKph, it, provenance, VerificationLevel.TRANSIENT_OBSERVED, timestampEpochMs))
            }
        }
    }

    /**
     * VISS 3.0: Read the current snapshot of a VSS signal by exact path.
     */
    fun get(path: String): VssSignalSnapshot? {
        return signalStore[path]
    }

    /**
     * VISS 3.0: Subscribe to updates for a specific path or wildcard prefix.
     */
    fun subscribe(pathFilter: String): Flow<VssSignalSnapshot> {
        val isWildcard = pathFilter.endsWith("*")
        val prefix = if (isWildcard) pathFilter.removeSuffix("*") else pathFilter

        return signalUpdateFlow.asSharedFlow().filter { snapshot ->
            if (isWildcard) {
                snapshot.path.startsWith(prefix)
            } else {
                snapshot.path == pathFilter
            }
        }
    }

    /**
     * VISS 3.0: Protected set operation. Writing directly to a physical vehicle signal
     * requires authorized diagnostic or actuator test permissions.
     */
    fun set(path: String, value: Any, isAuthorizedAction: Boolean): VssSetResult {
        val metadata = VssStandardPaths.CATALOG[path]
            ?: return VssSetResult.InvalidPathOrType("Unknown VSS path: $path")

        if (!isAuthorizedAction) {
            return VssSetResult.Unauthorized("Physical actuator or signal mutation on $path requires verified authorization")
        }

        val provenance = SignalProvenance(
            protocol = SignalProtocol.UDS_DIAGNOSTIC,
            rawIdentifier = "VISS_ACTUATE",
            rawPayloadHex = "",
            capturedAtEpochMs = System.currentTimeMillis(),
            sampleSequence = sequenceCounter.getAndIncrement(),
        )

        val snapshot = VssSignalSnapshot(
            path = path,
            value = value,
            metadata = metadata,
            provenance = provenance,
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            timestampEpochMs = System.currentTimeMillis(),
        )

        storeAndPublish(snapshot)
        return VssSetResult.Success(path, snapshot)
    }

    /**
     * Returns a point-in-time snapshot of all active vehicle signals.
     */
    fun snapshotAll(): Map<String, VssSignalSnapshot> {
        return HashMap(signalStore)
    }

    fun clear() {
        signalStore.clear()
    }

    private fun storeAndPublish(snapshot: VssSignalSnapshot) {
        signalStore[snapshot.path] = snapshot
        signalUpdateFlow.tryEmit(snapshot)
    }
}
