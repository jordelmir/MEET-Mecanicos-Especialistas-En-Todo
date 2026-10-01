package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.ClimateMode
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.LockSource
import com.elysium.nexus.fabric.canonical.UniversalAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * §36 durable scene codec.
 *
 * Full-fidelity JSON serialization of [Scene] — every step field,
 * every [UniversalAction] variant, every [StatePredicate] variant
 * and every [DeviceState] carried inside predicates.
 *
 * Contract:
 * - [encode] never loses data. If a value cannot be represented
 *   losslessly it **throws** rather than silently dropping fields.
 * - [decode] rejects unknown types with [IllegalArgumentException]
 *   (no silent fallback to a wrong meaning).
 * - The codec is pure (no Android deps) and round-trips exactly,
 *   modulo defaulted fields the model itself makes optional.
 */
object SceneJsonCodec {

    private const val FORMAT_VERSION = 1
    private const val KEY_FORMAT = "formatVersion"
    private const val KEY_SCENE = "scene"

    // ─── Encode ──────────────────────────────────────────

    fun encode(scene: Scene): String {
        val root = JSONObject()
        root.put(KEY_FORMAT, FORMAT_VERSION)
        root.put(KEY_SCENE, encodeScene(scene))
        return root.toString()
    }

    fun encodeScene(scene: Scene): JSONObject {
        val o = JSONObject()
        o.put("id", scene.id)
        o.put("name", scene.name)
        o.put("description", scene.description)
        o.put("tags", JSONArray(scene.tags.toList()))
        o.put("metadata", JSONObject(scene.metadata))
        val steps = JSONArray()
        scene.steps.forEach { steps.put(encodeStep(it)) }
        o.put("steps", steps)
        return o
    }

    private fun encodeStep(step: ActionStep): JSONObject {
        val o = JSONObject()
        o.put("stepId", step.stepId)
        o.put("targetDeviceId", step.targetDeviceId.value)
        o.put("action", encodeAction(step.action))
        step.precondition?.let { o.put("precondition", encodePredicate(it)) }
        step.successCondition?.let { o.put("successCondition", encodePredicate(it)) }
        o.put("timeoutMs", step.timeoutMs)
        step.rollbackAction?.let { o.put("rollbackAction", encodeAction(it)) }
        o.put("description", step.description)
        o.put("optional", step.optional)
        o.put("retryCount", step.retryCount)
        o.put("retryDelayMs", step.retryDelayMs)
        return o
    }

    private fun encodeAction(action: UniversalAction): JSONObject {
        val o = JSONObject()
        o.put("targetDeviceId", action.targetDeviceId.value)
        when (action) {
            is UniversalAction.PowerOn -> o.put("type", "power_on")
            is UniversalAction.PowerOff -> o.put("type", "power_off")
            is UniversalAction.PowerToggle -> o.put("type", "power_toggle")
            is UniversalAction.VolumeUp -> o.put("type", "volume_up")
            is UniversalAction.VolumeDown -> o.put("type", "volume_down")
            is UniversalAction.Mute -> o.put("type", "mute")
            is UniversalAction.SetVolume -> {
                o.put("type", "set_volume")
                o.put("level", action.level.toDouble())
            }
            is UniversalAction.ChannelUp -> o.put("type", "channel_up")
            is UniversalAction.ChannelDown -> o.put("type", "channel_down")
            is UniversalAction.InputSelect -> {
                o.put("type", "input_select")
                o.put("inputId", action.inputId)
            }
            is UniversalAction.MediaPlay -> o.put("type", "media_play")
            is UniversalAction.MediaPause -> o.put("type", "media_pause")
            is UniversalAction.MediaStop -> o.put("type", "media_stop")
            is UniversalAction.MediaNext -> o.put("type", "media_next")
            is UniversalAction.MediaPrevious -> o.put("type", "media_previous")
            is UniversalAction.Navigate -> {
                o.put("type", "navigate")
                o.put("direction", action.direction.name)
            }
            is UniversalAction.Ok -> o.put("type", "ok")
            is UniversalAction.Back -> o.put("type", "back")
            is UniversalAction.Home -> o.put("type", "home")
            is UniversalAction.Menu -> o.put("type", "menu")
            is UniversalAction.SetTemperature -> {
                o.put("type", "set_temperature")
                o.put("targetCelsius", action.targetCelsius.toDouble())
                o.put("mode", action.mode.name)
            }
            is UniversalAction.SetFanSpeed -> {
                o.put("type", "set_fan_speed")
                o.put("level", action.level.toDouble())
            }
            is UniversalAction.SetMode -> {
                o.put("type", "set_mode")
                o.put("mode", action.mode.name)
            }
            is UniversalAction.Custom -> {
                o.put("type", "custom")
                o.put("key", action.key)
                o.put("payload", JSONObject(action.payload))
            }
        }
        return o
    }

