package com.elysium369.meet.core.mesh.transport

import com.elysium369.meet.core.mesh.MeshAdvertisement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M E S H   T R A N S P O R T   A B S T R A C T I O N S
 *  ────────────────────────────────────────────────────────────
 *  Architectural contract for physical radio and LAN bearers.
 *
 *  STATUS: DESIGNED
 *  Production truth is established ONLY after:
 *  - Two physical devices verify discovery & transfer
 *  - Radio lifecycle and process death are handled cleanly
 *  - Power/battery consumption is profiled and within limits
 * ══════════════════════════════════════════════════════════════════════
 */

interface MeshDiscoveryTransport {
    val discoveredPeers: Flow<MeshAdvertisement>
    suspend fun startAdvertising(advertisement: MeshAdvertisement)
    suspend fun startScanning()
    suspend fun stop()
}

interface MeshLink {
    val peerId: String
    suspend fun send(envelope: ByteArray)
    val inbound: Flow<ByteArray>
    suspend fun close()
}

interface MeshLinkFactory {
    suspend fun connect(peer: MeshAdvertisement): MeshLink
}

/**
 * BLE bearer: dedicated ONLY to discovery, small control frames,
 * and custody receipts. NOT for high-bandwidth media or live voice calls.
 * STATUS: DESIGNED (No active radio I/O in simulator mode)
 */
class AndroidBleDiscoveryTransport : MeshDiscoveryTransport {
    override val discoveredPeers: Flow<MeshAdvertisement> = emptyFlow()
    override suspend fun startAdvertising(advertisement: MeshAdvertisement) {
        // Pending physical Android BluetoothLeAdvertiser implementation
    }
    override suspend fun startScanning() {
        // Pending physical Android BluetoothLeScanner implementation
    }
    override suspend fun stop() {
        // No-op until physical bearer is bound
    }
}

/**
 * Wi-Fi Aware bearer: high-bandwidth neighbor-aware networking.
 * STATUS: DESIGNED
 */
class AndroidWifiAwareTransport : MeshDiscoveryTransport {
    override val discoveredPeers: Flow<MeshAdvertisement> = emptyFlow()
    override suspend fun startAdvertising(advertisement: MeshAdvertisement) {}
    override suspend fun startScanning() {}
    override suspend fun stop() {}
}

/**
 * Wi-Fi Direct bearer: peer-to-peer payload and evidence chunk delivery.
 * STATUS: DESIGNED
 */
class AndroidWifiDirectTransport : MeshDiscoveryTransport {
    override val discoveredPeers: Flow<MeshAdvertisement> = emptyFlow()
    override suspend fun startAdvertising(advertisement: MeshAdvertisement) {}
    override suspend fun startScanning() {}
    override suspend fun stop() {}
}

/**
 * Local Area Network bearer: multicast / socket transport on shared subnet.
 * STATUS: DESIGNED
 */
class AndroidLanTransport : MeshDiscoveryTransport {
    override val discoveredPeers: Flow<MeshAdvertisement> = emptyFlow()
    override suspend fun startAdvertising(advertisement: MeshAdvertisement) {}
    override suspend fun startScanning() {}
    override suspend fun stop() {}
}
