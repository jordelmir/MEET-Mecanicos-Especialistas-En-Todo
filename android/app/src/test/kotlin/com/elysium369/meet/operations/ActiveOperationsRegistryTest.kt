package com.elysium369.meet.operations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveOperationsRegistryTest {
    @Test
    fun `operation survives unrelated navigation because registry has no route mutation`() {
        val registry = ActiveOperationsRegistry()
        registry.upsert(obdOperation())
        assertTrue(registry.operations.value.containsKey("obd-session"))
        assertTrue(ActiveOperationsRegistry::class.java.methods.none {
            it.name.contains("navigation", true) || it.name.contains("route", true)
        })
    }

    @Test
    fun `only operation authority completion removes active operation`() {
        val registry = ActiveOperationsRegistry()
        registry.upsert(obdOperation())
        registry.heartbeat("obd-session", OperationState.RUNNING, nowEpochMs = 2L)
        assertEquals(OperationState.RUNNING, registry.operations.value.getValue("obd-session").state)
        registry.complete("obd-session")
        assertTrue(registry.operations.value.isEmpty())
    }

    private fun obdOperation() = ActiveOperation(
        operationId = "obd-session",
        type = "OBD_SESSION",
        vehicleId = "vehicle-1",
        startedAtEpochMs = 1L,
        state = OperationState.STARTING,
        progress = null,
        owner = OperationOwner.FOREGROUND_SERVICE_SCOPED,
        recoverability = OperationRecoverability.ACTIVITY_RECREATION,
        lastHeartbeatEpochMs = 1L,
    )
}
