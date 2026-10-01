package com.elysium.nexus.fabric.automation

import android.util.Log
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.ClimateMode
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.LockSource
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.runBlocking

/**
 * The production [ActionDispatcher].
 *
 * Maps each canonical [Action] to the
 * appropriate [DeviceAdapter] and translates
 * the [CommandValue] into a [DeviceState]
 * that the adapter can execute.
 *
 * The adapter map is built once at
 * construction time. The dispatcher is
 * **stateless** (no mutable fields).
 */
class AdapterActionDispatcher(
    private val adapters: Map<Protocol, DeviceAdapter>
) : ActionDispatcher {

    companion object {
        private const val TAG = "AdapterActionDispatcher"
    }

    override fun dispatch(
        action: Action,
        verification: VerificationPolicy
    ): CommandStatus {
        val adapter = findAdapter(action)
        if (adapter == null) {
            Log.w(TAG, "No adapter for device ${action.deviceId.value}")
            return CommandStatus.Unsupported
        }
        val state = commandToDeviceState(action)
        if (state == null) {
            Log.w(TAG, "Cannot convert command to DeviceState: ${action.command}")
            return CommandStatus.Unsupported
        }
        return runBlocking {
            val result = adapter.write(action.deviceId, state)
            when (result) {
                is WriteResult.Ok -> {
                    Log.i(TAG, "Command accepted for ${action.deviceId.value}")
                    CommandStatus.Accepted
                }
                is WriteResult.Error -> {
                    Log.w(TAG, "Command failed: ${result.message}")
                    when (result.code) {
                        ErrorCode.DeviceOffline -> CommandStatus.DeviceOffline
                        ErrorCode.Timeout -> CommandStatus.TimedOut
                        ErrorCode.UnsupportedOperation -> CommandStatus.Unsupported
                        else -> CommandStatus.Rejected
                    }
                }
            }
        }
    }

    private fun findAdapter(action: Action): DeviceAdapter? {
        // Try to find an adapter that supports
        // this device's capabilities.
        return adapters.values.firstOrNull { adapter ->
            adapter.supportedCapabilities.contains(action.capability)
        }
    }

    private fun commandToDeviceState(action: Action): DeviceState? {
        return when (action.command) {
            is CommandValue.OnOff -> DeviceState.OnOff(
                isOn = (action.command as CommandValue.OnOff).turnOn
            )
            is CommandValue.Level -> DeviceState.Level(
                value = (action.command as CommandValue.Level).value
            )
            is CommandValue.Color -> DeviceState.Color(
                hueDegrees = (action.command as CommandValue.Color).hueDegrees,
                saturation = (action.command as CommandValue.Color).saturation
            )
            is CommandValue.ColorTemperature -> DeviceState.ColorTemperature(
                kelvin = (action.command as CommandValue.ColorTemperature).kelvin
            )
            is CommandValue.Climate -> DeviceState.Climate(
                targetCelsius = (action.command as CommandValue.Climate).targetCelsius,
                mode = com.elysium.nexus.fabric.canonical.ClimateMode.Auto
            )
            is CommandValue.Lock -> DeviceState.Lock(
                locked = (action.command as CommandValue.Lock).locked,
                source = com.elysium.nexus.fabric.canonical.LockSource.App
            )
            is CommandValue.Position -> DeviceState.Position(
                percentOpen = (action.command as CommandValue.Position).percentOpen
            )
            is CommandValue.Media -> DeviceState.Media(
                playing = (action.command as CommandValue.Media).play
            )
            is CommandValue.Noop -> null
        }
    }
}
