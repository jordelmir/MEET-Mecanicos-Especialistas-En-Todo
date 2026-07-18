package com.elysium369.meet.visual3d.domain

import com.elysium369.meet.domain.visualdiagnostics.ComponentCategory

object VehicleTwinRenderPolicy {
    const val CATALOG_FALLBACK_EXPLOSION_THRESHOLD = 0.12f

    fun shouldRenderProgrammaticAssembly(
        category: ComponentCategory,
        selectedSystemId: String,
        isSelected: Boolean,
        hasDtc: Boolean,
        xRayEnabled: Boolean,
        hasCatalogSemanticGeometry: Boolean
    ): Boolean {
        if (isSelected || hasDtc) return true
        if (!xRayEnabled || selectedSystemId != "engine" || hasCatalogSemanticGeometry) return false
        return category in engineCategories
    }

    fun shouldShowCatalogFallback(
        sourceName: String,
        renderedByDetailedAsset: Boolean,
        hasDetailedSystemAsset: Boolean,
        xRayEnabled: Boolean,
        explodedProgress: Float,
        isSelected: Boolean
    ): Boolean {
        if (!isPhysicalComponentName(sourceName)) return false
        if (!xRayEnabled || renderedByDetailedAsset) return false
        if (!hasDetailedSystemAsset) return true
        return isSelected || explodedProgress > CATALOG_FALLBACK_EXPLOSION_THRESHOLD
    }

    fun isPhysicalComponentName(sourceName: String): Boolean =
        sourceName.isNotBlank() && '\t' !in sourceName && '\n' !in sourceName && '\r' !in sourceName

    private val engineCategories = setOf(
        ComponentCategory.IGNITION,
        ComponentCategory.AIR_INTAKE,
        ComponentCategory.FUEL,
        ComponentCategory.EXHAUST,
        ComponentCategory.COOLING,
        ComponentCategory.LUBRICATION,
        ComponentCategory.TURBO_SUPERCHARGER,
        ComponentCategory.SENSOR,
        ComponentCategory.ELECTRICAL
    )
}
