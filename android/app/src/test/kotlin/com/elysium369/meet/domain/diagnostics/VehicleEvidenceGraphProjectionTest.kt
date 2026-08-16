package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.data.local.entities.DiagnosticFindingEntity
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleEvidenceGraphProjectionTest {
    @Test
    fun rebuildIsDeterministicAndIgnoresForeignVehicleHistory() {
        val finding = DiagnosticFindingEntity(
            id = "finding-1",
            vehicleId = "vehicle-1",
            ecuEndpointId = "ECM:7E8",
            diagnosticNamespace = "UDS",
            rawDtcIdentity = "123456",
            displayCode = "P1234",
            createdAtMs = 10,
            failureType = 0x11,
            vehicleBindingId = "binding-1",
        )
        val foreign = finding.copy(id = "foreign", vehicleId = "vehicle-2", vehicleBindingId = "binding-2")
        val observation = DiagnosticObservationEntity(
            id = "observation-1",
            findingId = finding.id,
            sessionId = "session-1",
            observedAt = 20,
            observationState = "OBSERVED",
            semantics = "ACTIVE",
            statusByte = null,
            sourceService = "19",
            exchangeId = "exchange-1",
            rawPayloadHash = "a".repeat(64),
            findingSequence = 1,
        )
        val input = VehicleEvidenceGraphProjectionInput(
            vehicleId = "vehicle-1",
            vehicleBindingId = "binding-1",
            findings = listOf(foreign, finding),
            observations = listOf(observation),
        )

        val first = DeterministicVehicleEvidenceGraphRepository.rebuild(input)
        val second = DeterministicVehicleEvidenceGraphRepository.rebuild(
            input.copy(findings = input.findings.reversed()),
        )

        assertEquals(first, second)
        assertEquals(setOf("vehicle:vehicle-1", "finding:finding-1", "observation:observation-1"), first.nodes.map { it.id }.toSet())
    }
}
