package com.elysium.nexus.fabric.tv.adb

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration test against a REAL adbd over TCP.
 *
 * Enabled only when a device is reachable at ADB_TEST_HOST
 * (e.g. an Android TV on the store LAN or the lab Honor
 * restarted with `adb tcpip 5555`). When the env var is
 * absent the test self-skips — the development grid never
 * depends on lab hardware.
 *
 * First connect uses a brand-new key, so the TV shows the
 * "Allow USB debugging" dialog; the watcher script (USB
 * adb) taps Allow on the lab device. The second connect
 * with the same key must settle via pure RSA SIGNATURE —
 * that is the repeat-use path for the product.
 */
class AdbWirelessRealAdbdTest {

    private val targetHost: String? = System.getenv("ADB_TEST_HOST")
        ?: System.getProperty("adb.test.host")

    /**
     * The key file survives across gradle runs, so the
     * "already authorized" SIGNATURE-only path (what the
     * product hits from the second pairing onwards) can be
     * exercised with the exact same key the TV accepted.
     */
    private fun persistentKey(): AdbAuthorization {
        val path = System.getenv("ADB_TEST_KEY_PATH") ?: "/tmp/kotlin_adb_key.pem"
        val file = java.io.File(path)
        return if (file.isFile) {
            checkNotNull(AdbAuthorization.loadFromPem(file.readText(Charsets.UTF_8)))
        } else {
            val fresh = AdbAuthorization.generate()
            file.writeText(fresh.toPem())
            fresh
        }
    }

    @Test
    fun realAdbd_pairing_then_signature_and_keyevent() {
        val host = targetHost ?: return
        val authorization = persistentKey()

        // 1) First contact: pairing dialog flow on the TV (human taps Allow).
        val client = AdbWirelessClient(host, AdbProtocol.PORT)
        println("phase1: connecting (pairing dialog expected on the TV)")
        client.connect(authorization, authorizationTimeoutMs = 120_000)
        println("phase1: authorized")
        println("device banner: ${client.deviceBanner?.take(160)}")
        val model = client.shell("getprop ro.product.model", authorization)
        println("adbd @ $host identified model=$model")
        assertTrue("Expected a model string from adbd, got '$model'", model.isNotBlank())

        // 2) Disconnect, reconnect: the previously announced key is now in
        //    /data/misc/adb/adb_keys — the plain SIGNATURE path must settle.
        client.disconnect()
        println("phase2: reconnecting via SIGNATURE (key now authorized)")
        val client2 = AdbWirelessClient(host, AdbProtocol.PORT)
        client2.connect(authorization, authorizationTimeoutMs = 20_000)
        println("phase2: authorized without dialog")
        val out = client2.shell("input keyevent 25", authorization) // VOLUME_DOWN
        println("input keyevent output='$out'")
        assertTrue("keyevent must not raise", !out.contains("error:", ignoreCase = true))
        client2.disconnect()
    }

    @Test
    fun header_roundTrips() {
        val h = AdbProtocol.Header(AdbProtocol.A_CNXN, AdbProtocol.VERSION, AdbProtocol.MAXDATA, 20)
        val bytes = h.toBytes()
        val back = AdbProtocol.Header.fromBytes(bytes)
        assertTrue(back.command == AdbProtocol.A_CNXN)
        assertTrue(back.arg0 == AdbProtocol.VERSION)
        assertTrue(back.arg1 == AdbProtocol.MAXDATA)
        assertTrue(back.dataLength == 20)
        assertTrue(back.magic == h.magic)
        assertTrue(h.magic != 0)
    }
}