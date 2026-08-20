package com.elysium369.meet.core.search

import com.elysium369.meet.core.domain.EntityRef

enum class SearchCategory(val title: String, val glyph: String) {
    VEHICLES("Vehículos & Garaje", "🚗"),
    DTCS("Códigos de Falla (DTC)", "⚠️"),
    FINDINGS("Hallazgos & Evidencias", "📑"),
    REPAIRS("Reparaciones & Guías", "🛠️"),
    COMPONENTS("Componentes & BOM", "⚙️"),
    DOCUMENTS("Guantera Digital & Pólizas", "📄"),
    MANUALS("Manuales & Especificaciones", "📚"),
    PARTS("Repuestos & Compatibilidad", "📦"),
    SERVICES("Talleres & Asistencia", "🧰"),
    TRIPS("Viajes & Telemetría", "🛣️")
}

data class SearchResult(
    val id: String,
    val title: String,
    val snippet: String,
    val category: SearchCategory,
    val destinationRoute: String,
    val entityRef: EntityRef,
    val matchScore: Float
)

interface MeetSearchProvider {
    val providerCategory: SearchCategory
    suspend fun search(query: String, vehicleId: String?): List<SearchResult>
}
