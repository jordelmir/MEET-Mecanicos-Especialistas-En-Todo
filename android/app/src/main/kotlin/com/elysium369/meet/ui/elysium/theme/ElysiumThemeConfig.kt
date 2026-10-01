package com.elysium369.meet.ui.elysium.theme

import kotlinx.serialization.Serializable

/** Four independently inherited brand channels. ARGB values are unsigned 32-bit longs. */
@Serializable
data class ElysiumPaletteOverride(
    val primaryArgb: Long? = null,
    val secondaryArgb: Long? = null,
    val tertiaryArgb: Long? = null,
    val quaternaryArgb: Long? = null,
) {
    fun channel(index: Int): Long? = when (index) {
        0 -> primaryArgb
        1 -> secondaryArgb
        2 -> tertiaryArgb
        3 -> quaternaryArgb
        else -> error("Unknown brand channel")
    }
    fun withChannel(index: Int, value: Long?): ElysiumPaletteOverride = when (index) {
        0 -> copy(primaryArgb = value)
        1 -> copy(secondaryArgb = value)
        2 -> copy(tertiaryArgb = value)
        3 -> copy(quaternaryArgb = value)
        else -> error("Unknown brand channel")
    }
}

enum class ThemeScope { GLOBAL, DOMAIN, ROUTE }

@Serializable
data class ElysiumThemeConfig(
    val global: ElysiumPaletteOverride = ElysiumPaletteOverride(),
    val domains: Map<String, ElysiumPaletteOverride> = emptyMap(),
    val routes: Map<String, ElysiumPaletteOverride> = emptyMap(),
) {
    fun overrideFor(scope: ThemeScope, target: String): ElysiumPaletteOverride = when (scope) {
        ThemeScope.GLOBAL -> global
        ThemeScope.DOMAIN -> domains[target] ?: ElysiumPaletteOverride()
        ThemeScope.ROUTE -> routes[target] ?: ElysiumPaletteOverride()
    }
    fun withOverride(scope: ThemeScope, target: String, value: ElysiumPaletteOverride): ElysiumThemeConfig = when (scope) {
        ThemeScope.GLOBAL -> copy(global = value)
        ThemeScope.DOMAIN -> copy(domains = domains + (target to value))
        ThemeScope.ROUTE -> copy(routes = routes + (target to value))
    }
}

/** Pure, deterministic inheritance. Semantic safety colors never enter this resolver. */
object ElysiumPaletteResolver {
    val defaults = ElysiumPaletteOverride(0xFF39FF66L, 0xFF00D9FFL, 0xFF1677FFL, 0xFF8255FFL)
    fun resolve(config: ElysiumThemeConfig, domain: String, route: String, base: ElysiumPaletteOverride = defaults): ElysiumPaletteOverride {
        var resolved = base
        for (channel in 0..3) {
            val value = sequenceOf(config.routes[route], config.domains[domain], config.global, base)
                .mapNotNull { it?.channel(channel)?.takeIf { argb -> argb in 0xFF000000L..0xFFFFFFFFL } }
                .first()
            resolved = resolved.withChannel(channel, value)
        }
        return resolved
    }

    /** Stable route families; argument-bearing routes keep their navigation template as the key. */
    fun domain(route: String): String = when {
        route.startsWith("safety") -> "Safety"
        route.startsWith("ride") || route.startsWith("fleet") || route.startsWith("tow") -> "Mobility"
        route == "component_locator" || route.contains("forge") || route.contains("engine") || route.contains("3d") || route.contains("hud") -> "Forge"
        route in setOf("reports", "live_link", "connect", "clone_test", "adaptation", "findings") || route.contains("scanner") || route.contains("dtc") || route.contains("diagnos") || route.contains("terminal") || route.contains("oscillo") -> "Diagnostics"
        route == "health_score" || route.contains("garage") || route.contains("vehicle") || route.contains("maintenance") || route.contains("dvir") || route == "trips" -> "Vehicle"
        route in setOf("ai", "meet_dna", "meet_perito", "agent_store") || route.startsWith("ai_") || route.contains("intelligence") || route.contains("command") || route.contains("expert") -> "Intelligence"
        route.startsWith("repair") || route.contains("market") || route.contains("provider") || route.contains("mechanic") || route.contains("part") || route.contains("service") -> "Services"
        route.startsWith("pro_") -> "Professional"
        else -> "System"
    }
}
