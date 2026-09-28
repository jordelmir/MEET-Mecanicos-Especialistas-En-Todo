package com.elysium.nexus.fabric.simulator

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.DeviceType
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.ProtocolBinding
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §67 Simulator — Virtual Devices.
 *
 * Creates virtual devices for testing:
 * - Virtual LG TV
 * - Virtual PC
 * - Virtual IR receiver
 * - Virtual Matter light
 *
 * Allows testing:
 * - Routing
 * - Automation
 * - UI
 * - Plugins
 *
 * WITHOUT hardware.
 *
 * All virtual devices are clearly marked as:
 * ```
 * SIMULATED
 * ```
 *
 * Never treated as physical evidence.
 */
class VirtualDeviceSimulator {

    private val devices = mutableMapOf<DeviceId, VirtualDevice>()

    /**
     * Create a virtual TV device.
     */
    fun createVirtualTv(
        name: String = "Virtual LG TV",
        brand: String = "LG",
        model: String = "OLED C3"
    ): VirtualDevice {
        val device = VirtualTvDevice(
            name = name,
            brand = brand,
            model = model
        )
        devices[device.deviceId] = device
        return device
    }

    /**
     * Create a virtual light device.
     */
    fun createVirtualLight(
        name: String = "Virtual Matter Light"
    ): VirtualDevice {
        val device = VirtualLightDevice(name = name)
        devices[device.deviceId] = device
        return device
    }

    /**
     * Create a virtual IR receiver.
     */
    fun createVirtualIrReceiver(
        name: String = "Virtual IR Receiver"
    ): VirtualDevice {
        val device = VirtualIrReceiverDevice(name = name)
        devices[device.deviceId] = device
        return device
    }

    /**
     * Get a virtual device by ID.
     */
    fun getDevice(deviceId: DeviceId): VirtualDevice? = devices[deviceId]

    /**
     * Get all virtual devices.
     */
    fun allDevices(): List<VirtualDevice> = devices.values.toList()

    /**
     * Remove a virtual device.
     */
    fun removeDevice(deviceId: DeviceId): VirtualDevice? = devices.remove(deviceId)

    /**
     * Clear all virtual devices.
     */
    fun clear() = devices.clear()
}

/**
 * A virtual device for simulation.
 */
interface VirtualDevice {
    val deviceId: DeviceId
    val name: String
    val isSimulated: Boolean get() = true
    val deviceType: DeviceType
    val capabilities: Set<Capability>
    val protocolBindings: Set<ProtocolBinding>
    val state: Flow<DeviceState>

    /**
     * Execute a universal action on the virtual device.
     * Returns the new state after execution.
     */
    suspend fun execute(action: UniversalAction): DeviceState?

    /**
     * Read the current state.
     */
    fun readState(): DeviceState
}

/**
 * Virtual TV device.
 */
class VirtualTvDevice(
    override val name: String,
    private val brand: String,
    private val model: String
) : VirtualDevice {

    override val deviceId: DeviceId = DeviceId("virtual-tv-${System.currentTimeMillis()}")
    override val deviceType: DeviceType = DeviceType.Television
    override val capabilities: Set<Capability> = setOf(
        Capability.OnOff, Capability.Volume, Capability.Channel,
        Capability.InputSource, Capability.MediaTransport
    )
    override val protocolBindings: Set<ProtocolBinding> = setOf(
        ProtocolBinding(
            protocol = Protocol.WiFi,
            endpoint = "virtual://$deviceId",
            capabilities = capabilities
        )
    )

    private val _state = MutableStateFlow<DeviceState>(DeviceState.OnOff(isOn = true))
    override val state: Flow<DeviceState> = _state.asStateFlow()

    private var volume = 20
    private var isOn = true

    override suspend fun execute(action: UniversalAction): DeviceState? {
        return when (action) {
            is UniversalAction.PowerOn -> {
                isOn = true
                DeviceState.OnOff(isOn = true).also { _state.value = it }
            }
            is UniversalAction.PowerOff -> {
                isOn = false
                DeviceState.OnOff(isOn = false).also { _state.value = it }
            }
            is UniversalAction.PowerToggle -> {
                isOn = !isOn
                DeviceState.OnOff(isOn = isOn).also { _state.value = it }
            }
            is UniversalAction.VolumeUp -> {
                volume = (volume + 1).coerceAtMost(100)
                DeviceState.Level(value = volume / 100f).also { _state.value = it }
            }
            is UniversalAction.VolumeDown -> {
                volume = (volume - 1).coerceAtLeast(0)
                DeviceState.Level(value = volume / 100f).also { _state.value = it }
            }
            is UniversalAction.SetVolume -> {
                volume = (action.level * 100).toInt().coerceIn(0, 100)
                DeviceState.Level(value = action.level).also { _state.value = it }
            }
            else -> null
        }
    }

    override fun readState(): DeviceState = _state.value

    override fun toString(): String = "VirtualTvDevice($brand $model, SIMULATED)"
}

/**
 * Virtual light device.
 */
class VirtualLightDevice(override val name: String) : VirtualDevice {
    override val deviceId: DeviceId = DeviceId("virtual-light-${System.currentTimeMillis()}")
    override val deviceType: DeviceType = DeviceType.Light
    override val capabilities: Set<Capability> = setOf(Capability.OnOff, Capability.Level)
    override val protocolBindings: Set<ProtocolBinding> = setOf(
        ProtocolBinding(protocol = Protocol.WiFi, endpoint = "virtual://$deviceId", capabilities = capabilities)
    )

    private val _state = MutableStateFlow<DeviceState>(DeviceState.OnOff(isOn = false))
    override val state: Flow<DeviceState> = _state.asStateFlow()

    override suspend fun execute(action: UniversalAction): DeviceState? = when (action) {
        is UniversalAction.PowerOn -> DeviceState.OnOff(isOn = true).also { _state.value = it }
        is UniversalAction.PowerOff -> DeviceState.OnOff(isOn = false).also { _state.value = it }
        is UniversalAction.SetVolume -> DeviceState.Level(value = action.level).also { _state.value = it }
        else -> null
    }

    override fun readState(): DeviceState = _state.value
}

/**
 * Virtual IR receiver device.
 */
class VirtualIrReceiverDevice(override val name: String) : VirtualDevice {
    override val deviceId: DeviceId = DeviceId("virtual-ir-${System.currentTimeMillis()}")
    override val deviceType: DeviceType = DeviceType.Receiver
    override val capabilities: Set<Capability> = setOf(Capability.OnOff)
    override val protocolBindings: Set<ProtocolBinding> = setOf(
        ProtocolBinding(protocol = Protocol.DirectIr, endpoint = "virtual://$deviceId", capabilities = capabilities)
    )

    private val _state = MutableStateFlow<DeviceState>(DeviceState.OnOff(isOn = false))
    override val state: Flow<DeviceState> = _state.asStateFlow()

    override suspend fun execute(action: UniversalAction): DeviceState? = when (action) {
        is UniversalAction.PowerOn -> DeviceState.OnOff(isOn = true).also { _state.value = it }
        is UniversalAction.PowerOff -> DeviceState.OnOff(isOn = false).also { _state.value = it }
        else -> null
    }

    override fun readState(): DeviceState = _state.value
}
