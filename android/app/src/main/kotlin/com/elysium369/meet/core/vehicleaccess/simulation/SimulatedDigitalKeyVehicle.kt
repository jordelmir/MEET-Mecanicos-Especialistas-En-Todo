package com.elysium369.meet.core.vehicleaccess.simulation

import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.vehicleaccess.domain.*
import com.elysium369.meet.core.vehicleaccess.security.AccessGrantEngine

/**
 * End-to-end Simulator for Digital Key, NFC HCE, BLE Proximity, and Grant Verification.
 * Used for automated integration tests and laboratory validation.
 */
class SimulatedDigitalKeyVehicle(
    val vehicleId: String = "SIM-VANGUARD-001",
    val vin: String = "1G1SIM00000000001",
    var isDoorsLocked: Boolean = true,
    var isEngineRunning: Boolean = false
) {

    private val registeredCredentialHashes = mutableSetOf<String>()

    fun registerCredential(proofHash: String) {
        registeredCredentialHashes.add(proofHash)
    }

    fun handleNfcTap(credentialPayload: String): Boolean {
        val hash = HashEngine.sha256Hex(credentialPayload)
        if (registeredCredentialHashes.contains(hash)) {
            isDoorsLocked = !isDoorsLocked
            return true
        }
        return false
    }

    fun handleBleCommand(
        grant: AccessGrant,
        requestedPermission: AccessPermission,
        messageProof: String
    ): Boolean {
        val auth = AccessGrantEngine.evaluate(grant, requestedPermission)
        if (auth !is AccessGrantEngine.AuthorizationResult.Granted) {
            return false
        }

        return when (requestedPermission) {
            AccessPermission.ENTRY -> {
                isDoorsLocked = false
                true
            }
            AccessPermission.DRIVE -> {
                if (!isDoorsLocked) {
                    isEngineRunning = true
                    true
                } else false
            }
            AccessPermission.TRUNK -> true
            AccessPermission.CHARGE -> true
            AccessPermission.REMOTE_COMMANDS -> true
            AccessPermission.DIAGNOSTICS -> true
        }
    }
}
