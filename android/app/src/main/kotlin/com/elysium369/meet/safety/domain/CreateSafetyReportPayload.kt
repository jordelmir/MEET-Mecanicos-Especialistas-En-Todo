package com.elysium369.meet.safety.domain

import kotlinx.serialization.Serializable

@Serializable
data class CreateSafetyReportPayload(
    val category: SafetyReportCategory,
    val narrative: String,
    val sourceRelation: SourceRelation,
    val occurredAtIso: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val locationSource: LocationSource = LocationSource.NONE,
    // V2 — Victim demographics (optional, for HOMICIDE category)
    val reportedVictimCount: Int? = null,
    val reportedVictimFemale: Int? = null,
    val reportedVictimMale: Int? = null,
)

@Serializable
enum class LocationSource {
    NONE,
    DEVICE,
    MAP_SELECTION,
    USER_DESCRIPTION,
}

fun SafetyReportCategory.label(): String = when (this) {
    SafetyReportCategory.HOMICIDE -> "Homicidio"
    SafetyReportCategory.VIOLENT_INCIDENT -> "Incidente violento"
    SafetyReportCategory.DRUG_SALE_ACTIVITY -> "Actividad reportada relacionada con drogas"
    SafetyReportCategory.THREAT -> "Amenaza"
    SafetyReportCategory.MISSING_PERSON -> "Persona desaparecida"
    SafetyReportCategory.INSTITUTIONAL_CONDUCT -> "Actuación institucional"
    SafetyReportCategory.OTHER -> "Otro"
}

fun SourceRelation.label(): String = when (this) {
    SourceRelation.DIRECT_WITNESS -> "Lo presencié (Civil)"
    SourceRelation.FAMILY_OR_NEIGHBOR -> "Familiar / vecino (Civil)"
    SourceRelation.SECOND_HAND -> "Me lo comunicaron (Civil)"
    SourceRelation.DOCUMENTARY -> "Fuente documental (Documentos / Grabación)"
    SourceRelation.JOURNALISTIC -> "Fuente periodística (Prensa / Investigación)"
    SourceRelation.PUBLIC_RECORD -> "Registro público (Judicial / Notarial)"
    SourceRelation.INSTITUTIONAL -> "Fuente institucional (Fuerza Pública / OIJ / Oficial)"
    SourceRelation.UNKNOWN -> "Otra fuente protegida"
}

enum class SafetySourceBadgeType(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val badgeColorHex: Long
) {
    CIVIL("Fuente Civil (Anónima)", "Testimonio ciudadano protegido", "🛡️", 0xFF00E5FF),
    JOURNALISTIC("Fuente Periodística", "Investigación periodística / medios", "📰", 0xFF69F0AE),
    PUBLIC_RECORD("Registro Público", "Expediente judicial o registral", "🏛️", 0xFFFFD700),
    DOCUMENTARY("Fuente Documental", "Evidencia física, video o peritaje", "📄", 0xFFFF9100),
    INSTITUTIONAL("Fuente Institucional", "Autoridad u órgano del estado", "🏢", 0xFFB388FF),
    ANONYMOUS("Fuente Protegida", "Reporte anónimo cifrado", "🔒", 0xFF80D8FF)
}

fun SourceRelation.toObservatoryBadge(): SafetySourceBadgeType = when (this) {
    SourceRelation.DIRECT_WITNESS,
    SourceRelation.FAMILY_OR_NEIGHBOR,
    SourceRelation.SECOND_HAND -> SafetySourceBadgeType.CIVIL
    SourceRelation.JOURNALISTIC -> SafetySourceBadgeType.JOURNALISTIC
    SourceRelation.PUBLIC_RECORD -> SafetySourceBadgeType.PUBLIC_RECORD
    SourceRelation.DOCUMENTARY -> SafetySourceBadgeType.DOCUMENTARY
    SourceRelation.INSTITUTIONAL -> SafetySourceBadgeType.INSTITUTIONAL
    SourceRelation.UNKNOWN -> SafetySourceBadgeType.ANONYMOUS
}

