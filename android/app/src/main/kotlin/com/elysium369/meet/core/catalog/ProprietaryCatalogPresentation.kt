package com.elysium369.meet.core.catalog

import android.content.Context

data class CatalogSystemFamily(
    val id: String,
    val title: String,
    val subtitle: String,
    val colorHex: String,
    val systemIds: Set<String>,
)

object CatalogSystemFamilies {
    val all: List<CatalogSystemFamily> = listOf(
        CatalogSystemFamily(
            id = "engine",
            title = "Motor",
            subtitle = "Combustión, admisión y sobrealimentación",
            colorHex = "#F59E0B",
            systemIds = setOf("engine", "intake", "forced_induction"),
        ),
        CatalogSystemFamily(
            id = "transmission",
            title = "Caja y tren motriz",
            subtitle = "Transmisión automática, ejes y transferencia",
            colorHex = "#10B981",
            systemIds = setOf("transmission"),
        ),
        CatalogSystemFamily(
            id = "electrical",
            title = "Eléctrico y electrónico",
            subtitle = "Cableado, ECUs, sensores, actuadores y luces",
            colorHex = "#60A5FA",
            systemIds = setOf(
                "electrical",
                "control_modules",
                "sensors",
                "actuators",
                "lighting",
                "adas",
                "infotainment",
                "access",
            ),
        ),
        CatalogSystemFamily(
            id = "hydraulics",
            title = "Hidráulico y control",
            subtitle = "Frenos, dirección y circuitos de presión",
            colorHex = "#22D3EE",
            systemIds = setOf("brakes", "steering"),
        ),
        CatalogSystemFamily(
            id = "chassis",
            title = "Chasis y rodaje",
            subtitle = "Estructura, suspensión, ruedas y neumáticos",
            colorHex = "#A3E635",
            systemIds = setOf("structure", "suspension", "wheels"),
        ),
        CatalogSystemFamily(
            id = "body",
            title = "Carrocería y exterior",
            subtitle = "Paneles, puertas, cristales y lavado",
            colorHex = "#94A3B8",
            systemIds = setOf("body", "wipers"),
        ),
        CatalogSystemFamily(
            id = "cabin",
            title = "Cabina y seguridad",
            subtitle = "Interior, climatización y protección pasiva",
            colorHex = "#C084FC",
            systemIds = setOf("interior", "hvac", "passive_safety"),
        ),
        CatalogSystemFamily(
            id = "service",
            title = "EV, fluidos y servicio",
            subtitle = "Híbrido/EV, consumibles, sellos e índice técnico",
            colorHex = "#FACC15",
            systemIds = setOf("hybrid_ev", "fluids", "hardware", "overview"),
        ),
    )

    fun familyFor(systemId: String): CatalogSystemFamily? =
        all.singleOrNull { systemId in it.systemIds }

    fun uncoveredSystemIds(systemIds: Collection<String>): Set<String> =
        systemIds.toSet() - all.flatMapTo(linkedSetOf()) { it.systemIds }

    fun duplicateSystemIds(): Set<String> = all
        .flatMap(CatalogSystemFamily::systemIds)
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
}

enum class ProprietaryCanonicalMatchMethod {
    EXACT_NAME_OR_ALIAS,
    CONSERVATIVE_NOMINAL_FORM,
}

data class ProprietaryCanonical3dResolution(
    val part: CanonicalVehiclePart,
    val method: ProprietaryCanonicalMatchMethod,
)

internal data class CanonicalPartIdentity(
    val canonicalId: String,
    val name: String,
    val aliases: List<String>,
)

internal data class CanonicalIdentityResolution(
    val identity: CanonicalPartIdentity,
    val method: ProprietaryCanonicalMatchMethod,
)

