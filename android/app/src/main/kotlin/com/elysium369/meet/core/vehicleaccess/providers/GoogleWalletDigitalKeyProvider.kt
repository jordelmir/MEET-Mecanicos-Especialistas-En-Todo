package com.elysium369.meet.core.vehicleaccess.providers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.vehicleaccess.domain.*

/**
 * Google Wallet Digital Car Key Integration & Orchestration Provider.
 * 
 * Safety & Standards:
 * - Adheres strictly to Google Play Services & Car Connectivity Consortium (CCC) principles.
 * - Does NOT attempt to synthesize unauthorized private keys.
 * - Detects eligibility (Android 12+, GMS, Screen Lock, NFC/UWB).
 * - Launches authorized OEM/Wallet handoff flows.
 */
class GoogleWalletDigitalKeyProvider(private val context: Context) {

    sealed class ProvisioningResult {
        data class HandoffInitiated(val intent: Intent, val sessionRef: String) : ProvisioningResult()
        data class Unsupported(val reason: String) : ProvisioningResult()
    }

    /**
     * Checks if the phone & vehicle satisfy prerequisites for Google Wallet Car Key.
     */
    fun evaluateEligibility(phone: PhoneAccessCapabilities, vehicle: VehicleAccessCapabilities?): CapabilityState {
        if (!phone.walletAvailability || !phone.hasSecureScreenLock || phone.androidVersion < 31) {
            return CapabilityState.UNSUPPORTED
        }
        if (vehicle == null) {
            return CapabilityState.UNKNOWN
        }
        return vehicle.walletProvisioningSupport
    }

    /**
     * Prepares an authorized pairing handoff intent for Google Wallet.
     */
    fun beginProvisioningHandoff(
        vehicleId: String,
        vin: String?,
        make: String,
        pairingCode: String?
    ): ProvisioningResult {
        val phoneCaps = PhoneAccessCapabilityDetector.detect(context)
        if (!phoneCaps.walletAvailability) {
            return ProvisioningResult.Unsupported("Google Play Services / Wallet no disponible en este dispositivo.")
        }
        if (!phoneCaps.hasSecureScreenLock) {
            return ProvisioningResult.Unsupported("Se requiere bloqueo seguro de pantalla (PIN/Huella/Patrón) para configurar Digital Car Key.")
        }

        val sessionProof = HashEngine.sha256Hex("WALLET_PROVISION:$vehicleId:$vin:${System.currentTimeMillis()}")
        
        // Launch standard GMS Wallet car key provisioning action or OEM DeepLink
        val intent = Intent("com.google.android.gms.pay.action.DIGITAL_CAR_KEY_PROVISION").apply {
            setPackage("com.google.android.gms")
            putExtra("EXTRA_VEHICLE_ID", vehicleId)
            putExtra("EXTRA_VIN", vin ?: "")
            putExtra("EXTRA_MAKE", make)
            if (!pairingCode.isNullOrBlank()) {
                putExtra("EXTRA_PAIRING_CODE", pairingCode)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Verify if GMS can resolve this intent, otherwise fallback to standard Wallet URL
        val pm = context.packageManager
        val canResolve = intent.resolveActivity(pm) != null
        val finalIntent = if (canResolve) {
            intent
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://pay.google.com/gp/v/car_keys")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return ProvisioningResult.HandoffInitiated(finalIntent, sessionProof)
    }
}
