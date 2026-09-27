package com.elysium369.meet.ui.elysium.theme

import com.elysium369.meet.ui.navigation.MeetDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElysiumThemeConfigTest {
    private val global = 0xFF102030L
    private val domain = 0xFF405060L
    private val route = 0xFF708090L

    @Test
    fun `each channel inherits route then domain then global then defaults independently`() {
        val config = ElysiumThemeConfig(
            global = ElysiumPaletteOverride(global, global, global),
            domains = mapOf("Diagnostics" to ElysiumPaletteOverride(domain, domain)),
            routes = mapOf("scanner" to ElysiumPaletteOverride(route)),
        )
        assertEquals(
            ElysiumPaletteOverride(route, domain, global, ElysiumPaletteResolver.defaults.quaternaryArgb),
            ElysiumPaletteResolver.resolve(config, "Diagnostics", "scanner"),
        )
        for (channel in 0..3) {
            val allScopes = ElysiumThemeConfig(
                global = ElysiumPaletteOverride().withChannel(channel, global),
                domains = mapOf("D" to ElysiumPaletteOverride().withChannel(channel, domain)),
                routes = mapOf("R" to ElysiumPaletteOverride().withChannel(channel, route)),
            )
            assertEquals(route, ElysiumPaletteResolver.resolve(allScopes, "D", "R").channel(channel))
            assertEquals(domain, ElysiumPaletteResolver.resolve(allScopes, "D", "other").channel(channel))
            assertEquals(global, ElysiumPaletteResolver.resolve(allScopes, "other", "other").channel(channel))
        }
    }

    @Test
    fun `invalid and non opaque ARGB values fall through without contaminating siblings`() {
        for (invalid in listOf(-1L, 0L, 0x7F123456L, 0xFEFFFFFFL, 0x100000000L, Long.MAX_VALUE)) {
            val config = ElysiumThemeConfig(
                global = ElysiumPaletteOverride(global),
                domains = mapOf("D" to ElysiumPaletteOverride(invalid, domain)),
                routes = mapOf("R" to ElysiumPaletteOverride(invalid, null, route)),
            )
            val resolved = ElysiumPaletteResolver.resolve(config, "D", "R")
            assertEquals(global, resolved.primaryArgb)
            assertEquals(domain, resolved.secondaryArgb)
            assertEquals(route, resolved.tertiaryArgb)
            assertEquals(ElysiumPaletteResolver.defaults.quaternaryArgb, resolved.quaternaryArgb)
        }
        for (valid in listOf(0xFF000000L, 0xFFFFFFFFL)) {
            val config = ElysiumThemeConfig(global = ElysiumPaletteOverride(valid))
            assertEquals(valid, ElysiumPaletteResolver.resolve(config, "D", "R").primaryArgb)
        }
    }

    @Test
    fun `resetting an override restores its parent rather than factory palette`() {
        val config = ElysiumThemeConfig(
            global = ElysiumPaletteOverride(global),
            domains = mapOf("D" to ElysiumPaletteOverride(domain)),
            routes = mapOf("R" to ElysiumPaletteOverride(route)),
        )
        val routeReset = config.withOverride(ThemeScope.ROUTE, "R", ElysiumPaletteOverride())
        assertEquals(domain, ElysiumPaletteResolver.resolve(routeReset, "D", "R").primaryArgb)
        val domainReset = routeReset.withOverride(ThemeScope.DOMAIN, "D", ElysiumPaletteOverride())
        assertEquals(global, ElysiumPaletteResolver.resolve(domainReset, "D", "R").primaryArgb)
        val globalReset = domainReset.withOverride(ThemeScope.GLOBAL, "", ElysiumPaletteOverride())
        assertEquals(ElysiumPaletteResolver.defaults, ElysiumPaletteResolver.resolve(globalReset, "D", "R"))
    }

    @Test
    fun `withOverride preserves original and unrelated scopes and targets`() {
        val original = ElysiumThemeConfig(
            global = ElysiumPaletteOverride(global),
            domains = mapOf("D" to ElysiumPaletteOverride(domain)),
            routes = mapOf("R" to ElysiumPaletteOverride(route)),
        )
        val draft = ElysiumPaletteOverride(quaternaryArgb = route)
        val changedRoute = original.withOverride(ThemeScope.ROUTE, "new", draft)
        assertEquals(original.global, changedRoute.global)
        assertEquals(original.domains, changedRoute.domains)
        assertEquals(original.routes["R"], changedRoute.routes["R"])
        assertNull(original.routes["new"])
        val changedDomain = original.withOverride(ThemeScope.DOMAIN, "new", draft)
        assertEquals(original.global, changedDomain.global)
        assertEquals(original.routes, changedDomain.routes)
        assertEquals(original.domains["D"], changedDomain.domains["D"])
        assertNull(original.domains["new"])
        val changedGlobal = original.withOverride(ThemeScope.GLOBAL, "ignored", draft)
        assertEquals(original.domains, changedGlobal.domains)
        assertEquals(original.routes, changedGlobal.routes)
        assertEquals(ElysiumPaletteOverride(global), original.global)
    }

    @Test
    fun `domain classification follows actual Android destination keys`() {
        val expected = mapOf(
            MeetDestinations.SAFETY_HOME to "Safety",
            MeetDestinations.SAFETY_CASE_DETAIL to "Safety",
            MeetDestinations.SAFETY_REPORT_LOCATION to "Safety",
            MeetDestinations.RIDE_HOME to "Mobility",
            MeetDestinations.RIDE_DRIVER_REGISTRATION to "Mobility",
            MeetDestinations.TOW_TRUCK to "Mobility",
            MeetDestinations.SCANNER to "Diagnostics",
            MeetDestinations.DTCS to "Diagnostics",
            MeetDestinations.TERMINAL to "Diagnostics",
            MeetDestinations.GARAGE to "Vehicle",
            MeetDestinations.VEHICLE_ACCESS to "Vehicle",
            MeetDestinations.MAINTENANCE to "Vehicle",
            MeetDestinations.TRIP_LOG to "Vehicle",
            MeetDestinations.AI to "Intelligence",
            MeetDestinations.ENGINE_3D to "Forge",
            MeetDestinations.HUD to "Forge",
            MeetDestinations.PARTS_STORE to "Services",
            MeetDestinations.MECHANIC_SERVICES to "Services",
            MeetDestinations.PROVIDER_SERVICES_CONFIG to "Services",
            MeetDestinations.SETTINGS to "System",
            MeetDestinations.HOME to "System",
        )
        expected.forEach { (key, family) ->
            assertEquals("Incorrect domain for $key", family, ElysiumPaletteResolver.domain(key))
        }
    }
}
