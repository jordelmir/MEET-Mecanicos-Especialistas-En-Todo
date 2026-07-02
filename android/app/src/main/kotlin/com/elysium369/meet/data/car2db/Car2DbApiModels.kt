package com.elysium369.meet.data.car2db

import com.elysium.vanguard.forge.domain.SafetyClassification
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Modelos JSON-LD para la API Car2DB v3.
 *
 * Car2DB responde con `application/ld+json` que envuelve cada recurso o colección
 * en un objeto con `@context`, `@type`, `@id`. Para colecciones, la lista viene
 * en `member` con metadatos de paginación en `view`.
 *
 * Estrategia:
 * - Modelos tipados para los recursos comunes (Make, Model, Trim, Specification).
 * - Modelo genérico [Car2DbCollection] para colecciones paginadas que no usamos directamente.
 * - Los campos opcionales usan JsonElement para tolerar cambios de schema sin romper el build.
 */

@Serializable
data class Car2DbCollection<T>(
    @SerialName("@context") val context: String? = null,
    @SerialName("@type") val type: String? = null,
    val member: List<T> = emptyList(),
    val totalItems: Int = 0,
    val view: Car2DbView? = null
)

@Serializable
data class Car2DbView(
    @SerialName("@id") val id: String? = null,
    val first: String? = null,
    val last: String? = null,
    val next: String? = null,
    val previous: String? = null
)

@Serializable
data class Car2DbError(
    val type: String? = null,
    val title: String? = null,
    val status: Int = 0,
    val detail: String? = null,
    val instance: String? = null
)

@Serializable
data class Car2DbMake(
    @SerialName("@id") val iri: String? = null,
    val id: Int = 0,
    val name: String = "",
    val slug: String = ""
)

@Serializable
data class Car2DbModel(
    @SerialName("@id") val iri: String? = null,
    val id: Int = 0,
    val name: String = "",
    val slug: String = ""
)

@Serializable
data class Car2DbTrim(
    @SerialName("@id") val iri: String? = null,
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
    val yearBegin: Int? = null,
    val yearEnd: Int? = null,
    val breadcrumbs: Car2DbBreadcrumbs? = null,
    val keySpecifications: Car2DbKeySpecs? = null,
    val specifications: List<Car2DbSpecGroup> = emptyList(),
    val equipments: List<Car2DbEquipment> = emptyList()
)

@Serializable
data class Car2DbBreadcrumbs(
    val make: Car2DbMake? = null,
    val model: Car2DbModel? = null,
    val generation: JsonElement? = null,
    val series: JsonElement? = null
)

@Serializable
data class Car2DbKeySpecs(
    val engineVolume: Double? = null,
    val power: Double? = null,
    val torque: Double? = null,
    val transmission: String? = null,
    val drivetrain: String? = null,
    val fuelType: String? = null,
    val bodyType: String? = null,
    val seats: Int? = null,
    val doors: Int? = null,
    val lengthMm: Double? = null,
    val widthMm: Double? = null,
    val heightMm: Double? = null,
    val wheelbaseMm: Double? = null,
    val curbWeightKg: Double? = null,
    val topSpeedKph: Double? = null,
    val acceleration0To100: Double? = null
)

@Serializable
data class Car2DbSpecGroup(
    val category: Car2DbCategory = Car2DbCategory(),
    val items: List<Car2DbSpecItem> = emptyList()
)

@Serializable
data class Car2DbCategory(
    val id: Int = 0,
    val name: String = ""
)

@Serializable
data class Car2DbSpecItem(
    val id: Int = 0,
    val name: String = "",
    val value: String = "",
    val unit: String? = null
)

@Serializable
data class Car2DbEquipment(
    @SerialName("@id") val iri: String? = null,
    val id: Int = 0,
    val name: String = "",
    val options: List<Car2DbOption> = emptyList()
)

@Serializable
data class Car2DbOption(
    val id: Int = 0,
    val name: String = "",
    val value: String = ""
)

@Serializable
data class Car2DbSearchResult(
    val results: List<Car2DbSearchModelGroup> = emptyList()
)

@Serializable
data class Car2DbSearchModelGroup(
    val model: Car2DbModel = Car2DbModel(),
    val make: Car2DbMake? = null,
    val matchingTrimsCount: Int = 0,
    val matchingTrims: List<Car2DbSearchTrim> = emptyList()
)

@Serializable
data class Car2DbSearchTrim(
    val id: Int = 0,
    val name: String = "",
    val yearBegin: Int? = null,
    val yearEnd: Int? = null,
    val relevanceScore: Double = 0.0,
    val keySpecifications: Car2DbKeySpecs? = null,
    val breadcrumbs: Car2DbBreadcrumbs? = null
)

@Serializable
data class Car2DbYear(
    @SerialName("@id") val iri: String? = null,
    val year: Int = 0,
    val makeIds: List<Int> = emptyList(),
    val modelIds: List<Int> = emptyList(),
    val trimIds: List<Int> = emptyList()
)

/**
 * Mapeo a tipo de vehículo seguro para el consumidor (Forge/DTC).
 * Este es el modelo que la UI consume, NO el modelo crudo de Car2DB.
 */
@Serializable
data class Car2DbVehicleLookup(
    val trimId: Int,
    val make: String,
    val model: String,
    val trimName: String,
    val yearBegin: Int?,
    val yearEnd: Int?,
    val engineDisplacementL: Double?,
    val powerHp: Double?,
    val torqueNm: Double?,
    val transmission: String?,
    val drivetrain: String?,
    val fuelType: String?,
    val bodyType: String?,
    val lengthMm: Double?,
    val widthMm: Double?,
    val heightMm: Double?,
    val wheelbaseMm: Double?,
    val curbWeightKg: Double?,
    val topSpeedKph: Double?,
    val acceleration0To100: Double?,
    val rawSpecifications: List<Car2DbSpecGroup> = emptyList(),
    val provenance: String = "Car2DB API v3",
    val safetyClassification: SafetyClassification = SafetyClassification.SAFETY_CRITICAL_UNCERTIFIED,
    val fetchedAtMs: Long = 0L
)

/**
 * Resultado de búsqueda natural.
 */
@Serializable
data class Car2DbSearchResponse(
    val query: String,
    val results: List<Car2DbSearchModelGroup> = emptyList(),
    val totalTrims: Int = 0,
    val error: String? = null
) {
    val isEmpty: Boolean get() = results.isEmpty()
}