package com.elysium.nexus.fabric.adapter

import com.elysium.nexus.fabric.adapter.alexa.AlexaAdapter
import com.elysium.nexus.fabric.adapter.applehome.AppleHomeAdapter
import com.elysium.nexus.fabric.adapter.ble.BleAdapter
import com.elysium.nexus.fabric.adapter.googlehome.GoogleHomeAdapter
import com.elysium.nexus.fabric.adapter.infrared.InfraredAdapter
import com.elysium.nexus.fabric.adapter.media.MediaAdapter
import com.elysium.nexus.fabric.adapter.matter.MatterAdapter
import com.elysium.nexus.fabric.adapter.mqtt.MqttAdapter
import com.elysium.nexus.fabric.adapter.onvif.OnvifAdapter
import com.elysium.nexus.fabric.adapter.vendor.VendorAdapter
import com.elysium.nexus.fabric.adapter.zigbee.ZigbeeAdapter
import com.elysium.nexus.fabric.adapter.zwave.ZWaveAdapter
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAdapterContractTest {

    private fun allAdapters(): List<DeviceAdapter> = listOf(
        MatterAdapter(),
        ZigbeeAdapter(),
        ZWaveAdapter(),
        MqttAdapter(brokerUrl = "tcp://localhost:1883"),
        OnvifAdapter(),
        InfraredAdapter(),
        BleAdapter(),
        MediaAdapter(),
        AlexaAdapter(),
        GoogleHomeAdapter(),
        AppleHomeAdapter(),
        VendorAdapter(vendorName = "TestVendor", baseUrl = "https://example.com")
    )

    @Test
    fun allAdapters_haveNonBlankLabel() {
        allAdapters().forEach { adapter ->
            assertTrue(
                "${adapter::class.simpleName}.label must not be blank",
                adapter.label.isNotBlank()
            )
        }
    }

    @Test
    fun allAdapters_haveCorrectProtocol() {
        assertEquals(Protocol.Matter, MatterAdapter().protocol)
        assertEquals(Protocol.Zigbee, ZigbeeAdapter().protocol)
        assertEquals(Protocol.ZWave, ZWaveAdapter().protocol)
        assertEquals(Protocol.Mqtt, MqttAdapter(brokerUrl = "").protocol)
        assertEquals(Protocol.Onvif, OnvifAdapter().protocol)
        assertEquals(Protocol.DirectIr, InfraredAdapter().protocol)
        assertEquals(Protocol.Ble, BleAdapter().protocol)
        assertEquals(Protocol.Rtsp, MediaAdapter().protocol)
        assertEquals(Protocol.VendorRest, AlexaAdapter().protocol)
        assertEquals(Protocol.VendorRest, GoogleHomeAdapter().protocol)
        assertEquals(Protocol.Ble, AppleHomeAdapter().protocol)
        assertEquals(Protocol.VendorRest, VendorAdapter(vendorName = "X", baseUrl = "").protocol)
    }

    @Test
    fun allAdapters_haveNonEmptyCapabilities() {
        allAdapters().forEach { adapter ->
            assertTrue(
                "${adapter::class.simpleName}.supportedCapabilities must not be empty",
                adapter.supportedCapabilities.isNotEmpty()
            )
        }
    }

    @Test
    fun allAdapters_startInIdleState() {
        allAdapters().forEach { adapter ->
            assertEquals(
                "${adapter::class.simpleName} must start in Idle",
                AdapterState.Idle,
                adapter.state.value
            )
        }
    }

    @Test
    fun allAdapters_transitionToActiveOnStart() = runTest {
        allAdapters().forEach { adapter ->
            val result = adapter.start()
            assertTrue(
                "${adapter::class.simpleName}.start() must return Ok",
                result is AdapterResult.Ok
            )
            assertEquals(
                "${adapter::class.simpleName} must be Active after start()",
                AdapterState.Active,
                adapter.state.value
            )
            adapter.stop()
        }
    }

    @Test
    fun allAdapters_transitionToReleasedOnStop() = runTest {
        allAdapters().forEach { adapter ->
            adapter.start()
            val result = adapter.stop()
            assertTrue(
                "${adapter::class.simpleName}.stop() must return Ok",
                result is AdapterResult.Ok
            )
            assertEquals(
                "${adapter::class.simpleName} must be Released after stop()",
                AdapterState.Released,
                adapter.state.value
            )
        }
    }

    @Test
    fun allAdapters_startWithEmptyDevices() {
        allAdapters().forEach { adapter ->
            assertTrue(
                "${adapter::class.simpleName}.devices must start empty",
                adapter.devices.value.isEmpty()
            )
        }
    }

    @Test
    fun stubAdapters_returnErrorOnScan() = runTest {
        allAdapters().forEach { adapter ->
            adapter.start()
            val result = adapter.scan()
            assertTrue(
                "${adapter::class.simpleName}.scan() should return Error for stubs",
                result is ScanResult.Error
            )
            adapter.stop()
        }
    }

    @Test
    fun stubAdapters_returnErrorOnRead() = runTest {
        allAdapters().forEach { adapter ->
            adapter.start()
            val result = adapter.read(DeviceId("test-device"))
            assertTrue(
                "${adapter::class.simpleName}.read() should return Error for stubs",
                result is ReadResult.Error
            )
            adapter.stop()
        }
    }

    @Test
    fun stubAdapters_returnErrorOnWrite() = runTest {
        allAdapters().forEach { adapter ->
            adapter.start()
            val result = adapter.write(DeviceId("test-device"), DeviceState.OnOff(isOn = true))
            assertTrue(
                "${adapter::class.simpleName}.write() should return Error for stubs",
                result is WriteResult.Error
            )
            adapter.stop()
        }
    }

    @Test
    fun stubAdapters_returnOkOnUnsubscribe() = runTest {
        allAdapters().forEach { adapter ->
            adapter.start()
            val result = adapter.unsubscribe(DeviceId("test-device"))
            assertTrue(
                "${adapter::class.simpleName}.unsubscribe() should return Ok",
                result is AdapterResult.Ok
            )
            adapter.stop()
        }
    }

    @Test
    fun matterAdapter_hasExpectedCapabilities() {
        val caps = MatterAdapter().supportedCapabilities
        assertTrue(Capability.OnOff in caps)
        assertTrue(Capability.Level in caps)
        assertTrue(Capability.Color in caps)
        assertTrue(Capability.Temperature in caps)
        assertTrue(Capability.LockUnlock in caps)
        assertTrue(Capability.Scene in caps)
    }

    @Test
    fun zigbeeAdapter_hasExpectedCapabilities() {
        val caps = ZigbeeAdapter().supportedCapabilities
        assertTrue(Capability.OnOff in caps)
        assertTrue(Capability.MotionDetection in caps)
        assertTrue(Capability.ContactDetection in caps)
    }

    @Test
    fun zwaveAdapter_hasExpectedCapabilities() {
        val caps = ZWaveAdapter().supportedCapabilities
        assertTrue(Capability.OnOff in caps)
        assertTrue(Capability.LockUnlock in caps)
        assertTrue(Capability.EnergyRead in caps)
    }

    @Test
    fun mqttAdapter_hasExpectedCapabilities() {
        val caps = MqttAdapter(brokerUrl = "").supportedCapabilities
        assertTrue(Capability.SmokeDetection in caps)
        assertTrue(Capability.WaterLeakDetection in caps)
        assertTrue(Capability.EnergyRead in caps)
    }

    @Test
    fun onvifAdapter_hasCameraCapabilities() {
        val caps = OnvifAdapter().supportedCapabilities
        assertTrue(Capability.CameraStream in caps)
        assertTrue(Capability.CameraPtz in caps)
    }

    @Test
    fun infraredAdapter_hasMediaCapabilities() {
        val caps = InfraredAdapter().supportedCapabilities
        assertTrue(Capability.MediaTransport in caps)
        assertTrue(Capability.Volume in caps)
        assertTrue(Capability.Channel in caps)
    }

    @Test
    fun alexaAdapter_hasVoiceCapabilities() {
        val caps = AlexaAdapter().supportedCapabilities
        assertTrue(Capability.Scene in caps)
        assertTrue(Capability.LockUnlock in caps)
    }

    @Test
    fun googleHomeAdapter_hasVoiceCapabilities() {
        val caps = GoogleHomeAdapter().supportedCapabilities
        assertTrue(Capability.Scene in caps)
        assertTrue(Capability.LockUnlock in caps)
        assertTrue(Capability.FanSpeed in caps)
    }

    @Test
    fun appleHomeKitAdapter_hasHomeKitCapabilities() {
        val caps = AppleHomeAdapter().supportedCapabilities
        assertTrue(Capability.MotionDetection in caps)
        assertTrue(Capability.ContactDetection in caps)
        assertTrue(Capability.LockUnlock in caps)
    }

    @Test
    fun vendorAdapter_isConfigurable() {
        val adapter = VendorAdapter(
            vendorName = "Tuya",
            baseUrl = "https://api.tuya.com"
        )
        assertEquals("Tuya", adapter.label)
        assertEquals(Protocol.VendorRest, adapter.protocol)
    }

    @Test
    fun errorCode_enum_hasExpectedValues() {
        val codes = ErrorCode.entries
        assertTrue(ErrorCode.NotStarted in codes)
        assertTrue(ErrorCode.AlreadyStarted in codes)
        assertTrue(ErrorCode.DeviceNotFound in codes)
        assertTrue(ErrorCode.DeviceOffline in codes)
        assertTrue(ErrorCode.AuthFailed in codes)
        assertTrue(ErrorCode.NetworkError in codes)
        assertTrue(ErrorCode.Timeout in codes)
        assertTrue(ErrorCode.HardwareUnavailable in codes)
    }

    @Test
    fun adapterState_enum_hasExpectedValues() {
        val states = AdapterState.entries
        assertEquals(7, states.size)
        assertTrue(AdapterState.Idle in states)
        assertTrue(AdapterState.Starting in states)
        assertTrue(AdapterState.Active in states)
        assertTrue(AdapterState.Scanning in states)
        assertTrue(AdapterState.Stopping in states)
        assertTrue(AdapterState.Released in states)
        assertTrue(AdapterState.Error in states)
    }
}
