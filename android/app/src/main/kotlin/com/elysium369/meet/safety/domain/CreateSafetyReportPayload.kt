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
    SourceRelation.DIRECT_WITNESS -> "Lo presencié"
    SourceRelation.FAMILY_OR_NEIGHBOR -> "Familiar / vecino"
    SourceRelation.SECOND_HAND -> "Me lo comunicaron"
    SourceRelation.DOCUMENTARY -> "Tengo documentos"
    SourceRelation.JOURNALISTIC -> "Fuente periodística"
    SourceRelation.PUBLIC_RECORD -> "Registro público"
    SourceRelation.UNKNOWN -> "Otra"
}
