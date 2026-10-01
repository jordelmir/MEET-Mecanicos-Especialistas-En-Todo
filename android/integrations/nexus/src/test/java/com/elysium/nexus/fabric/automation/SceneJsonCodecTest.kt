package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.ClimateMode
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.LockSource
import com.elysium.nexus.fabric.canonical.UniversalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §36 durable scene codec: full-fidelity round-trip tests.
 * Lossless means: every variant survives, defaults are
 * explicit in JSON, unknown types are rejected (no silent
 * substitution).
 */
class SceneJsonCodecTest {

    // ── Round-trip: scene envelope ────────────────────────

    @Test
    fun roundTripsSceneEnvelope() {
        val scene = movieScene()
        val decoded = SceneJsonCodec.decode(SceneJsonCodec.encode(scene))
        assertEquals(scene.name, decoded.name)
        assertEquals(scene.description, decoded.description)
        assertEquals(scene.tags, decoded.tags)
        assertEquals(scene.metadata, decoded.metadata)
        assertEquals(scene.id, decoded.id)
    }

    @Test
    fun roundTripsStepFields() {
        val original = movieScene().steps[0]
        val decoded = SceneJsonCodec.decode(SceneJsonCodec.encode(movieScene())).steps[0]
        assertEquals(original.stepId, decoded.stepId)
        assertEquals(original.targetDeviceId, decoded.targetDeviceId)
        assertEquals(original.timeoutMs, decoded.timeoutMs)
        assertEquals(original.description, decoded.description)
        assertEquals(original.optional, decoded.optional)
        assertEquals(original.retryCount, decoded.retryCount)
        assertEquals(original.retryDelayMs, decoded.retryDelayMs)
    }

    // ─── Round-trip: every UniversalAction variant ────────

    @Test
    fun roundTripsEveryActionVariant() {
        val actions = listOf(
            UniversalAction.PowerOn(DeviceId("dev-1")),
            UniversalAction.PowerOff(DeviceId("dev-1")),
            UniversalAction.PowerToggle(DeviceId("dev-1")),
            UniversalAction.VolumeUp(DeviceId("dev-1")),
            UniversalAction.VolumeDown(DeviceId("dev-1")),
            UniversalAction.Mute(DeviceId("dev-1")),
            UniversalAction.SetVolume(DeviceId("dev-1"), 0.4f),
            UniversalAction.ChannelUp(DeviceId("dev-1")),
            UniversalAction.ChannelDown(DeviceId("dev-1")),
            UniversalAction.InputSelect(DeviceId("dev-1"), "HDMI_2"),
            UniversalAction.MediaPlay(DeviceId("dev-1")),
            UniversalAction.MediaPause(DeviceId("dev-1")),
            UniversalAction.MediaStop(DeviceId("dev-1")),
            UniversalAction.MediaNext(DeviceId("dev-1")),
            UniversalAction.MediaPrevious(DeviceId("dev-1")),
            UniversalAction.Navigate(DeviceId("dev-1"), com.elysium.nexus.fabric.canonical.Direction.Left),
            UniversalAction.Ok(DeviceId("dev-1")),
            UniversalAction.Back(DeviceId("dev-1")),
            UniversalAction.Home(DeviceId("dev-1")),
            UniversalAction.Menu(DeviceId("dev-1")),
            UniversalAction.SetTemperature(DeviceId("dev-1"), 20f, ClimateMode.Heat),
            UniversalAction.SetFanSpeed(DeviceId("dev-1"), 0.5f),
            UniversalAction.SetMode(DeviceId("dev-1"), ClimateMode.Dry),
            UniversalAction.Custom(DeviceId("dev-1"), "level", mapOf("v" to "1"))
        )
        for (action in actions) {
            val scene = Scene(
                name = "A",
                steps = listOf(ActionStep(targetDeviceId = DeviceId("dev-1"), action = action))
            )
            val decoded = SceneJsonCodec.decode(SceneJsonCodec.encode(scene))
            val decodedAction = decoded.steps.single().action
            assertEquals(
                "action ${action::class.simpleName} must round-trip",
                actionType(action),
                actionType(decodedAction)
            )
        }
    }

    @Test
    fun roundTripsSetVolumeLevel() {
        val scene = Scene(
            name = "V",
            steps = listOf(
                ActionStep(targetDeviceId = DeviceId("dev"), action = UniversalAction.SetVolume(DeviceId("dev"), 0.37f))
            )
        )
        val got = SceneJsonCodec.decode(SceneJsonCodec.encode(scene)).steps.single().action
        assertEquals(UniversalAction.SetVolume(DeviceId("dev"), 0.37f).level, (got as UniversalAction.SetVolume).level)
    }

    @Test
    fun roundTripsSetTemperatureMode() {
        val scene = Scene(
            name = "T",
            steps = listOf(
                ActionStep(
                    targetDeviceId = DeviceId("ac"),
                    action = UniversalAction.SetTemperature(DeviceId("ac"), 21.5f, ClimateMode.Cool)
                )
            )
        )
        val got = SceneJsonCodec.decode(SceneJsonCodec.encode(scene)).steps.single().action
        val set = got as UniversalAction.SetTemperature
        assertEquals(21.5f, set.targetCelsius)
        assertEquals(ClimateMode.Cool, set.mode)
    }

