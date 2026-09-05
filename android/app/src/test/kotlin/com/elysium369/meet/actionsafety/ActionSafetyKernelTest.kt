package com.elysium369.meet.actionsafety

import com.elysium369.meet.authority.ElysiumAuthorityKernel
import com.elysium369.meet.identity.ActivePrincipal
import com.elysium369.meet.identity.ActivePrincipalProvider
import com.elysium369.meet.vss.VehicleSignalGraph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionSafetyKernelTest {

    private lateinit var signalGraph: VehicleSignalGraph
    private lateinit var authorityKernel: ElysiumAuthorityKernel
    private lateinit var safetyKernel: ActionSafetyKernel

    private val fakePrincipal = ActivePrincipal.authenticated("mechanic_carlos_007")
    private val fakePrincipalProvider = object : ActivePrincipalProvider {
        override fun current(): ActivePrincipal = fakePrincipal
    }

    @Before
    fun setUp() {
        signalGraph = VehicleSignalGraph()
        authorityKernel = ElysiumAuthorityKernel(fakePrincipalProvider)
        safetyKernel = ActionSafetyKernel(authorityKernel, signalGraph)

        // Seed safe baseline vehicle signals (stationary, healthy battery)
        signalGraph.ingestObdPid("010D", 0.0)    // 0 km/h
        signalGraph.ingestObdPid("0142", 12.8)   // 12.8 V
    }

    @Test
    fun `executeAction completes full 10-phase pipeline when verified and stationary`() = runBlocking {
        var hardwareExecuted = false
        var postVerified = false

        val intent = ActionIntent(
            actionType = ActionType.CLEAR_DTC,
            initiatorPrincipalId = "mechanic_carlos_007",
            targetEntityId = "vehicle_vin_123",
            preconditions = ActionPreconditions(
                requireStationary = true,
                minBatteryVoltage = 12.0f,
                requireUserConfirmation = true,
            ),
        )

        val receipt = safetyKernel.executeAction(
            intent = intent,
            isUserConfirmed = true,
            hardwareExecutor = { lease ->
                assertTrue(lease >= 1000L)
                hardwareExecuted = true
                true // positive ACK
            },
            postVerifier = {
                postVerified = true
                true // verification passed
            },
        )

        assertTrue(hardwareExecuted)
        assertTrue(postVerified)
        assertEquals(ActionSafetyPhase.COMPLETED, receipt.phase)
        assertTrue(receipt.isVerified)
        assertNotNull(receipt.evidenceHash)
    }

    @Test
    fun `executeAction fails safe when vehicle is moving`() = runBlocking {
        // Vehicle moving at 45 km/h
        signalGraph.ingestObdPid("010D", 45.0)

        var hardwareTouched = false

        val intent = ActionIntent(
            actionType = ActionType.ECU_WRITE,
            initiatorPrincipalId = "mechanic_carlos_007",
            targetEntityId = "vehicle_vin_123",
            preconditions = ActionPreconditions(requireStationary = true),
        )

        val receipt = safetyKernel.executeAction(
            intent = intent,
            isUserConfirmed = true,
            hardwareExecutor = {
                hardwareTouched = true
                true
            },
            postVerifier = { true },
        )

        assertFalse(hardwareTouched)
        assertEquals(ActionSafetyPhase.FAILED_SAFE, receipt.phase)
        assertFalse(receipt.isVerified)
        assertTrue(receipt.failureReason?.contains("Vehicle is moving") == true)
    }

    @Test
    fun `executeAction fails safe when battery voltage is below minimum threshold`() = runBlocking {
        // Low battery voltage: 11.2 V
        signalGraph.ingestObdPid("0142", 11.2)

        var hardwareTouched = false

        val intent = ActionIntent(
            actionType = ActionType.ECU_FLASH_BLOCK,
            initiatorPrincipalId = "mechanic_carlos_007",
            targetEntityId = "vehicle_vin_123",
            preconditions = ActionPreconditions(minBatteryVoltage = 12.4f),
        )

        val receipt = safetyKernel.executeAction(
            intent = intent,
            isUserConfirmed = true,
            hardwareExecutor = {
                hardwareTouched = true
                true
            },
            postVerifier = { true },
        )

        assertFalse(hardwareTouched)
        assertEquals(ActionSafetyPhase.FAILED_SAFE, receipt.phase)
        assertTrue(receipt.failureReason?.contains("Battery voltage") == true)
    }

    @Test
    fun `executeAction invokes recovery routine when post verification fails`() = runBlocking {
        var recoveryInvoked = false

        val intent = ActionIntent(
            actionType = ActionType.ACTIVE_ACTUATOR_TEST,
            initiatorPrincipalId = "mechanic_carlos_007",
            targetEntityId = "vehicle_vin_123",
        )

        val receipt = safetyKernel.executeAction(
            intent = intent,
            isUserConfirmed = true,
            hardwareExecutor = { true }, // Hardware claimed OK
            postVerifier = { false },     // Physical measurement showed actuator didn't move!
            recoveryRoutine = {
                recoveryInvoked = true
            },
        )

        assertTrue(recoveryInvoked)
        assertEquals(ActionSafetyPhase.FAILED_SAFE, receipt.phase)
        assertFalse(receipt.isVerified)
        assertTrue(receipt.failureReason?.contains("post-action physical state verification failed", ignoreCase = true) == true)
    }

    @Test
    fun `executeAction blocks unconfirmed action requiring user confirmation`() = runBlocking {
        var hardwareTouched = false

        val intent = ActionIntent(
            actionType = ActionType.CLEAR_DTC,
            initiatorPrincipalId = "mechanic_carlos_007",
            targetEntityId = "vehicle_vin_123",
            preconditions = ActionPreconditions(requireUserConfirmation = true),
        )

        val receipt = safetyKernel.executeAction(
            intent = intent,
            isUserConfirmed = false, // Not confirmed
            hardwareExecutor = {
                hardwareTouched = true
                true
            },
            postVerifier = { true },
        )

        assertFalse(hardwareTouched)
        assertEquals(ActionSafetyPhase.FAILED_SAFE, receipt.phase)
        assertTrue(receipt.failureReason?.contains("explicit user confirmation") == true)
    }
}
