package com.elysium369.meet.visualdiagnostics

import com.elysium369.meet.ai.DiagnosticAiContextBuilder
import com.elysium369.meet.data.visualdiagnostics.VisualDiagnosticRepositoryImpl
import com.elysium369.meet.domain.visualdiagnostics.EngineType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticAiContextBuilderTest {

    @Test
    fun alternatorContextIncludesVehicleComponentDtcsAndLivePids() {
        val repository = VisualDiagnosticRepositoryImpl()
        val alternator = repository.findComponent(EngineType.L4, "alternator")

        assertNotNull(alternator)

        val context = DiagnosticAiContextBuilder().build(
            vehicleLabel = "2020 Toyota Corolla 2.0",
            engineType = EngineType.L4,
            component = alternator!!,
            activeDtcs = setOf("P0562"),
            livePidValues = mapOf("0142" to "12.10 V")
        )

        assertTrue(context.contains("2020 Toyota Corolla 2.0"))
        assertTrue(context.contains("Alternador"))
        assertTrue(context.contains("P0562"))
        assertTrue(context.contains("P0563"))
        assertTrue(context.contains("0142"))
        assertTrue(context.contains("12.10 V"))
        assertTrue(context.contains("recommendedTests"))
    }
}
