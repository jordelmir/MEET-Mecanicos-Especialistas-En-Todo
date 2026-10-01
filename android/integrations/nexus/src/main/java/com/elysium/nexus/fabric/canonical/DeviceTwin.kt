package com.elysium.nexus.fabric.canonical

/**
 * The §4.2 canonical device twin.
 *
 * A [DeviceTwin] is the system's local,
 * source-of-truth view of one physical
 * (or virtual) device. The twin is what
 * the UI displays, what automations read,
 * and what the audit log records. The
 * protocol adapters **populate** the twin
 * from the device's actual state; the
 * automation engine **commands** the
 * adapter via the twin.
 *
 * The twin is intentionally **immutable**
 * (Kotlin `data class`). Every state
 * change produces a new twin; the
 * previous twin is the "last known good".
 * The Hub persists a ring of recent
 * twins per device; the Android app and
 * desktop agents mirror.
 *
 * ## Why a twin and not a mutable device
 *
 * A mutable device object would let an
 * adapter mutate the canonical state
 * without going through the §28
 * automation engine. The twin is the
 * *boundary* between the adapter (which
 * reads from a real device) and the
 * engine (which decides what to do).
 * Mutations cross the boundary as
 * "new twin instances".
 */
data class DeviceTwin(
    /** Stable, opaque device id (UUID-derived). */
    val deviceId: DeviceId,
    /** Manufacturer display name; null when unknown. */
    val manufacturer: String? = null,
    /** Model display name; null when unknown. */
    val model: String? = null,
    /** The category of the device (light, lock, AC, …). */
    val deviceType: DeviceType,
    /** The capabilities the device actually exposes. */
    val capabilities: Set<Capability> = emptySet(),
    /** The most recent state observed or computed. */
    val reportedState: DeviceState = DeviceState.Unknown,
    /** The state the system *wants* the device to be in. */
    val desiredState: DeviceState = DeviceState.Unknown,
    /** The current connectivity class. */
    val connectivity: ConnectivityState = ConnectivityState.Unknown,
    /** Trust class of the device. */
    val trust: TrustState = TrustState.Untrusted,
    /** Protocol bindings currently active. */
    val protocolBindings: Set<ProtocolBinding> = emptySet(),
    /** Wall-clock nanoseconds of the last contact. */
    val lastSeenNs: Long = 0L,
    /** A user-facing label. */
    val label: String = ""
) {
    init {
        require(deviceId.value.isNotBlank()) {
            "DeviceId must be non-blank."
        }
        require(capabilities.isNotEmpty() || deviceType == DeviceType.Unknown) {
            "DeviceTwin must declare at least one capability " +
                "(or be explicitly DeviceType.Unknown)."
        }
        require(lastSeenNs >= 0L) {
            "lastSeenNs must be non-negative (got $lastSeenNs)."
        }
    }
}

/**
 * A stable, opaque device identifier. The id
 * is a UUID-derived string; the same physical
 * device has the same id across adapters and
 * reboots. The id is the join key for the
 * [DeviceKnowledgeGraph].
 */
@JvmInline
value class DeviceId(val value: String) {
    companion object {
        /** A canonical "unknown device" id. */
        val UNKNOWN: DeviceId = DeviceId("00000000-0000-0000-0000-000000000000")
    }
}

/**
 * The §5 device type. The enum is the
 * taxonomy used by the [DeviceKnowledgeGraph]
 * to group devices into rooms and zones.
 * The enum is **closed**; a new device type
 * is an ADR-worthy decision.
 */
enum class DeviceType {
    Unknown,
    Light,
    Switch,
    Outlet,
    Dimmer,
    Fan,
    Thermostat,
    AirConditioner,
    Heater,
    AirPurifier,
    Humidifier,
    Dehumidifier,
    Ventilator,
    Curtain,
    Blind,
    Shade,
    Awning,
    Skylight,
    Lock,
    Doorbell,
    GarageDoor,
    Gate,
    Camera,
    SensorMotion,
    SensorContact,
    SensorTemperature,
    SensorHumidity,
    SensorAirQuality,
    SensorSmoke,
    SensorCarbonMonoxide,
    SensorWaterLeak,
    SensorLight,
    SensorEnergy,
    SensorPresence,
    MediaPlayer,
    Television,
    Projector,
    Speaker,
    Soundbar,
    AvReceiver,
    StreamingDevice,
    ApplianceWasher,
    ApplianceDryer,
    ApplianceDishwasher,
    ApplianceOven,
    ApplianceCooktop,
    ApplianceMicrowave,
    ApplianceRefrigerator,
    ApplianceFreezer,
    ApplianceCoffee,
    ApplianceKettle,
    ApplianceAirFryer,
    ApplianceRiceCooker,
    ApplianceRangeHood,
    ApplianceRobotVacuum,
    ApplianceRobotMop,
    ApplianceWindowCleaner,
    ApplianceWaterHeater,
    AppliancePetFeeder,
    AppliancePetDoor,
    Irrigation,
    Pool,
    Spa,
    Fountain,
    OutdoorLight,
    WeatherStation,
    SmartMeter,
    SolarInverter,
    Battery,
    EvCharger,
    Generator,
    Computer,
    Console,
    Controller,
    Hub,
    Receiver
}

