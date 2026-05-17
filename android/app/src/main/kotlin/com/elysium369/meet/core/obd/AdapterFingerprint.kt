package com.elysium369.meet.core.obd

import android.content.Context
import android.content.SharedPreferences

class AdapterFingerprint(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("obd_profiles", Context.MODE_PRIVATE)

    fun saveProfile(address: String, profile: ElmNegotiator.AdapterProfile) {
        prefs.edit().apply {
            putString("${address}_chip", profile.chipVersion)
            putBoolean("${address}_clone", profile.isClone)
            putString("${address}_protocol", profile.detectedProtocol.name)
            apply()
        }
    }

    fun getProfile(address: String): ElmNegotiator.AdapterProfile? {
        val chip = prefs.getString("${address}_chip", null) ?: return null
        val isClone = prefs.getBoolean("${address}_clone", true)
        val protocolName = prefs.getString("${address}_protocol", ObdProtocol.AUTO.name)
        val protocol = try { ObdProtocol.valueOf(protocolName!!) } catch (_: Exception) { ObdProtocol.AUTO }
        
        return ElmNegotiator.AdapterProfile(
            chipVersion = chip,
            isClone = isClone,
            isSTN = chip.contains("STN", true) || chip.contains("vLinker", true),
            detectedProtocol = protocol,
            baseDelayMs = if (isClone) 70L else 20L,
            maxLineLength = if (isClone) 64 else 512
        )
    }

    fun invalidateProfile(address: String) {
        prefs.edit().remove("${address}_chip").remove("${address}_clone").apply()
    }

    fun getRecommendedPids(profile: ElmNegotiator.AdapterProfile): List<String> {
        return if (profile.isClone) {
            listOf("010C", "010D", "0105", "0104") // Safe PIDs
        } else {
            // Return all supported based on 0100
            listOf("010C", "010D", "0105", "0104", "010B", "010E", "0111", "010F", "0110") 
        }
    }
}
