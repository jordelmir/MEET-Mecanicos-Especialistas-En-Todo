package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import java.util.UUID

/**
 * §36 SceneDefinition ↔ Scene conversion.
 *
 * The declarative DSL ([SceneDefinition]) is the portable,
 * lossy-optional representation; [SceneJsonCodec] is the
 * durable full-fidelity one. This converter maps between
 * the two so imports/exports work from any storage.
 */
object SceneDefinitionConverter {

    fun toScene(definition: SceneDefinition): Scene {
        val steps = definition.steps.map { stepDef ->
            ActionStep(
                stepId = UUID.randomUUID().toString(),
                targetDeviceId = DeviceId(stepDef.device),
                action = parseAction(stepDef.action, stepDef),
                precondition = stepDef.precondition?.let { parsePredicate(it) },
                successCondition = stepDef.successCondition?.let { parsePredicate(it) },
                timeoutMs = stepDef.timeout,
                rollbackAction = stepDef.rollback?.let { parseAction(it.action, it) },
                optional = stepDef.optional,
                retryCount = stepDef.retryCount,
                retryDelayMs = stepDef.retryDelayMs,
                description = stepDef.description
            )
        }
        return Scene(
            name = definition.scene,
            description = definition.description,
            steps = steps,
            tags = definition.tags
        )
    }

    fun toDefinition(scene: Scene): SceneDefinition {
        val stepDefinitions = scene.steps.map { step ->
            StepDefinition(
                device = step.targetDeviceId.value,
                action = actionName(step.action),
                precondition = step.precondition?.let { exportPredicate(it) },
                successCondition = step.successCondition?.let { exportPredicate(it) },
                timeout = step.timeoutMs,
                rollback = step.rollbackAction?.let {
                    StepDefinition(device = step.targetDeviceId.value, action = actionName(it))
                },
                optional = step.optional,
                retryCount = step.retryCount,
                retryDelayMs = step.retryDelayMs,
                description = step.description
            )
        }
        return SceneDefinition(
            scene = scene.name,
            description = scene.description,
            steps = stepDefinitions,
            tags = scene.tags
        )
    }

    fun parseAction(actionName: String, step: StepDefinition): UniversalAction {
        val targetId = DeviceId(step.device)
        return when (actionName.lowercase()) {
            "power_on", "poweron" -> UniversalAction.PowerOn(targetId)
            "power_off", "poweroff" -> UniversalAction.PowerOff(targetId)
            "volume_up", "volumeup" -> UniversalAction.VolumeUp(targetId)
            "volume_down", "volumedown" -> UniversalAction.VolumeDown(targetId)
            "mute" -> UniversalAction.Mute(targetId)
            "set_volume", "setvolume" -> {
                val level = step.level ?: 0.5f
                UniversalAction.SetVolume(targetId, level)
            }
            "channel_up", "channelup" -> UniversalAction.ChannelUp(targetId)
            "channel_down", "channeldown" -> UniversalAction.ChannelDown(targetId)
            "input_select", "inputselect" -> {
                UniversalAction.InputSelect(targetId, step.inputId ?: "HDMI_1")
            }
            "play" -> UniversalAction.MediaPlay(targetId)
            "pause" -> UniversalAction.MediaPause(targetId)
            "stop" -> UniversalAction.MediaStop(targetId)
            "next" -> UniversalAction.MediaNext(targetId)
            "previous" -> UniversalAction.MediaPrevious(targetId)
            "home" -> UniversalAction.Home(targetId)
            "back" -> UniversalAction.Back(targetId)
            "ok" -> UniversalAction.Ok(targetId)
            "navigate_up" -> UniversalAction.Navigate(targetId, com.elysium.nexus.fabric.canonical.Direction.Up)
            "navigate_down" -> UniversalAction.Navigate(targetId, com.elysium.nexus.fabric.canonical.Direction.Down)
            "navigate_left" -> UniversalAction.Navigate(targetId, com.elysium.nexus.fabric.canonical.Direction.Left)
            "navigate_right" -> UniversalAction.Navigate(targetId, com.elysium.nexus.fabric.canonical.Direction.Right)
            else -> UniversalAction.Custom(targetId, actionName)
        }
    }