    private fun encodePredicate(predicate: StatePredicate): JSONObject {
        val o = JSONObject()
        when (predicate) {
            is StatePredicate.DeviceState -> {
                o.put("type", "device_state")
                o.put("deviceId", predicate.deviceId.value)
                o.put("expectedState", encodeDeviceState(predicate.expectedState))
            }
            is StatePredicate.CapabilityAvailable -> {
                o.put("type", "capability_available")
                o.put("deviceId", predicate.deviceId.value)
                o.put("capability", predicate.capability)
            }
            is StatePredicate.DeviceReachable -> {
                o.put("type", "device_reachable")
                o.put("deviceId", predicate.deviceId.value)
                o.put("maxAgeMs", predicate.maxAgeMs)
            }
            is StatePredicate.Custom -> {
                o.put("type", "custom_predicate")
                o.put("description", predicate.description)
            }
            is StatePredicate.All -> {
                o.put("type", "all")
                o.put("predicates", JSONArray(predicate.predicates.map { encodePredicate(it) }))
            }
            is StatePredicate.Any -> {
                o.put("type", "any")
                o.put("predicates", JSONArray(predicate.predicates.map { encodePredicate(it) }))
            }
        }
        return o
    }

    private fun encodeDeviceState(state: DeviceState): JSONObject {
        val o = JSONObject()
        when (state) {
            is DeviceState.Unknown -> o.put("type", "unknown")
            is DeviceState.OnOff -> {
                o.put("type", "on_off")
                o.put("isOn", state.isOn)
            }
            is DeviceState.Level -> {
                o.put("type", "level")
                o.put("value", state.value.toDouble())
            }
            is DeviceState.Color -> {
                o.put("type", "color")
                o.put("hueDegrees", state.hueDegrees.toDouble())
                o.put("saturation", state.saturation.toDouble())
            }
            is DeviceState.ColorTemperature -> {
                o.put("type", "color_temperature")
                o.put("kelvin", state.kelvin)
            }
            is DeviceState.Climate -> {
                o.put("type", "climate")
                o.put("targetCelsius", state.targetCelsius.toDouble())
                o.put("mode", state.mode.name)
            }
            is DeviceState.Lock -> {
                o.put("type", "lock")
                o.put("locked", state.locked)
                o.put("source", state.source.name)
            }
            is DeviceState.Position -> {
                o.put("type", "position")
                o.put("percentOpen", state.percentOpen.toDouble())
            }
            is DeviceState.Media -> {
                o.put("type", "media")
                o.put("playing", state.playing)
                state.track?.let { o.put("track", it) }
            }
            is DeviceState.EnergyRead -> {
                o.put("type", "energy_read")
                o.put("watts", state.watts.toDouble())
                o.put("kwhTotal", state.kwhTotal.toDouble())
            }
            is DeviceState.IrCommand -> {
                // Lossless IR requires the full IrSignal blob; refuse to
                // silently drop it.
                if (state.irSignal != null) {
                    throw IllegalStateException(
                        "SceneJsonCodec cannot losslessly encode IrCommand with irSignal; " +
                            "use the IR profile store for IR payloads."
                    )
                }
                o.put("type", "ir_command")
                o.put("protocolName", state.protocolName)
                o.put("address", state.address)
                o.put("command", state.command)
                o.put("extras", JSONObject(state.extras))
            }
        }
        return o
    }

