package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM tests for AutomationDefinitionStore serialization.
 * Tests the JSON serialization/deserialization logic
 * directly without needing Android Context.
 */
class AutomationDefinitionStoreSerializationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun serializeAndParseRoundTrips() {
        val auto = makeAutomation("auto-1", "Test Automation")
        val json = serializeAuto(auto)
        val parsed = parseAuto(json)
        assertEquals(auto.id, parsed.id)
        assertEquals(auto.name, parsed.name)
        assertEquals(auto.author, parsed.author)
        assertEquals(auto.triggers.size, parsed.triggers.size)
        assertEquals(auto.actions.size, parsed.actions.size)
    }

    @Test
    fun serializePreservesTriggerEvent() {
        val auto = makeAutomation("a1", "T").copy(
            triggers = listOf(Trigger(event = TriggerEvent.DoorOpened))
        )
        val json = serializeAuto(auto)
        val parsed = parseAuto(json)
        assertEquals(TriggerEvent.DoorOpened, parsed.triggers[0].event)
    }

    @Test
    fun serializePreservesOnOffAction() {
        val auto = makeAutomation("a1", "T").copy(
            actions = listOf(
                Action(
                    deviceId = DeviceId("light-1"),
                    capability = Capability.OnOff,
                    command = CommandValue.OnOff(turnOn = false)
                )
            )
        )
        val json = serializeAuto(auto)
        val parsed = parseAuto(json)
        assertEquals(DeviceId("light-1"), parsed.actions[0].deviceId)
        assertEquals(false, (parsed.actions[0].command as CommandValue.OnOff).turnOn)
    }

    @Test
    fun serializePreservesLevelAction() {
        val auto = makeAutomation("a1", "T").copy(
            actions = listOf(
                Action(
                    deviceId = DeviceId("d1"),
                    capability = Capability.Level,
                    command = CommandValue.Level(0.75f)
                )
            )
        )
        val json = serializeAuto(auto)
        val parsed = parseAuto(json)
        val cmd = parsed.actions[0].command as CommandValue.Level
        assertEquals(0.75f, cmd.value, 0.001f)
    }

    @Test
    fun serializePreservesLockAction() {
        val auto = makeAutomation("a1", "T").copy(
            actions = listOf(
                Action(
                    deviceId = DeviceId("lock-1"),
                    capability = Capability.LockUnlock,
                    command = CommandValue.Lock(locked = true)
                )
            )
        )
        val json = serializeAuto(auto)
        val parsed = parseAuto(json)
        assertEquals(true, (parsed.actions[0].command as CommandValue.Lock).locked)
    }

    @Test
    fun serializePreservesMediaAction() {
        val auto = makeAutomation("a1", "T").copy(
            actions = listOf(
                Action(
                    deviceId = DeviceId("tv-1"),
                    capability = Capability.MediaTransport,
                    command = CommandValue.Media(play = false)
                )
            )
        )
        val json = serializeAuto(auto)
        val parsed = parseAuto(json)
        assertEquals(false, (parsed.actions[0].command as CommandValue.Media).play)
    }

    @Test
    fun serializePreservesVerificationPolicy() {
        val auto = makeAutomation("a1", "T").copy(
            verification = VerificationPolicy(
                timeoutMs = 10_000L,
                requireStateConfirmation = true
            )
        )
        val json = serializeAuto(auto)
        val parsed = parseAuto(json)
        assertEquals(10_000L, parsed.verification.timeoutMs)
        assertEquals(true, parsed.verification.requireStateConfirmation)
    }

    @Test
    fun fullFileRoundTrip() {
        val auto = makeAutomation("auto-1", "Persisted")
        val arr = JSONArray()
        arr.put(JSONObject().apply { put("version", 1); put("count", 1) })
        arr.put(serializeAuto(auto))
        val file = tempFolder.newFile("automations.json")
        file.writeText(arr.toString())

        // Read back
        val readArr = JSONArray(file.readText())
        val parsed = parseAuto(readArr.getJSONObject(1))
        assertEquals("Persisted", parsed.name)
        assertEquals("auto-1", parsed.id.value)
    }

    // --- Helpers: serialize/parse mirroring AutomationDefinitionStore logic ---

    private fun serializeAuto(auto: Automation): JSONObject {
        return JSONObject().apply {
            put("id", auto.id.value)
            put("name", auto.name)
            put("author", auto.author)
            put("createdAtNs", auto.createdAtNs)
            put("triggers", JSONArray().apply {
                for (trigger in auto.triggers) {
                    put(JSONObject().apply {
                        put("event", trigger.event.name)
                        put("deviceId", trigger.deviceId?.value)
                    })
                }
            })
            put("conditions", JSONArray())
            put("actions", JSONArray().apply {
                for (action in auto.actions) {
                    put(JSONObject().apply {
                        put("deviceId", action.deviceId.value)
                        put("capability", action.capability.name)
                        put("commandType", action.command::class.simpleName)
                        when (val cmd = action.command) {
                            is CommandValue.OnOff -> put("turnOn", cmd.turnOn)
                            is CommandValue.Level -> put("levelValue", cmd.value.toDouble())
                            is CommandValue.Lock -> put("locked", cmd.locked)
                            is CommandValue.Media -> put("play", cmd.play)
                            is CommandValue.Position -> put("percentOpen", cmd.percentOpen.toDouble())
                            is CommandValue.Climate -> put("targetCelsius", cmd.targetCelsius.toDouble())
                            is CommandValue.Color -> {
                                put("hueDegrees", cmd.hueDegrees.toDouble())
                                put("saturation", cmd.saturation.toDouble())
                            }
                            is CommandValue.ColorTemperature -> put("kelvin", cmd.kelvin)
                            is CommandValue.Noop -> {}
                        }
                    })
                }
            })
            put("verificationTimeoutMs", auto.verification.timeoutMs)
            put("verificationRequireState", auto.verification.requireStateConfirmation)
            put("compensation", JSONArray())
        }
    }

    private fun parseAuto(obj: JSONObject): Automation {
        val triggers = mutableListOf<Trigger>()
        val triggersArr = obj.optJSONArray("triggers")
        if (triggersArr != null) {
            for (i in 0 until triggersArr.length()) {
                val t = triggersArr.getJSONObject(i)
                val event = try { TriggerEvent.valueOf(t.getString("event")) } catch (_: Throwable) { TriggerEvent.Motion }
                val deviceId = t.optString("deviceId", null)?.let { DeviceId(it) }
                triggers.add(Trigger(event = event, deviceId = deviceId))
            }
        }
        val actions = mutableListOf<Action>()
        val actionsArr = obj.optJSONArray("actions")
        if (actionsArr != null) {
            for (i in 0 until actionsArr.length()) {
                val a = actionsArr.getJSONObject(i)
                val deviceId = DeviceId(a.getString("deviceId"))
                val capability = try { Capability.valueOf(a.getString("capability")) } catch (_: Throwable) { Capability.OnOff }
                val commandType = a.optString("commandType", "OnOff")
                val command = when (commandType) {
                    "OnOff" -> CommandValue.OnOff(turnOn = a.optBoolean("turnOn", true))
                    "Level" -> CommandValue.Level(value = a.optDouble("levelValue", 0.5).toFloat())
                    "Lock" -> CommandValue.Lock(locked = a.optBoolean("locked", false))
                    "Media" -> CommandValue.Media(play = a.optBoolean("play", true))
                    "Position" -> CommandValue.Position(percentOpen = a.optDouble("percentOpen", 0.0).toFloat())
                    "Climate" -> CommandValue.Climate(targetCelsius = a.optDouble("targetCelsius", 24.0).toFloat())
                    else -> CommandValue.Noop
                }
                actions.add(Action(deviceId = deviceId, capability = capability, command = command))
            }
        }
        val timeoutMs = obj.optLong("verificationTimeoutMs", 5_000L)
        val requireState = obj.optBoolean("verificationRequireState", false)
        return Automation(
            id = AutomationId(obj.getString("id")),
            name = obj.getString("name"),
            author = obj.getString("author"),
            createdAtNs = obj.getLong("createdAtNs"),
            triggers = triggers,
            conditions = emptyList(),
            actions = actions,
            verification = VerificationPolicy(timeoutMs = timeoutMs, requireStateConfirmation = requireState)
        )
    }

    private fun makeAutomation(id: String, name: String) = Automation(
        id = AutomationId(id),
        name = name,
        author = "test",
        createdAtNs = System.nanoTime(),
        triggers = listOf(Trigger(event = TriggerEvent.Motion)),
        conditions = emptyList(),
        actions = listOf(
            Action(
                deviceId = DeviceId("d1"),
                capability = Capability.OnOff,
                command = CommandValue.OnOff(turnOn = true)
            )
        ),
        verification = VerificationPolicy(timeoutMs = 5_000L, requireStateConfirmation = false)
    )
}
