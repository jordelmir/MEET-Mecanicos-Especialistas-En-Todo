package com.elysium.vanguard.forge.presentation.navigation

import com.elysium.vanguard.forge.presentation.state.ForgeHomeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regresion: estos eventos deben mapearse a una ruta del NavGraph.
 * Si el test falla, los botones del home quedan sin destino (taps no hacen nada).
 */
class ForgeNavigationMapTest {

    @Test
    fun `OnCreatePart maps to part-editor`() {
        assertEquals("forge/part-editor", routeForEvent(ForgeHomeEvent.OnCreatePart))
    }

    @Test
    fun `OnCreateAssembly maps to assembly-editor`() {
        assertEquals("forge/assembly-editor", routeForEvent(ForgeHomeEvent.OnCreateAssembly))
    }

    @Test
    fun `OnCreateVehicle maps to vehicle-builder`() {
        assertEquals("forge/vehicle-builder", routeForEvent(ForgeHomeEvent.OnCreateVehicle))
    }

    @Test
    fun `OnOpenSimulation maps to simulation with empty arg`() {
        assertEquals("forge/simulation?assemblyId=", routeForEvent(ForgeHomeEvent.OnOpenSimulation))
    }

    @Test
    fun `OnOpenEngineRuntime maps to engine-runtime with empty arg`() {
        assertEquals("forge/engine-runtime?vehicleId=", routeForEvent(ForgeHomeEvent.OnOpenEngineRuntime))
    }

    @Test
    fun `OnOpenFailureLab maps to failure-lab with empty arg`() {
        assertEquals("forge/failure-lab?assemblyId=", routeForEvent(ForgeHomeEvent.OnOpenFailureLab))
    }

    @Test
    fun `OnOpenDiagnostics maps to diagnostic-report with empty arg`() {
        assertEquals("forge/diagnostic-report?reportId=", routeForEvent(ForgeHomeEvent.OnOpenDiagnostics))
    }

    @Test
    fun `OnOpenManuals maps to manual with empty arg`() {
        assertEquals("forge/manual?manualId=", routeForEvent(ForgeHomeEvent.OnOpenManuals))
    }

    @Test
    fun `OnOpenMaterials maps to materials`() {
        assertEquals("forge/materials", routeForEvent(ForgeHomeEvent.OnOpenMaterials))
    }

    @Test
    fun `OnOpenManufacturing maps to manufacturing`() {
        assertEquals("forge/manufacturing", routeForEvent(ForgeHomeEvent.OnOpenManufacturing))
    }

    @Test
    fun `OnOpenMyArtifacts maps to my-artifacts`() {
        assertEquals("forge/my-artifacts", routeForEvent(ForgeHomeEvent.OnOpenMyArtifacts))
    }

    @Test
    fun `OnOpenArtifact with id embeds id in partId arg`() {
        assertEquals(
            "forge/part-editor?partId=brake_disc_v1",
            routeForEvent(ForgeHomeEvent.OnOpenArtifact("brake_disc_v1", com.elysium.vanguard.forge.domain.ForgeArtifactType.PART))
        )
    }

    @Test
    fun `OnRefresh returns null (no nav)`() {
        assertNull(routeForEvent(ForgeHomeEvent.OnRefresh))
    }

    @Test
    fun `OnSearch returns null (no nav)`() {
        assertNull(routeForEvent(ForgeHomeEvent.OnSearch("anything")))
    }

    @Test
    fun `all routes start with forge slash so the NavGraph guard accepts them`() {
        val navigationEvents = listOf(
            ForgeHomeEvent.OnCreatePart,
            ForgeHomeEvent.OnCreateAssembly,
            ForgeHomeEvent.OnCreateVehicle,
            ForgeHomeEvent.OnOpenSimulation,
            ForgeHomeEvent.OnOpenEngineRuntime,
            ForgeHomeEvent.OnOpenFailureLab,
            ForgeHomeEvent.OnOpenDiagnostics,
            ForgeHomeEvent.OnOpenManuals,
            ForgeHomeEvent.OnOpenMaterials,
            ForgeHomeEvent.OnOpenManufacturing,
            ForgeHomeEvent.OnOpenMyArtifacts,
            ForgeHomeEvent.OnOpenArtifact("x", com.elysium.vanguard.forge.domain.ForgeArtifactType.PART)
        )
        for (event in navigationEvents) {
            val route = routeForEvent(event)
            assert(route != null) { "Event $event should produce a route" }
            assert(route!!.startsWith("forge/")) { "Route $route must start with forge/" }
        }
    }
}
