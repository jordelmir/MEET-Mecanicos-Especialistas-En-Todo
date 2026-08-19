package com.elysium369.meet.core.vehicleaccess.domain

import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build

object PhoneAccessCapabilityDetector {

    fun detect(context: Context): PhoneAccessCapabilities {
        val pm = context.packageManager

        // 1. NFC and HCE (Host Card Emulation)
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        val hasNfc = nfcAdapter != null && pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        val hasHce = hasNfc && pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

        // 2. Bluetooth LE & BLE Advertising
        val hasBle = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        val canAdvertiseBle = hasBle && (btAdapter?.isMultipleAdvertisementSupported == true)

        // 3. Ultra-Wideband (UWB) - Android 12+ (API 31+)
        val hasUwb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pm.hasSystemFeature("android.hardware.uwb")
        } else false

        // 4. Secure Screen Lock & Biometrics
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val hasSecureLock = keyguardManager?.isKeyguardSecure == true

        // 5. Google Wallet / GMS Availability
        val hasGms = runCatching {
            pm.getPackageInfo("com.google.android.gms", 0)
            true
        }.getOrDefault(false)

        return PhoneAccessCapabilities(
            hasNfc = hasNfc,
            hasHce = hasHce,
            hasBle = hasBle,
            canAdvertiseBle = canAdvertiseBle,
            hasUwb = hasUwb,
            hasSecureScreenLock = hasSecureLock,
            androidVersion = Build.VERSION.SDK_INT,
            walletAvailability = hasGms
        )
    }
}
