package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySceneRegistryTest {

    private val tvId = DeviceId("tv-living")

    @Test
    fun `save and get scene roundtrip`() = runBlocking {
        val registry = InMemorySceneRegistry()
        val scene = Scene(
            name = "movie",
            steps = listOf(
                ActionStep(targetDeviceId = tvId, action = UniversalAction.PowerOn(tvId))
            )
        )

        registry.saveScene(scene)
        val loaded = registry.getScene(scene.id)

        assertNotNull(loaded)
        assertEquals("movie", loaded?.name)
    }

    @Test
    fun `deleteScene removes scene`() = runBlocking {
        val registry = InMemorySceneRegistry()
        val scene = Scene(
            name = "doomed",
            steps = listOf(
                ActionStep(targetDeviceId = tvId, action = UniversalAction.PowerOff(tvId))
            )
        )

        registry.saveScene(scene)
        registry.deleteScene(scene.id)

        assertNull(registry.getScene(scene.id))
    }

    @Test
    fun `getScenesByTag filters correctly`() = runBlocking {
        val registry = InMemorySceneRegistry()
        registry.saveScene(
            Scene(
                name = "movie-night",
                steps = listOf(
                    ActionStep(targetDeviceId = tvId, action = UniversalAction.PowerOn(tvId))
                ),
                tags = setOf("movie", "night")
            )
        )
        registry.saveScene(
            Scene(
                name = "coffee",
                steps = listOf(
                    ActionStep(targetDeviceId = tvId, action = UniversalAction.Mute(tvId))
                ),
                tags = setOf("morning")
            )
        )

        assertEquals(1, registry.getScenesByTag("movie").size)
        assertEquals(0, registry.getScenesByTag("gaming").size)
    }

    @Test
    fun `importScene creates scene with parsed steps`() = runBlocking {
        val registry = InMemorySceneRegistry()
        val definition = SceneDefinition(
            scene = "filme",
            description = "Película en el salón",
            tags = setOf("movie"),
            steps = listOf(
                StepDefinition(
                    device = "tv-living",
                    action = "power_on",
                    timeout = 5_000L
                ),
                StepDefinition(
                    device = "tv-living",
                    action = "set_volume",
                    level = 0.2f,
                    timeout = 2_000L
                ),
                StepDefinition(
                    device = "tv-living",
                    action = "input_select",
                    inputId = "HDMI2",
                    timeout = 3_000L
                )
            )
        )

        val scene = registry.importScene(definition)

        assertEquals("filme", scene.name)
        assertEquals(3, scene.steps.size)
        assertTrue(scene.steps[0].action is UniversalAction.PowerOn)
        val setVolume = scene.steps[1].action as UniversalAction.SetVolume
        assertEquals(0.2f, setVolume.level)
        val inputSelect = scene.steps[2].action as UniversalAction.InputSelect
        assertEquals("HDMI2", inputSelect.inputId)
        assertEquals(setOf("movie"), scene.tags)
    }

    @Test
    fun `importScene parses preconditions and rollback`() = runBlocking {
        val registry = InMemorySceneRegistry()
        val definition = SceneDefinition(
            scene = "guarded",
            steps = listOf(
                StepDefinition(
                    device = "tv-living",
                    action = "power_on",
                    precondition = PredicateDefinition(
                        type = "device_state",
                        device = "tv-living",
                        state = "off"
                    ),
                    rollback = StepDefinition(device = "tv-living", action = "power_off")
                )
            )
        )

        val scene = registry.importScene(definition)

        val precondition = scene.steps[0].precondition
        assertTrue(precondition is StatePredicate.DeviceState)
        assertEquals(
            com.elysium.nexus.fabric.canonical.DeviceState.OnOff(false),
            (precondition as StatePredicate.DeviceState).expectedState
        )
        assertTrue(scene.steps[0].rollbackAction is UniversalAction.PowerOff)
    }

    @Test
    fun `exportScene roundtrip preserves structure`() = runBlocking {
        val registry = InMemorySceneRegistry()
        val scene = Scene(
            name = "cinema",
            description = "Cine en casa",
            steps = listOf(
                ActionStep(
                    targetDeviceId = tvId,
                    action = UniversalAction.PowerOn(tvId),
                    timeoutMs = 4_000,
                    precondition = StatePredicate.DeviceReachable(deviceId = tvId, maxAgeMs = 15_000)
                ),
                ActionStep(
                    targetDeviceId = DeviceId("soundbar"),
                    action = UniversalAction.Mute(DeviceId("soundbar"))
                )
            ),
            tags = setOf("night")
        )
        registry.saveScene(scene)

        val exported = registry.exportScene(scene.id)

        assertNotNull(exported)
        assertEquals("cinema", exported?.scene)
        assertEquals(2, exported?.steps?.size)
        assertEquals("power_on", exported?.steps?.get(0)?.action)
        assertEquals("device_reachable", exported?.steps?.get(0)?.precondition?.type)
        assertEquals(setOf("night"), exported?.tags)
    }

    @Test
    fun `exportScene returns null for missing scene`() = runBlocking {
        val registry = InMemorySceneRegistry()

        assertNull(registry.exportScene("missing"))
    }
}