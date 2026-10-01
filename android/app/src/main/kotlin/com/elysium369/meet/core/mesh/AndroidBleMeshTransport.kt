package com.elysium369.meet.core.mesh

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native BLE advertisements contain only random rotating UUIDs; GATT is capped to control frames.
 * Radio connections remain untrusted until MeshCryptoSuite authenticates every envelope/receipt.
 */
@SuppressLint("MissingPermission")
class AndroidBleDiscoveryTransport(context: Context) : MeshDiscoveryTransport, MeshLinkFactory {
    private val manager = context.applicationContext.getSystemService(BluetoothManager::class.java)
    private val peers = MutableSharedFlow<MeshPeerAdvertisement>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val discoveredPeers: Flow<MeshPeerAdvertisement> = peers.asSharedFlow()
    private val accepted = MutableSharedFlow<MeshLink>(extraBufferCapacity = 4)
    val incomingLinks: Flow<MeshLink> = accepted.asSharedFlow()
    private var advertising = false
    private var scanning = false
    private var server: BluetoothGattServer? = null
    private var control: BluetoothGattCharacteristic? = null
    private var serviceReady = CompletableDeferred<Int>()
    private val serverLinks = ConcurrentHashMap<String, BleServerLink>()
    private val outbound = ConcurrentHashMap<String, MeshLink>()
    private val radioBudget = MeshLinkBudget()
    private val advertiseResult = MutableStateFlow<Int?>(null)
    val radioError = MutableStateFlow<String?>(null)
    private val advertiser = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) { advertiseResult.value = 0 }
        override fun onStartFailure(errorCode: Int) { advertiseResult.value = errorCode; radioError.value = "BLE_ADVERTISE_$errorCode" }
    }
    private val scanner = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val data = result.scanRecord?.getServiceData(ParcelUuid(SERVICE)) ?: return
            if (data.size != 8) return
            val input = ByteBuffer.wrap(data)
            peers.tryEmit(MeshPeerAdvertisement(java.lang.Long.toHexString(input.long).padStart(16, '0'), MeshBearer.BLE, result.device.address, System.currentTimeMillis()))
        }
        override fun onScanFailed(errorCode: Int) { radioError.value = "BLE_SCAN_$errorCode" }
    }
    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) { serviceReady.complete(status) }
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                val lease = radioBudget.reserve()
                if (lease == null || (!advertising && !scanning)) { lease?.close(); server?.cancelConnection(device); return }
                val link = BleServerLink(device, lease)
                if (serverLinks.putIfAbsent(device.address, link) != null) { lease.close(); server?.cancelConnection(device); return }
                if (!accepted.tryEmit(link)) { serverLinks.remove(device.address); server?.cancelConnection(device) }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) serverLinks.remove(device.address)?.disconnected()
        }
        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) { serverLinks[device.address]?.maxFrame = minOf(512, mtu - 3) }
        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val link = serverLinks[device.address]
            val ok = characteristic.uuid == CONTROL && !preparedWrite && offset == 0 && link != null && value.size in 1..link.maxFrame && link.offer(value)
            if (responseNeeded) server?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
        }
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val link = serverLinks[device.address]
            val ok = descriptor.uuid == CCC && !preparedWrite && offset == 0 && value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) && link != null
            if (ok) link?.subscribed = true
            if (responseNeeded) server?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
        }
        override fun onNotificationSent(device: BluetoothDevice, status: Int) { serverLinks[device.address]?.sent(status) }
    }
    override suspend fun startAdvertising(rotatingId: UUID) {
        check(manager.adapter?.isEnabled == true) { "BLE_RADIO_DISABLED" }
        if (advertising) manager.adapter.bluetoothLeAdvertiser?.stopAdvertising(advertiser)
        if (server == null) {
            serviceReady = CompletableDeferred()
            server = manager.openGattServer(context, serverCallback) ?: error("BLE_GATT_UNAVAILABLE")
            control = BluetoothGattCharacteristic(CONTROL, BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_WRITE)
            control!!.addDescriptor(BluetoothGattDescriptor(CCC, BluetoothGattDescriptor.PERMISSION_WRITE))
            val service = BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).also { it.addCharacteristic(control) }
            check(server!!.addService(service)) { "BLE_SERVICE_REGISTRATION_FAILED" }
            check(withTimeout(10_000) { serviceReady.await() } == BluetoothGatt.GATT_SUCCESS) { "BLE_SERVICE_REGISTRATION_FAILED" }
        }
        val bytes = ByteBuffer.allocate(8).putLong(rotatingId.mostSignificantBits).array()
        val data = AdvertiseData.Builder().addServiceData(ParcelUuid(SERVICE), bytes).build()
        advertiseResult.value = null
        val native = manager.adapter.bluetoothLeAdvertiser ?: error("BLE_ADVERTISING_UNSUPPORTED")
        native.startAdvertising(AdvertiseSettings.Builder().setConnectable(true).setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER).build(), data, advertiser)
        advertising = true
        try { check(withTimeout(10_000) { advertiseResult.filterNotNull().first() } == 0) { "BLE_ADVERTISING_FAILED" } }
        catch (e: Exception) { native.stopAdvertising(advertiser); advertising = false; throw e }
    }
    private val context = context.applicationContext
    override suspend fun startScanning() {
        if (scanning) return
        val native = manager.adapter?.bluetoothLeScanner ?: error("BLE_SCANNING_UNAVAILABLE")
        native.startScan(listOf(ScanFilter.Builder().setServiceData(ParcelUuid(SERVICE), byteArrayOf()).build()), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), scanner)
        scanning = true
    }
    override suspend fun stop() {
        if (scanning) runCatching { manager.adapter?.bluetoothLeScanner?.stopScan(scanner) }
        if (advertising) runCatching { manager.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiser) }
        scanning = false; advertising = false
        outbound.values.toList().forEach { runCatching { it.close() } }; outbound.clear()
        serverLinks.values.toList().forEach { runCatching { it.close() } }; serverLinks.clear()
        runCatching { server?.close() }; server = null; control = null
    }
    override suspend fun connect(peer: MeshPeerAdvertisement): MeshLink {
        require(peer.bearer == MeshBearer.BLE)
        check(scanning || advertising) { "BLE_STOPPED" }
        val reservation = radioBudget.reserve() ?: error("BLE_LINK_CAPACITY")
        try {
            val device = manager.adapter.getRemoteDevice(peer.address)
            val link = BleClientLink(device, peer.rotatingId, reservation)
            if (outbound.putIfAbsent(peer.address, link) != null) { link.close(); error("BLE_LINK_ALREADY_PENDING") }
            link.open()
            return link
        } catch (e: Exception) { reservation.close(); throw e }
    }
    private inner class BleServerLink(private val device: BluetoothDevice, private val reservation: MeshLinkBudget.Lease) : MeshLink {
        override val peerId = device.address
        override val bearer = MeshBearer.BLE
        private val incoming = Channel<ByteArray>(8)
        override val inbound: Flow<ByteArray> = incoming.receiveAsFlow()
        var maxFrame = 20
        var subscribed = false
        private val lock = Mutex()
        private var pending: CompletableDeferred<Int>? = null
        fun offer(bytes: ByteArray) = incoming.trySend(bytes.copyOf()).isSuccess
        fun sent(status: Int) { pending?.complete(status) }
        fun disconnected() { reservation.close(); incoming.close(); pending?.completeExceptionally(IllegalStateException("BLE_DISCONNECTED")) }
        override suspend fun send(frame: ByteArray) = lock.withLock {
            require(frame.size in 1..maxFrame); check(subscribed) { "BLE_NOT_SUBSCRIBED" }
            val characteristic = control ?: error("BLE_STOPPED")
            val completion = CompletableDeferred<Int>(); pending = completion
            try {
                characteristic.value = frame.copyOf()
                check(server?.notifyCharacteristicChanged(device, characteristic, false) == true)
                check(withTimeout(10_000) { completion.await() } == BluetoothGatt.GATT_SUCCESS)
            } finally { pending = null }
        }
        override suspend fun close() { disconnected(); server?.cancelConnection(device); serverLinks.remove(device.address, this) }
    }
    private inner class BleClientLink(private val device: BluetoothDevice, override val peerId: String, private val reservation: MeshLinkBudget.Lease) : MeshLink {
        override val bearer = MeshBearer.BLE
        private val incoming = Channel<ByteArray>(8)
        override val inbound: Flow<ByteArray> = incoming.receiveAsFlow()
        private val ready = CompletableDeferred<Unit>()
        private var gatt: BluetoothGatt? = null
        private var characteristic: BluetoothGattCharacteristic? = null
        private var maxFrame = 20
        private val sendLock = Mutex()
        private var write: CompletableDeferred<Int>? = null
        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                    if (!g.requestMtu(517) && !g.discoverServices()) ready.completeExceptionally(IllegalStateException("BLE_DISCOVERY_FAILED"))
                } else if (state == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    ready.completeExceptionally(IllegalStateException("BLE_CONNECTION_FAILED")); write?.completeExceptionally(IllegalStateException("BLE_DISCONNECTED")); g.close(); outbound.remove(device.address, this@BleClientLink); reservation.close()
                }
            }
            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) maxFrame = minOf(512, mtu - 3)
                if (!g.discoverServices()) ready.completeExceptionally(IllegalStateException("BLE_DISCOVERY_FAILED"))
            }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val c = g.getService(SERVICE)?.getCharacteristic(CONTROL)
                val descriptor = c?.getDescriptor(CCC)
                if (status != BluetoothGatt.GATT_SUCCESS || c == null || descriptor == null || !g.setCharacteristicNotification(c, true)) { ready.completeExceptionally(IllegalStateException("BLE_CONTROL_UNAVAILABLE")); return }
                characteristic = c; descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!g.writeDescriptor(descriptor)) ready.completeExceptionally(IllegalStateException("BLE_SUBSCRIBE_FAILED"))
            }
            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) ready.complete(Unit) else ready.completeExceptionally(IllegalStateException("BLE_SUBSCRIBE_FAILED"))
            }
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { if (c.uuid == CONTROL && c.value.size in 1..maxFrame) incoming.trySend(c.value.copyOf()) }
            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { write?.complete(status) }
        }
        suspend fun open() {
            try { gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE); withTimeout(20_000) { ready.await() } }
            catch (e: Exception) { close(); throw e }
        }
        override suspend fun send(frame: ByteArray) = sendLock.withLock {
            require(frame.size in 1..maxFrame)
            val c = characteristic ?: error("BLE_NOT_CONNECTED")
            val completion = CompletableDeferred<Int>(); write = completion
            try { c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; c.value = frame.copyOf(); check(gatt?.writeCharacteristic(c) == true); check(withTimeout(10_000) { completion.await() } == BluetoothGatt.GATT_SUCCESS) }
            finally { write = null }
        }
        override suspend fun close() { reservation.close(); gatt?.disconnect(); gatt?.close(); gatt = null; outbound.remove(device.address, this); write?.cancel(); ready.cancel() }
    }
    companion object {
        // 128-bit service data + 8-byte random token fits legacy 31-byte advertising; no assigned vendor UUID.
        private val SERVICE = UUID.fromString("a46b8800-c31f-4d33-a3d9-9c0e8752b5fa")
        private val CONTROL = UUID.fromString("a46b8801-c31f-4d33-a3d9-9c0e8752b5fa")
        private val CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
