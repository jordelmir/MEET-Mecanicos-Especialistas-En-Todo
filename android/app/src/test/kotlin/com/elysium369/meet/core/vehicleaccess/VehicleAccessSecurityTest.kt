package com.elysium369.meet.core.vehicleaccess

import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.vehicleaccess.domain.*
import com.elysium369.meet.core.vehicleaccess.security.AccessGrantEngine
import com.elysium369.meet.core.vehicleaccess.simulation.SimulatedDigitalKeyVehicle
import com.elysium369.meet.core.vehicleaccess.transport.BleAccessTransport
import org.junit.Assert.*
import org.junit.Test

class VehicleAccessSecurityTest {

    @Test
    fun testExpiredGrantIsDenied() {
        val now = System.currentTimeMillis()
        val expiredGrant = AccessGrant(
            vehicleId = "V-TEST-01",
            recipientName = "Conductor Temporal",
            recipientRole = "Familiar",
            permissions = setOf(AccessPermission.ENTRY, AccessPermission.DRIVE),
            validFromEpochMs = now - 100000L,
            validUntilEpochMs = now - 5000L // Already expired
        )

        val result = AccessGrantEngine.evaluate(expiredGrant, AccessPermission.ENTRY, currentEpochMs = now)
        assertTrue(result is AccessGrantEngine.AuthorizationResult.Denied)
        assertEquals("El permiso de acceso ha expirado.", (result as AccessGrantEngine.AuthorizationResult.Denied).reason)
    }

    @Test
    fun testRevokedGrantIsDenied() {
        val now = System.currentTimeMillis()
        val revokedGrant = AccessGrant(
            vehicleId = "V-TEST-01",
            recipientName = "Valet Parking",
            recipientRole = "Valet Parking",
            permissions = setOf(AccessPermission.ENTRY),
            validFromEpochMs = now - 1000L,
            validUntilEpochMs = now + 3600000L,
            status = CredentialStatus.REVOKED,
            revocationReason = "Servicio terminado"
        )

        val result = AccessGrantEngine.evaluate(revokedGrant, AccessPermission.ENTRY, currentEpochMs = now)
        assertTrue(result is AccessGrantEngine.AuthorizationResult.Denied)
        assertTrue((result as AccessGrantEngine.AuthorizationResult.Denied).reason.contains("revocado"))
    }

    @Test
    fun testValetRoleDeniedTrunkAndRemoteCommands() {
        val now = System.currentTimeMillis()
        val valetGrant = AccessGrant(
            vehicleId = "V-TEST-01",
            recipientName = "Valet Hotel",
            recipientRole = "Valet Parking",
            permissions = setOf(AccessPermission.ENTRY, AccessPermission.DRIVE, AccessPermission.TRUNK),
            validFromEpochMs = now - 1000L,
            validUntilEpochMs = now + 3600000L
        )

        val trunkResult = AccessGrantEngine.evaluate(valetGrant, AccessPermission.TRUNK, currentEpochMs = now)
        assertTrue(trunkResult is AccessGrantEngine.AuthorizationResult.Denied)
        assertTrue((trunkResult as AccessGrantEngine.AuthorizationResult.Denied).reason.contains("Valet"))

        val entryResult = AccessGrantEngine.evaluate(valetGrant, AccessPermission.ENTRY, currentEpochMs = now)
        assertTrue(entryResult is AccessGrantEngine.AuthorizationResult.Granted)
    }

    @Test
    fun testBleTransportReplayAttackIsRejected() {
        val transport = BleAccessTransport()
        val vehicleId = "V-BLE-001"
        val signingProof = HashEngine.sha256Hex("KEY_SECRET_PROOF")

        val message = transport.createSecureMessage(vehicleId, "UNLOCK_DOORS", signingProof)

        // First verification must succeed
        val firstResult = transport.verifyAndConsumeMessage(message, signingProof)
        assertTrue("Primera verificación debe ser exitosa", firstResult)

        // Immediate replay of identical message (same nonce) must be REJECTED
        val replayResult = transport.verifyAndConsumeMessage(message, signingProof)
        assertFalse("Ataque de replay debe ser rechazado inmediatamente", replayResult)
    }

    @Test
    fun testSimulatedVehicleIntegrity() {
        val simVehicle = SimulatedDigitalKeyVehicle(vehicleId = "SIM-01")
        val payload = "MEET_DIGITAL_KEY_PAYLOAD_VALID"
        val proof = HashEngine.sha256Hex(payload)

        simVehicle.registerCredential(proof)

        assertTrue(simVehicle.isDoorsLocked)
        val unlocked = simVehicle.handleNfcTap(payload)
        assertTrue(unlocked)
        assertFalse(simVehicle.isDoorsLocked)

        // Invalid key tap fails
        val fakeResult = simVehicle.handleNfcTap("INVALID_KEY_PAYLOAD")
        assertFalse(fakeResult)
    }
}