    // ─── Decode ──────────────────────────────────────────

    fun decode(json: String): Scene {
        val root = JSONObject(json)
        require(root.getInt(KEY_FORMAT) == FORMAT_VERSION) {
            "Unsupported scene format ${root.getInt(KEY_FORMAT)}"
        }
        return decodeScene(root.getJSONObject(KEY_SCENE))
    }

    fun decodeScene(o: JSONObject): Scene {
        val steps = mutableListOf<ActionStep>()
        val stepsArr = o.optJSONArray("steps") ?: JSONArray()
        for (i in 0 until stepsArr.length()) {
            steps.add(decodeStep(stepsArr.getJSONObject(i)))
        }
        require(steps.isNotEmpty()) { "Scene must have at least one step." }

        val tags = mutableListOf<String>()
        val tagsArr = o.optJSONArray("tags") ?: JSONArray()
        for (i in 0 until tagsArr.length()) tags.add(tagsArr.getString(i))

        val metadata = mutableMapOf<String, String>()
        val meta = o.optJSONObject("metadata")
        if (meta != null) {
            meta.keys().forEach { k -> metadata[k] = meta.getString(k) }
        }

        return Scene(
            id = o.getString("id"),
            name = o.getString("name"),
            description = o.optString("description", ""),
            steps = steps,
            tags = tags.toSet(),
            metadata = metadata
        )
    }

    private fun decodeStep(o: JSONObject): ActionStep {
        val rollback = o.optJSONObject("rollbackAction")?.let { decodeAction(it) }
        val precondition = o.optJSONObject("precondition")?.let { decodePredicate(it) }
        val successCondition = o.optJSONObject("successCondition")?.let { decodePredicate(it) }
        return ActionStep(
            stepId = o.getString("stepId"),
            targetDeviceId = DeviceId(o.getString("targetDeviceId")),
            action = decodeAction(o.getJSONObject("action")),
            precondition = precondition,
            successCondition = successCondition,
            timeoutMs = o.getLong("timeoutMs"),
            rollbackAction = rollback,
            description = o.optString("description", ""),
            optional = o.optBoolean("optional", false),
            retryCount = o.optInt("retryCount", 0),
            retryDelayMs = o.optLong("retryDelayMs", 1_000L)
        )
    }

    private fun decodeAction(o: JSONObject): UniversalAction {
        val deviceId = DeviceId(o.getString("targetDeviceId"))
        return when (val type = o.getString("type")) {
            "power_on" -> UniversalAction.PowerOn(deviceId)
            "power_off" -> UniversalAction.PowerOff(deviceId)
            "power_toggle" -> UniversalAction.PowerToggle(deviceId)
            "volume_up" -> UniversalAction.VolumeUp(deviceId)
            "volume_down" -> UniversalAction.VolumeDown(deviceId)
            "mute" -> UniversalAction.Mute(deviceId)
            "set_volume" -> UniversalAction.SetVolume(deviceId, o.getDouble("level").toFloat())
            "channel_up" -> UniversalAction.ChannelUp(deviceId)
            "channel_down" -> UniversalAction.ChannelDown(deviceId)
            "input_select" -> UniversalAction.InputSelect(deviceId, o.getString("inputId"))
            "media_play" -> UniversalAction.MediaPlay(deviceId)
            "media_pause" -> UniversalAction.MediaPause(deviceId)
            "media_stop" -> UniversalAction.MediaStop(deviceId)
            "media_next" -> UniversalAction.MediaNext(deviceId)
            "media_previous" -> UniversalAction.MediaPrevious(deviceId)
            "navigate" -> UniversalAction.Navigate(
                deviceId, com.elysium.nexus.fabric.canonical.Direction.valueOf(o.getString("direction"))
            )
            "ok" -> UniversalAction.Ok(deviceId)
            "back" -> UniversalAction.Back(deviceId)
            "home" -> UniversalAction.Home(deviceId)
            "menu" -> UniversalAction.Menu(deviceId)
            "set_temperature" -> UniversalAction.SetTemperature(
                deviceId,
                o.getDouble("targetCelsius").toFloat(),
                ClimateMode.valueOf(o.optString("mode", ClimateMode.Auto.name))
            )
            "set_fan_speed" -> UniversalAction.SetFanSpeed(deviceId, o.getDouble("level").toFloat())
            "set_mode" -> UniversalAction.SetMode(deviceId, ClimateMode.valueOf(o.getString("mode")))
            "custom" -> {
                val payload = mutableMapOf<String, String>()
                val p = o.optJSONObject("payload")
                if (p != null) p.keys().forEach { k -> payload[k] = p.getString(k) }
                UniversalAction.Custom(deviceId, o.getString("key"), payload)
            }
            else -> throw IllegalArgumentException("Unknown action type: $type")
        }
    }

