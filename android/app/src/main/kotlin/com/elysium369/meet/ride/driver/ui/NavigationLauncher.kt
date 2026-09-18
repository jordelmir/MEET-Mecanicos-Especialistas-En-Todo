package com.elysium369.meet.ride.driver.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object NavigationLauncher {

    /**
     * Attempts to launch turn-by-turn navigation to [latitude], [longitude] in Waze.
     * Falls back to Google Maps, generic geo viewer, or Waze Web if Waze is not installed.
     */
    fun launchWaze(
        context: Context,
        latitude: Double,
        longitude: Double,
        label: String = "Destino",
    ) {
        val packageManager = context.packageManager

        // 1. Try launching native Waze directly
        val wazeUri = Uri.parse("waze://?ll=$latitude,$longitude&navigate=yes")
        val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (wazeIntent.resolveActivity(packageManager) != null) {
            try {
                context.startActivity(wazeIntent)
                return
            } catch (_: Exception) {}
        }

        // 2. Try Google Maps navigation
        val gmapsUri = Uri.parse("google.navigation:q=$latitude,$longitude&mode=d")
        val gmapsIntent = Intent(Intent.ACTION_VIEW, gmapsUri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (gmapsIntent.resolveActivity(packageManager) != null) {
            try {
                context.startActivity(gmapsIntent)
                return
            } catch (_: Exception) {}
        }

        // 3. Try generic Geo intent (which lets user choose installed navigation app)
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
        val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (geoIntent.resolveActivity(packageManager) != null) {
            try {
                context.startActivity(geoIntent)
                return
            } catch (_: Exception) {}
        }

        // 4. Web fallback to Waze Live Map
        try {
            val webUri = Uri.parse("https://www.waze.com/ul?ll=$latitude,$longitude&navigate=yes")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se encontró una aplicación de mapas o navegador", Toast.LENGTH_SHORT).show()
        }
    }
}