    fun actionName(action: UniversalAction): String = when (action) {
        is UniversalAction.PowerOn -> "power_on"
        is UniversalAction.PowerOff -> "power_off"
        is UniversalAction.PowerToggle -> "power_toggle"
        is UniversalAction.VolumeUp -> "volume_up"
        is UniversalAction.VolumeDown -> "volume_down"
        is UniversalAction.Mute -> "mute"
        is UniversalAction.SetVolume -> "set_volume"
        is UniversalAction.ChannelUp -> "channel_up"
        is UniversalAction.ChannelDown -> "channel_down"
        is UniversalAction.InputSelect -> "input_select"
        is UniversalAction.MediaPlay -> "play"
        is UniversalAction.MediaPause -> "pause"
        is UniversalAction.MediaStop -> "stop"
        is UniversalAction.MediaNext -> "next"
        is UniversalAction.MediaPrevious -> "previous"
        is UniversalAction.Home -> "home"
        is UniversalAction.Back -> "back"
        is UniversalAction.Ok -> "ok"
        is UniversalAction.Navigate -> "navigate_${action.direction.name.lowercase()}"
        is UniversalAction.Menu -> "menu"
        is UniversalAction.SetTemperature -> "set_temperature"
        is UniversalAction.SetFanSpeed -> "set_fan_speed"
        is UniversalAction.SetMode -> "set_mode"
        is UniversalAction.Custom -> action.key
    }

    fun parsePredicate(def: PredicateDefinition): StatePredicate? {
        return when (def.type.lowercase()) {
            "device_state" -> {
                if (def.device != null && def.state != null) {
                    StatePredicate.DeviceState(
                        deviceId = DeviceId(def.device),
                        expectedState = parseState(def.state)
                    )
                } else null
            }
            "capability_available" -> {
                if (def.device != null && def.capability != null) {
                    StatePredicate.CapabilityAvailable(
                        deviceId = DeviceId(def.device),
                        capability = def.capability
                    )
                } else null
            }
            "device_reachable" -> {
                if (def.device != null) {
                    StatePredicate.DeviceReachable(
                        deviceId = DeviceId(def.device),
                        maxAgeMs = def.maxAgeMs ?: 30_000L
                    )
                } else null
            }
            "all" -> {
                val predicates = def.predicates?.mapNotNull { parsePredicate(it) } ?: emptyList()
                if (predicates.isNotEmpty()) StatePredicate.All(predicates) else null
            }
            "any" -> {
                val predicates = def.predicates?.mapNotNull { parsePredicate(it) } ?: emptyList()
                if (predicates.isNotEmpty()) StatePredicate.Any(predicates) else null
            }
            else -> null
        }
    }

    fun parseState(stateStr: String): com.elysium.nexus.fabric.canonical.DeviceState {
        return when {
            stateStr.equals("on", ignoreCase = true) ->
                com.elysium.nexus.fabric.canonical.DeviceState.OnOff(true)
            stateStr.equals("off", ignoreCase = true) ->
                com.elysium.nexus.fabric.canonical.DeviceState.OnOff(false)
            stateStr.startsWith("level:", ignoreCase = true) -> {
                val value = stateStr.substringAfter(":").toFloatOrNull() ?: 0.5f
                com.elysium.nexus.fabric.canonical.DeviceState.Level(value)
            }
            else -> com.elysium.nexus.fabric.canonical.DeviceState.Unknown
        }
    }

    fun exportPredicate(predicate: StatePredicate): PredicateDefinition? {
        return when (predicate) {
            is StatePredicate.DeviceState -> PredicateDefinition(
                type = "device_state",
                device = predicate.deviceId.value,
                state = predicate.expectedState.toString()
            )
            is StatePredicate.CapabilityAvailable -> PredicateDefinition(
                type = "capability_available",
                device = predicate.deviceId.value,
                capability = predicate.capability
            )
            is StatePredicate.DeviceReachable -> PredicateDefinition(
                type = "device_reachable",
                device = predicate.deviceId.value,
                maxAgeMs = predicate.maxAgeMs
            )
            is StatePredicate.Custom -> PredicateDefinition(
                type = "custom",
                state = predicate.description
            )
            is StatePredicate.All -> PredicateDefinition(
                type = "all",
                predicates = predicate.predicates.mapNotNull { exportPredicate(it) }
            )
            is StatePredicate.Any -> PredicateDefinition(
                type = "any",
                predicates = predicate.predicates.mapNotNull { exportPredicate(it) }
            )
        }
    }
}