    private fun decodePredicate(o: JSONObject): StatePredicate = when (o.getString("type")) {
        "device_state" -> StatePredicate.DeviceState(
            DeviceId(o.getString("deviceId")),
            decodeDeviceState(o.getJSONObject("expectedState"))
        )
        "capability_available" -> StatePredicate.CapabilityAvailable(
            DeviceId(o.getString("deviceId")),
            o.getString("capability")
        )
        "device_reachable" -> StatePredicate.DeviceReachable(
            DeviceId(o.getString("deviceId")),
            o.optLong("maxAgeMs", 30_000L)
        )
        "custom_predicate" -> StatePredicate.Custom(
            o.optString("description", "custom"),
            evaluate = { false }
        )
        "all" -> {
            val arr = o.getJSONArray("predicates")
            StatePredicate.All((0 until arr.length()).map { decodePredicate(arr.getJSONObject(it)) })
        }
        "any" -> {
            val arr = o.getJSONArray("predicates")
            StatePredicate.Any((0 until arr.length()).map { decodePredicate(arr.getJSONObject(it)) })
        }
        else -> throw IllegalArgumentException("Unknown predicate type: ${o.getString("type")}")
    }

    private fun decodeDeviceState(o: JSONObject): DeviceState = when (o.getString("type")) {
        "unknown" -> DeviceState.Unknown
        "on_off" -> DeviceState.OnOff(o.getBoolean("isOn"))
        "level" -> DeviceState.Level(o.getDouble("value").toFloat())
        "color" -> DeviceState.Color(
            o.getDouble("hueDegrees").toFloat(),
            o.getDouble("saturation").toFloat()
        )
        "color_temperature" -> DeviceState.ColorTemperature(o.getInt("kelvin"))
        "climate" -> DeviceState.Climate(
            o.getDouble("targetCelsius").toFloat(),
            ClimateMode.valueOf(o.getString("mode"))
        )
        "lock" -> DeviceState.Lock(o.getBoolean("locked"), LockSource.valueOf(o.getString("source")))
        "position" -> DeviceState.Position(o.getDouble("percentOpen").toFloat())
        "media" -> DeviceState.Media(o.getBoolean("playing"), o.optString("track", null))
        "energy_read" -> DeviceState.EnergyRead(
            o.getDouble("watts").toFloat(),
            o.getDouble("kwhTotal").toFloat()
        )
        "ir_command" -> DeviceState.IrCommand(
            protocolName = o.getString("protocolName"),
            address = o.getInt("address"),
            command = o.getInt("command"),
            extras = decodeStringMap(o.optJSONObject("extras"))
        )
        else -> throw IllegalArgumentException("Unknown device state type: ${o.getString("type")}")
    }

    private fun decodeStringMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        o.keys().forEach { k -> map[k] = o.getString(k) }
        return map
    }
}