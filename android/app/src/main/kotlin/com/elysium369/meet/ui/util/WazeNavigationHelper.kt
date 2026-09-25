package com.elysium369.meet.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ══════════════════════════════════════════════════════════════════════
 *  W A Z E   N A V I G A T I O N   H E L P E R
 *  ──────────────────────────────────────────────────────────────
 *  Permite a clientes, choferes, mecánicos y repartidores viajar
 *  directamente al lugar donde se solicitan o brindan los servicios
 *  con un solo click usando Waze (o fallback a Google Maps / Web).
 * ══════════════════════════════════════════════════════════════════════
 */
object WazeNavigationHelper {

    /**
     * Lanza Waze con navegación en curso hacia las coordenadas especificadas.
     * Si Waze no está instalado, recurre automáticamente a Google Maps o navegador web.
     */
    fun openWaze(
        context: Context,
        latitude: Double,
        longitude: Double,
        label: String = "Destino del Servicio"
    ) {
        if (latitude == 0.0 && longitude == 0.0) {
            Toast.makeText(context, "Ubicación GPS no disponible todavía.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Protocolo nativo de Waze para navegación guiada en vivo
            val wazeUri = Uri.parse("waze://?ll=$latitude,$longitude&navigate=yes")
            val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(wazeIntent)
        } catch (_: Exception) {
            try {
                // Fallback 1: Intent Geo estándar (Google Maps u otra app de navegación instalada)
                val encodedLabel = Uri.encode(label)
                val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
                val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(geoIntent)
            } catch (_: Exception) {
                try {
                    // Fallback 2: Waze Live Map Web
                    val webUri = Uri.parse("https://www.waze.com/ul?ll=$latitude,$longitude&navigate=yes")
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(webIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, "No se pudo iniciar la navegación.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * Botón interactivo de navegación Waze con estética cyber-navigational.
 */
@Composable
fun WazeNavigationButton(
    latitude: Double = 0.0,
    longitude: Double = 0.0,
    label: String = "Destino",
    destinationLat: Double = latitude,
    destinationLng: Double = longitude,
    destinationLabel: String = label,
    modifier: Modifier = Modifier,
    text: String = "Viajar con Waze 🚗💨",
    compact: Boolean = false,
) {
    val finalLat = if (destinationLat != 0.0) destinationLat else latitude
    val finalLng = if (destinationLng != 0.0) destinationLng else longitude
    val finalLabel = if (destinationLabel != "Destino") destinationLabel else label
    val context = LocalContext.current
    Button(
        onClick = {
            WazeNavigationHelper.openWaze(context, finalLat, finalLng, finalLabel)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF33CCFF), // Waze Cyan
            contentColor = Color(0xFF002233)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = "Waze",
            tint = Color(0xFF002233),
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontWeight = FontWeight.Black,
            fontSize = if (compact) 11.sp else 12.sp,
            color = Color(0xFF002233)
        )
    }
}
