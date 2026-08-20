package com.elysium369.meet.core.obd

import android.content.Context
import android.content.SharedPreferences

class AdapterFingerprint(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("obd_profiles", Context.MODE_PRIVATE)

    fun saveProfile(address: String, profile: ElmNegotiator.AdapterProfile, vin: String? = null) {
        val activeVin = vin ?: profile.vin
        prefs.edit().apply {
            putString("${address}_chip", profile.chipVersion)
            putBoolean("${address}_clone", profile.isClone)
            putString("${address}_protocol", profile.detectedProtocol.name)
            profile.ecuHeader?.let { putString("${address}_header", it) }
            profile.recipeId?.let { putString("${address}_recipe", it) }
            if (profile.initCommands.isNotEmpty()) {
                putString("${address}_init_cmds", profile.initCommands.joinToString(","))
            }
            if (!activeVin.isNullOrBlank() && activeVin != "N/A") {
                putString("${address}_vin", activeVin)
                putString("vin_${activeVin}_protocol", profile.detectedProtocol.name)
                profile.ecuHeader?.let { putString("vin_${activeVin}_header", it) }
                profile.recipeId?.let { putString("vin_${activeVin}_recipe", it) }
                if (profile.initCommands.isNotEmpty()) {
                    putString("vin_${activeVin}_init_cmds", profile.initCommands.joinToString(","))
                }
            }
            apply()
        }
    }

    fun getProfile(address: String, vin: String? = null): ElmNegotiator.AdapterProfile? {
        val chip = prefs.getString("${address}_chip", null) ?: "ELM327"
        val isClone = prefs.getBoolean("${address}_clone", true)
        
        val protocolName = if (!vin.isNullOrBlank() && prefs.contains("vin_${vin}_protocol")) {
            prefs.getString("vin_${vin}_protocol", null)
        } else {
            prefs.getString("${address}_protocol", null)
        } ?: return null

        val protocol = try { ObdProtocol.valueOf(protocolName) } catch (_: Exception) { ObdProtocol.AUTO }
        val header = if (!vin.isNullOrBlank() && prefs.contains("vin_${vin}_header")) {
            prefs.getString("vin_${vin}_header", null)
        } else {
            prefs.getString("${address}_header", null)
        }
        val recipeId = if (!vin.isNullOrBlank() && prefs.contains("vin_${vin}_recipe")) {
            prefs.getString("vin_${vin}_recipe", null)
        } else {
            prefs.getString("${address}_recipe", null)
        }
        val initCmdsStr = if (!vin.isNullOrBlank() && prefs.contains("vin_${vin}_init_cmds")) {
            prefs.getString("vin_${vin}_init_cmds", "") ?: ""
        } else {
            prefs.getString("${address}_init_cmds", "") ?: ""
        }
        val initCmds = if (initCmdsStr.isNotBlank()) initCmdsStr.split(",").filter { it.isNotBlank() } else emptyList()

        return ElmNegotiator.AdapterProfile(
            chipVersion = chip,
            isClone = isClone,
            isSTN = chip.contains("STN", true) || chip.contains("vLinker", true),
            detectedProtocol = protocol,
            baseDelayMs = if (isClone) 70L else 20L,
            maxLineLength = if (isClone) 64 else 512,
            ecuHeader = header,
            initCommands = initCmds,
            recipeId = recipeId,
            vin = vin ?: prefs.getString("${address}_vin", null)
        )
    }

    fun invalidateProfile(address: String) {
        prefs.edit()
            .remove("${address}_chip")
            .remove("${address}_clone")
            .remove("${address}_protocol")
            .remove("${address}_header")
            .remove("${address}_recipe")
            .remove("${address}_init_cmds")
            .apply()
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