class ProprietaryCanonical3dResolver(context: Context) {
    private val partsRepository = CanonicalVehiclePartRepository(context)
    private val parts: List<CanonicalVehiclePart> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        partsRepository.all()
    }
    private val partById: Map<String, CanonicalVehiclePart> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        parts.associateBy { it.element.canonicalId }
    }
    private val identities: List<CanonicalPartIdentity> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        parts.map {
            CanonicalPartIdentity(
                canonicalId = it.element.canonicalId,
                name = it.element.nameOriginal,
                aliases = it.element.aliases,
            )
        }
    }

    fun resolve(entity: ProprietaryCatalogEntity): ProprietaryCanonical3dResolution? {
        if (entity.recordRole != "COMPONENT") return null
        val identityResolution = resolveCanonicalIdentity(
            sourceName = physicalComponentName(entity.nameOriginal),
            preferredCanonicalPrefixes = preferredCanonicalPrefixes(entity.systemId),
            candidates = identities,
        ) ?: return null
        val part = partById[identityResolution.identity.canonicalId] ?: return null
        return ProprietaryCanonical3dResolution(part, identityResolution.method)
    }
}

/**
 * Some source tables store the physical name and the service procedure in the
 * same cell. Only the leading identity is suitable for canonical matching.
 * The complete literal value remains untouched in the corpus and UI.
 */
internal fun physicalComponentName(value: String): String {
    val firstField = value
        .lineSequence()
        .firstOrNull()
        .orEmpty()
        .substringBefore('\t')
        .trim()
    return firstField.ifBlank { value.trim() }
}

internal fun resolveCanonicalIdentity(
    sourceName: String,
    preferredCanonicalPrefixes: List<String>,
    candidates: List<CanonicalPartIdentity>,
): CanonicalIdentityResolution? {
    val sourceExactKeys = identityKeys(sourceName, useNominalForm = false)
    val exactMatches = candidates.filter { candidate ->
        candidate.identityKeys(useNominalForm = false).any(sourceExactKeys::contains)
    }
    selectUniqueIdentity(exactMatches, preferredCanonicalPrefixes)?.let {
        return CanonicalIdentityResolution(it, ProprietaryCanonicalMatchMethod.EXACT_NAME_OR_ALIAS)
    }
    if (exactMatches.isNotEmpty()) return null

    val sourceNominalKeys = identityKeys(sourceName, useNominalForm = true)
    val nominalMatches = candidates.filter { candidate ->
        candidate.identityKeys(useNominalForm = true).any(sourceNominalKeys::contains)
    }
    return selectUniqueIdentity(nominalMatches, preferredCanonicalPrefixes)?.let {
        CanonicalIdentityResolution(it, ProprietaryCanonicalMatchMethod.CONSERVATIVE_NOMINAL_FORM)
    }
}

private fun selectUniqueIdentity(
    matches: List<CanonicalPartIdentity>,
    preferredCanonicalPrefixes: List<String>,
): CanonicalPartIdentity? {
    if (matches.isEmpty()) return null
    val preferred = matches.filter { candidate ->
        preferredCanonicalPrefixes.any { prefix -> candidate.canonicalId.startsWith("$prefix-") }
    }
    return when {
        preferred.size == 1 -> preferred.single()
        preferred.size > 1 -> null
        matches.size == 1 -> matches.single()
        else -> null
    }
}

private fun CanonicalPartIdentity.identityKeys(useNominalForm: Boolean): Set<String> =
    (listOf(name) + aliases).flatMapTo(linkedSetOf()) { identityKeys(it, useNominalForm) }

private fun identityKeys(value: String, useNominalForm: Boolean): Set<String> {
    val variants = buildList {
        add(value)
        addAll(value.split('/', '|', ';').map(String::trim).filter(String::isNotBlank))
    }
    return variants.mapNotNullTo(linkedSetOf()) { variant ->
        val normalized = variant.normalizedCatalogText()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.length < 3) {
            null
        } else if (useNominalForm) {
            normalized.split(' ')
                .filter(String::isNotBlank)
                .joinToString(" ") { token -> token.conservativeSingular() }
        } else {
            normalized
        }
    }
}

private fun String.conservativeSingular(): String = when {
    length > 5 && endsWith("es") -> dropLast(2)
    length > 4 && endsWith("s") -> dropLast(1)
    else -> this
}

private fun preferredCanonicalPrefixes(systemId: String): List<String> = when (systemId) {
    "engine", "intake", "forced_induction" -> listOf("g4ed")
    "transmission" -> listOf("transmission_hydraulics")
    "structure", "body", "wipers", "interior" -> listOf("body")
    "electrical", "control_modules", "sensors", "actuators", "lighting",
    "adas", "infotainment", "access" -> listOf("electrical", "g4ed")
    else -> listOf("remaining_systems", "transmission_hydraulics")
}