/**
 * The reported / desired state of a device.
 * The state is a sealed hierarchy: most
 * devices fit [OnOffState] or [LevelState];
 * richer devices (locks, ACs, cameras) carry
 * their own state. The engine maps a state
 * to the right adapter call.
 */
sealed class DeviceState {
    /** The state is not yet known (device just paired, never reported). */
    object Unknown : DeviceState()
    /** Boolean on/off. The workhorse. */
    data class OnOff(val isOn: Boolean) : DeviceState()
    /** 0..1 level (brightness, fan speed, volume). */
    data class Level(val value: Float) : DeviceState() {
        init {
            require(value in 0f..1f) {
                "Level.value must be in [0, 1] (got $value)."
            }
        }
    }
    /** Hue (0..360) + saturation (0..1). */
    data class Color(val hueDegrees: Float, val saturation: Float) : DeviceState() {
        init {
            require(hueDegrees in 0f..360f) {
                "Color.hueDegrees must be in [0, 360] (got $hueDegrees)."
            }
            require(saturation in 0f..1f) {
                "Color.saturation must be in [0, 1] (got $saturation)."
            }
        }
    }
    /** Kelvin color temperature (≈ 2200..6500 for white LEDs). */
    data class ColorTemperature(val kelvin: Int) : DeviceState() {
        init {
            require(kelvin in 1000..40000) {
                "ColorTemperature.kelvin must be in [1000, 40000] (got $kelvin)."
            }
        }
    }
    /** Climate target temperature in Celsius. */
    data class Climate(val targetCelsius: Float, val mode: ClimateMode) : DeviceState() {
        init {
            require(targetCelsius in -50f..150f) {
                "Climate.targetCelsius must be in [-50, 150] (got $targetCelsius)."
            }
        }
    }
    /** Lock state. */
    data class Lock(val locked: Boolean, val source: LockSource) : DeviceState()
    /** Position 0..1 (curtain open %, blind tilt, garage travel). */
    data class Position(val percentOpen: Float) : DeviceState() {
        init {
            require(percentOpen in 0f..1f) {
                "Position.percentOpen must be in [0, 1] (got $percentOpen)."
            }
        }
    }
    /** Media transport state. */
    data class Media(val playing: Boolean, val track: String? = null) : DeviceState()
    /** Energy read. */
    data class EnergyRead(val watts: Float, val kwhTotal: Float) : DeviceState()
    /** IR command to transmit. */
    data class IrCommand(
        val protocolName: String,
        val address: Int,
        val command: Int,
        val extras: Map<String, String> = emptyMap(),
        /** P0-6: Full IrSignal for lossless transmission. Adapter should use this when available. */
        val irSignal: com.elysium.nexus.core.device.IrSignal? = null
    ) : DeviceState()
}

/** Climate mode (HVAC). The enum is closed; new modes are an ADR. */
enum class ClimateMode {
    Off, Heat, Cool, Auto, Dry, FanOnly
}

/** Lock source: which subsystem last changed the lock. */
enum class LockSource {
    Manual, Key, App, Automation, Geofence, Voice, Unknown
}

/** Connectivity class. */
enum class ConnectivityState {
    Unknown,
    Online,
    Offline,
    Unreachable,
    Pairing,
    Recovering
}

/**
 * Trust class. Devices that speak a vendor API
 * with no attestation start as [Untrusted] and
 * graduate to [SelfDeclared] or [Attested] as
 * the user confirms them.
 */
enum class TrustState {
    Untrusted,
    SelfDeclared,
    Attested,
    ManufacturerCertified
}

/**
 * A protocol binding on a device. The same
 * physical device may have multiple bindings
 * (a light that is both Matter and BLE).
 * The Hub picks the *best* binding per
 * capability (the [Control] hierarchy in
 * `MASTER_ORDER.md` §2).
 */
data class ProtocolBinding(
    val protocol: Protocol,
    val endpoint: String,
    val capabilities: Set<Capability>
) {
    init {
        require(endpoint.isNotBlank()) {
            "ProtocolBinding.endpoint must be non-blank."
        }
    }
}

/** The protocol families. New families are an ADR. */
enum class Protocol {
    Unknown,
    HidOverUsb,
    HidOverBluetooth,
    HidOverBle,
    DirectIr,
    HubIr,
    Matter,
    Thread,
    Zigbee,
    ZWave,
    ZWaveLongRange,
    Ble,
    WiFi,
    Ethernet,
    Mqtt,
    Onvif,
    Rtsp,
    Rtsps,
    WebRtc,
    HdmiCec,
    VendorRest,
    VendorWebSocket,
    ElysiumLink
}