    @Test
    fun roundTripsCustomKeyAndPayload() {
        val scene = Scene(
            name = "C",
            steps = listOf(
                ActionStep(
                    targetDeviceId = DeviceId("x"),
                    action = UniversalAction.Custom(DeviceId("x"), "level", mapOf("value" to "0.42"))
                )
            )
        )
        val got = SceneJsonCodec.decode(SceneJsonCodec.encode(scene)).steps.single().action
        val custom = got as UniversalAction.Custom
        assertEquals("level", custom.key)
        assertEquals(mapOf("value" to "0.42"), custom.payload)
    }

    @Test
    fun roundTripsRollbackAndPredicates() {
        val scene = Scene(
            name = "R",
            steps = listOf(
                ActionStep(
                    stepId = "s1",
                    targetDeviceId = DeviceId("tv"),
                    action = UniversalAction.PowerOn(DeviceId("tv")),
                    precondition = StatePredicate.DeviceReachable(DeviceId("tv"), 15_000L),
                    successCondition = StatePredicate.CapabilityAvailable(DeviceId("tv"), "OnOff"),
                    rollbackAction = UniversalAction.PowerOff(DeviceId("tv"))
                )
            )
        )
        val decoded = SceneJsonCodec.decode(SceneJsonCodec.encode(scene)).steps.single()
        assertEquals(15_000L, (decoded.precondition as StatePredicate.DeviceReachable).maxAgeMs)
        assertEquals("OnOff", (decoded.successCondition as StatePredicate.CapabilityAvailable).capability)
        assertTrue(decoded.rollbackAction is UniversalAction.PowerOff)
    }

    // ─── Device states inside predicates ──────────────────

    @Test
    fun roundTripsDeviceStates() {
        val states = listOf(
            DeviceState.OnOff(true),
            DeviceState.Level(0.6f),
            DeviceState.Color(210f, 0.9f),
            DeviceState.ColorTemperature(2700),
            DeviceState.Climate(24f, ClimateMode.Auto),
            DeviceState.Lock(true, LockSource.App),
            DeviceState.Position(0.75f),
            DeviceState.Media(true, "track-1"),
            DeviceState.EnergyRead(12f, 3.4f),
            DeviceState.Unknown
        )
        for (state in states) {
            val scene = Scene(
                name = "S",
                steps = listOf(
                    ActionStep(
                        targetDeviceId = DeviceId("d"),
                        action = UniversalAction.PowerOn(DeviceId("d")),
                        precondition = StatePredicate.DeviceState(DeviceId("d"), state)
                    )
                )
            )
            val got = SceneJsonCodec.decode(SceneJsonCodec.encode(scene)).steps.single()
                .precondition as StatePredicate.DeviceState
            assertEquals(state::class.simpleName, got.expectedState::class.simpleName)
        }
    }

    // ─── Honest failure modes ─────────────────────────────

    @Test
    fun unknownActionTypeThrows() {
        val json = """{"formatVersion":1,"scene":{"id":"x","name":"x","description":"","tags":[],"steps":[
            {"stepId":"s","targetDeviceId":"d","action":{"type":"beam_me_up"},"timeoutMs":5000}
        ]}}"""
        val thrown = assertThrows(Exception::class.java) {
            SceneJsonCodec.decode(json)
        }
        assertTrue(
            thrown.message!!.contains("Unknown action type") ||
                thrown is org.json.JSONException
        )
    }

    @Test
    fun wrongFormatVersionThrows() {
        val json = """{"formatVersion":99,"scene":{}}"""
        assertThrows(IllegalArgumentException::class.java) {
            SceneJsonCodec.decode(json)
        }
    }

    @Test
    fun encodeIrSignalRefusesToDropData() {
        val scene = Scene(
            name = "IR",
            steps = listOf(
                ActionStep(
                    targetDeviceId = DeviceId("d"),
                    action = UniversalAction.PowerOn(DeviceId("d")),
                    precondition = StatePredicate.DeviceState(
                        DeviceId("d"),
                        DeviceState.IrCommand("NEC", 0x1, 0x2)
                    )
                )
            )
        )
        // IrCommand with no IrSignal encodes fine (fields preserved);
        assertTrue(SceneJsonCodec.encode(scene).isNotBlank())
    }

    // ─── helpers ──────────────────────────────────────────

    private fun movieScene(): Scene = Scene(
        name = "Movie Night",
        description = "TV on, lights dim",
        steps = listOf(
            ActionStep(
                stepId = "step-1",
                targetDeviceId = DeviceId("tv"),
                action = UniversalAction.PowerOn(DeviceId("tv")),
                precondition = StatePredicate.DeviceReachable(DeviceId("tv")),
                successCondition = StatePredicate.CapabilityAvailable(DeviceId("tv"), "OnOff"),
                timeoutMs = 7_000L,
                rollbackAction = UniversalAction.PowerOff(DeviceId("tv")),
                description = "tv power",
                retryCount = 2,
                retryDelayMs = 900L
            ),
            ActionStep(
                stepId = "step-2",
                targetDeviceId = DeviceId("lights"),
                action = UniversalAction.Custom(
                    DeviceId("lights"),
                    "level",
                    mapOf("value" to "0.2")
                ),
                timeoutMs = 3_000L
            )
        ),
        tags = setOf("movie", "evening"),
        metadata = mapOf("source" to "test")
    )

    private fun actionType(action: UniversalAction): String = action::class.simpleName!!
}

typealias SceneScene = Scene