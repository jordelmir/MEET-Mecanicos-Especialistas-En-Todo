package com.elysium369.meet.safety.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray

/**
 * Representa una actualización o avistamiento cronológico subsiguiente vinculado a un reporte o caso.
 *
 * Principio Append-Only (Constitución Safety):
 * Nunca sobreescribe el relato original. Añade un nuevo hito inmutable a la línea de tiempo.
 *
 * Doble Marca de Tiempo Forense:
 * - occurredAt: Momento real en que los testigos observaron el suceso (fecha y hora de los hechos).
 * - recordedAt: Momento exacto de inscripción inmutable en el sistema (hora en que se grabó).
 *
 * Restricciones:
 * - Videos: Estrictamente URLs externas (videoUrlsJson), sin archivos de video locales para prevenir saturación.
 * - Narrativa: Límite extendido de hasta 100,000 caracteres para bitácoras procesales completas.
 */
@Entity(
    tableName = "safety_report_updates",
    indices = [
        Index(value = ["reportId"]),
        Index(value = ["occurredAt"]),
        Index(value = ["recordedAt"]),
    ],
)
data class SafetyReportUpdateEntity(
    @PrimaryKey
    val updateId: String,
    val reportId: String,
    val occurredAt: Long,
    val recordedAt: Long,
    val locationLabel: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val clothingAndFeatures: String,
    val narrative: String,
    val videoUrlsJson: String = "[]",
    val syncState: String = "SYNCED",
    val serverVersion: Long = 1L,
    val createdAt: Long = recordedAt,
) {
    fun getVideoUrls(): List<String> {
        return runCatching {
            val arr = JSONArray(videoUrlsJson)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                if (item.isNotBlank()) list.add(item)
            }
            list
        }.getOrDefault(emptyList())
    }

    companion object {
        fun encodeVideoUrls(urls: List<String>): String {
            val arr = JSONArray()
            urls.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { arr.put(it) }
            return arr.toString()
        }
    }
}
