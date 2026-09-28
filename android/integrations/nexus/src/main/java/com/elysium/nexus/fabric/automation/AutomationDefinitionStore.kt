package com.elysium.nexus.fabric.automation

import android.content.Context
import com.elysium.nexus.fabric.canonical.DeviceId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File-backed persistence for automation definitions.
 *
 * Automations are stored as a JSON array in
 * `automations.json` in the app's internal
 * files directory. The format is versioned
 * (the first element is a metadata object)
 * so the schema can evolve.
 *
 * ## Why JSON files and not Room
 *
 * The automation schema is deeply nested
 * (triggers, conditions, actions with typed
 * command values). Flattening to Room tables
 * would require 4+ tables with JOINs. A single
 * JSON file is simpler for the initial
 * implementation and sufficient for the
 * expected scale (< 100 automations per user).
 *
 * ## Thread safety
 *
 * All reads/writes happen on the calling
 * coroutine's thread. The caller is responsible
 * for not concurrent-modifying the file. The
 * automation UI is single-user and single-
 * activity, so this is safe.
 */
class AutomationDefinitionStore(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, FILENAME)

    /**
     * Load all persisted automations.
     */
    fun loadAll(): List<Automation> {
        if (!file.exists()) return emptyList()
        return try {
            val raw = file.readText()
            val arr = JSONArray(raw)
            val automations = mutableListOf<Automation>()
            for (i in 1 until arr.length()) {
                val obj = arr.getJSONObject(i)
                automations.add(parseAutomation(obj))
            }
            automations
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Save all automations (replaces the entire file).
     */
    fun saveAll(automations: List<Automation>) {
        val arr = JSONArray()
        // Metadata object as first element
        arr.put(JSONObject().apply {
            put("version", 1)
            put("count", automations.size)
        })
        for (auto in automations) {
            arr.put(serializeAutomation(auto))
        }
        file.writeText(arr.toString())
    }

    /**
     * Add a single automation.
     */
    fun add(automation: Automation) {
        val current = loadAll().toMutableList()
        current.add(automation)
        saveAll(current)
    }

    /**
     * Update a single automation by id.
     */
    fun update(automation: Automation) {
        val current = loadAll().toMutableList()
        val idx = current.indexOfFirst { it.id == automation.id }
        if (idx >= 0) {
            current[idx] = automation
            saveAll(current)
        }
    }

    /**
     * Delete an automation by id.
     */
    fun delete(id: AutomationId) {
        val current = loadAll().toMutableList()
        current.removeAll { it.id == id }
        saveAll(current)
    }

    /**
     * Clear all automations.
     */
    fun clear() {
        if (file.exists()) file.delete()
    }

    internal fun serializeAutomation(auto: Automation): JSONObject {
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
            put("conditions", JSONArray().apply {
                for (cond in auto.conditions) {
                    put(JSONObject().apply {
                        put("kind", cond.kind.name)
                        put("value", cond.value)
                    })
                }
            })
            put("actions", JSONArray().apply {
                for (action in auto.actions) {
                    put(serializeAction(action))
                }
            })
            put("verificationTimeoutMs", auto.verification.timeoutMs)
            put("verificationRequireState", auto.verification.requireStateConfirmation)
            put("compensation", JSONArray().apply {
                for (comp in auto.compensation) {
                    put(serializeAction(comp))
                }
            })
        }
    }

    private fun serializeAction(action: Action): JSONObject {
        return JSONObject().apply {
            put("deviceId", action.deviceId.value)
            put("capability", action.capability.name)
            put("commandType", action.command::class.simpleName)
            when (val cmd = action.command) {
                is CommandValue.OnOff -> put("turnOn", cmd.turnOn)
                is CommandValue.Level -> put("levelValue", cmd.value.toDouble())
                is CommandValue.Color -> {
                    put("hueDegrees", cmd.hueDegrees.toDouble())
                    put("saturation", cmd.saturation.toDouble())
                }
                is CommandValue.ColorTemperature -> put("kelvin", cmd.kelvin)
                is CommandValue.Climate -> put("targetCelsius", cmd.targetCelsius.toDouble())
                is CommandValue.Lock -> put("locked", cmd.locked)
                is CommandValue.Position -> put("percentOpen", cmd.percentOpen.toDouble())
                is CommandValue.Media -> put("play", cmd.play)
                is CommandValue.Noop -> {}
            }
        }
    }

    internal fun parseAutomation(obj: JSONObject): Automation {
        val triggers = mutableListOf<Trigger>()
        val triggersArr = obj.optJSONArray("triggers")
        if (triggersArr != null) {
            for (i in 0 until triggersArr.length()) {
                val t = triggersArr.getJSONObject(i)
                val eventName = t.optString("event", "Motion")
                val event = try {
                    TriggerEvent.valueOf(eventName)
                } catch (_: Throwable) {
                    TriggerEvent.Motion
                }
                val deviceId = t.optString("deviceId", null)?.let { DeviceId(it) }
                triggers.add(Trigger(event = event, deviceId = deviceId))
            }
        }

        val conditions = mutableListOf<Condition>()
        val condsArr = obj.optJSONArray("conditions")
        if (condsArr != null) {
            for (i in 0 until condsArr.length()) {
                val c = condsArr.getJSONObject(i)
                val kindName = c.optString("kind", "UserPresent")
                val kind = try {
                    ConditionKind.valueOf(kindName)
                } catch (_: Throwable) {
                    ConditionKind.UserPresent
                }
                conditions.add(Condition(kind = kind, value = c.optString("value")))
            }
        }

        val actions = mutableListOf<Action>()
        val actionsArr = obj.optJSONArray("actions")
        if (actionsArr != null) {
            for (i in 0 until actionsArr.length()) {
                actions.add(parseAction(actionsArr.getJSONObject(i)))
            }
        }

        val compensation = mutableListOf<Action>()
        val compArr = obj.optJSONArray("compensation")
        if (compArr != null) {
            for (i in 0 until compArr.length()) {
                compensation.add(parseAction(compArr.getJSONObject(i)))
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
            conditions = conditions,
            actions = actions,
            verification = VerificationPolicy(
                timeoutMs = timeoutMs,
                requireStateConfirmation = requireState
            ),
            compensation = compensation
        )
    }

    internal fun parseAction(obj: JSONObject): Action {
        val deviceId = DeviceId(obj.getString("deviceId"))
        val capName = obj.getString("capability")
        val capability = try {
            com.elysium.nexus.fabric.canonical.Capability.valueOf(capName)
        } catch (_: Throwable) {
            com.elysium.nexus.fabric.canonical.Capability.OnOff
        }
        val commandType = obj.optString("commandType", "OnOff")
        val command = when (commandType) {
            "OnOff" -> CommandValue.OnOff(turnOn = obj.optBoolean("turnOn", true))
            "Level" -> CommandValue.Level(value = obj.optDouble("levelValue", 0.5).toFloat())
            "Color" -> CommandValue.Color(
                hueDegrees = obj.optDouble("hueDegrees", 0.0).toFloat(),
                saturation = obj.optDouble("saturation", 1.0).toFloat()
            )
            "ColorTemperature" -> CommandValue.ColorTemperature(kelvin = obj.optInt("kelvin", 4000))
            "Climate" -> CommandValue.Climate(targetCelsius = obj.optDouble("targetCelsius", 24.0).toFloat())
            "Lock" -> CommandValue.Lock(locked = obj.optBoolean("locked", false))
            "Position" -> CommandValue.Position(percentOpen = obj.optDouble("percentOpen", 0.0).toFloat())
            "Media" -> CommandValue.Media(play = obj.optBoolean("play", true))
            else -> CommandValue.Noop
        }
        return Action(deviceId = deviceId, capability = capability, command = command)
    }

    companion object {
        private const val FILENAME = "automations.json"
    }
}
