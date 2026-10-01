package com.elysium.nexus.core.transport.elysium

import com.elysium.nexus.core.transport.mac.MacProtocol
import kotlinx.coroutines.flow.StateFlow

/**
 * The Elysium Link transport abstraction.
 *
 * A [ElysiumLinkTransport] is a bidirectional
 * encrypted channel between the Android
 * controller and a host. The transport is
 * responsible for:
 *
 * 1. **Discovery** — finding hosts on the
 *    network (mDNS, BLE, manual).
 * 2. **Pairing** — X25519 key exchange + PIN
 *    verification.
 * 3. **Encrypted I/O** — sending/receiving
 *    frames over the established channel.
 *
 * ## Implementations
 *
 * - [com.elysium.nexus.core.transport.mac.MacTransport]
 *   — Wi-Fi TCP transport for Mac/PC.
 * - Future: BLE transport for Nexus Receiver,
 *   USB transport for wired connections.
 *
 * ## Why an interface
 *
 * The interface is the testable seam. Unit
 * tests use a `FakeTransport` that returns
 * canned responses; the production impl
 * talks to real sockets.
 */
interface ElysiumLinkTransport {

    /** The transport's current state. */
    val state: StateFlow<TransportState>

    /** The remote host's name (if connected). */
    val remoteName: String?

    /**
     * Start the transport and begin discovery.
     */
    suspend fun start()

    /**
     * Connect to a specific host.
     *
     * @param host the host IP or hostname.
     * @param port the port number.
     */
    suspend fun connect(host: String, port: Int)

    /**
     * Send a frame to the remote host.
     *
     * @param type the frame type.
     * @param payload the frame payload.
     */
    suspend fun sendFrame(type: MacProtocol.FrameType, payload: ByteArray)

    /**
     * Receive the next frame from the remote host.
     * Returns null if the transport is closed.
     */
    suspend fun receiveFrame(): MacProtocol.Frame?

    /**
     * Disconnect from the remote host and
     * release all resources.
     */
    suspend fun disconnect()
}

/**
 * Transport lifecycle state.
 */
enum class TransportState {
    /** Not started. */
    Idle,
    /** Discovering hosts. */
    Discovering,
    /** Connecting to a host. */
    Connecting,
    /** Pairing (PIN verification). */
    Pairing,
    /** Encrypted session active. */
    Ready,
    /** Disconnecting. */
    Disconnecting,
    /** Released. */
    Released,
    /** An error occurred. */
    Error
}
