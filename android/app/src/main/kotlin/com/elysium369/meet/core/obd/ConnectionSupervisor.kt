package com.elysium369.meet.core.obd

import android.util.Log
import com.elysium369.meet.core.transport.TransportException
import com.elysium369.meet.core.transport.TransportInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single-authority connection supervisor coordinating transport lifecycle,
 * fast-path recovery, and connection health metrics.
 */
class ConnectionSupervisor(
    private val scope: CoroutineScope
) {
    private val TAG = "EV_CONN_SUPERVISOR"
    private val lifecycleMutex = Mutex()

    private val _health = MutableStateFlow(ConnectionHealth())
    val health: StateFlow<ConnectionHealth> = _health.asStateFlow()

    @Volatile
    private var activeTransport: TransportInterface? = null

    @Volatile
    private var activeAddress: String? = null

    suspend fun switchTransport(
        newAddress: String,
        transportFactory: suspend (String) -> TransportInterface
    ): TransportInterface {
        return lifecycleMutex.withLock {
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Switching transport target: from=$activeAddress to=$newAddress")
                _health.value = _health.value.copy(
                    transport = TransportHealth.CONNECTING,
                    recoveryState = RecoveryState.IDLE
                )

                // 1. Await full closure of previous transport
                activeTransport?.let { old ->
                    Log.d(TAG, "Closing active transport before switch...")
                    runCatching { old.disconnect() }
                }
                activeTransport = null
                activeAddress = newAddress

                // 2. Instantiate new transport
                val newTransport = transportFactory(newAddress)
                activeTransport = newTransport
                newTransport
            }
        }
    }

    suspend fun connectWithFastPath(
        fingerprint: String,
        connectBlock: suspend (TransportInterface) -> Unit
    ) {
        lifecycleMutex.withLock {
            withContext(Dispatchers.IO) {
                val transport = activeTransport ?: throw IllegalStateException("No active transport configured")
                val knownProfile = KnownGoodAdapterStore.getProfile(fingerprint)
                val startMs = System.currentTimeMillis()

                try {
                    _health.value = _health.value.copy(
                        transport = TransportHealth.CONNECTING,
                        recoveryState = if (knownProfile != null) RecoveryState.FAST_RECONNECTING else RecoveryState.FULL_DISCOVERY
                    )

                    connectBlock(transport)

                    val duration = System.currentTimeMillis() - startMs
                    _health.value = _health.value.copy(
                        transport = TransportHealth.CONNECTED,
                        adapter = AdapterHealth.SYNCHRONIZED,
                        protocol = ProtocolHealth.LOCKED,
                        ecu = EcuHealth.RESPONSIVE,
                        lastSuccessfulExchangeMs = System.currentTimeMillis(),
                        consecutiveTimeouts = 0,
                        rollingErrorRate = 0.0,
                        recoveryState = RecoveryState.IDLE
                    )

                    KnownGoodAdapterStore.recordSuccess(
                        fingerprint = fingerprint,
                        transportType = TransportType.BLUETOOTH_CLASSIC,
                        connectMethod = ConnectMethod.INSECURE_SPP,
                        protocol = knownProfile?.preferredProtocol,
                        connectDurationMs = duration
                    )
                } catch (e: Exception) {
                    KnownGoodAdapterStore.recordFailure(fingerprint)
                    _health.value = _health.value.copy(
                        transport = TransportHealth.FAILED,
                        recoveryState = RecoveryState.IDLE
                    )
                    throw e
                }
            }
        }
    }

    fun recordExchangeOutcome(isSuccess: Boolean, latencyMs: Long, isNoData: Boolean = false) {
        val current = _health.value
        if (isSuccess) {
            val updatedP95 = if (current.latencyP95Ms > 0) (current.latencyP95Ms * 9 + latencyMs) / 10 else latencyMs
            _health.value = current.copy(
                lastSuccessfulExchangeMs = System.currentTimeMillis(),
                consecutiveTimeouts = 0,
                rollingErrorRate = (current.rollingErrorRate * 0.9).coerceAtLeast(0.0),
                latencyP95Ms = updatedP95,
                ecu = if (isNoData) EcuHealth.NO_DATA else EcuHealth.RESPONSIVE
            )
        } else {
            val newTimeouts = current.consecutiveTimeouts + 1
            val newErrorRate = (current.rollingErrorRate * 0.8 + 0.2).coerceAtMost(1.0)
            val newTransport = if (newTimeouts >= 5) TransportHealth.DEGRADED else current.transport
            _health.value = current.copy(
                consecutiveTimeouts = newTimeouts,
                rollingErrorRate = newErrorRate,
                transport = newTransport
            )
        }
    }

    suspend fun disconnect() {
        lifecycleMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { activeTransport?.disconnect() }
                activeTransport = null
                activeAddress = null
                _health.value = ConnectionHealth()
            }
        }
    }
}
