package com.elysium369.meet.core.emissions.analysis

import com.elysium369.meet.core.emissions.domain.Evaluation
import com.elysium369.meet.core.emissions.preitv.PreItvVerdict
import com.elysium369.meet.core.emissions.regulations.RegulatoryVehicleProfile
import com.elysium369.meet.core.obd.Mode06TestResult
import com.elysium369.meet.core.obd.Mode06Verdict
import com.elysium369.meet.core.obd.MonitorStatus
import com.elysium369.meet.core.obd.ReadinessResult
import org.junit.Assert.*
import org.junit.Test

class EmissionsEngineTest {

    private val engine = EmissionsEngine()

    @Test
    fun `when disconnected never outputs synthetic passing numbers`() {
        val input = EmissionsEngineInput(
            rpm = null,
            ectC = null,
            stftPct = null,
            ltftPct = null,
            lambda = null,
            isConnected = false
        )

        val output = engine.evaluate(input)

        assertFalse(output.isConnected)
        assertEquals("DISCONNECTED", output.combustionAssessment.coEstimate.modelVersion)
        assertEquals(0.0, output.combustionAssessment.coEstimate.pointEstimate, 0.001)
        assertEquals(PreItvVerdict.INCONCLUSIVE, output.overallVerdict)
        assertEquals(Evaluation.NOT_APPLICABLE, output.coEvaluation)
        assertEquals(Evaluation.INCONCLUSIVE, output.readinessEvaluation)
    }

    @Test
    fun `when readiness is incomplete with catalyst incomplete verdict cannot be low risk`() {
        val monitors: List<MonitorStatus> = listOf(
            MonitorStatus("Misfire", available = true, complete = true),
            MonitorStatus("Fuel System", available = true, complete = true),
            MonitorStatus("Components", available = true, complete = true),
            MonitorStatus("Catalyst", available = true, complete = false), // INC
            MonitorStatus("EVAP System", available = true, complete = false), // INC
            MonitorStatus("O2 Sensor", available = true, complete = true),
            MonitorStatus("O2 Heater", available = true, complete = true)
        )
        val readiness = ReadinessResult(milOn = false, dtcCount = 0, monitors = monitors)

        // Perfect trims and lambda
        val input = EmissionsEngineInput(
            rpm = 750.0,
            ectC = 90.0,
            stftPct = 0.0,
            ltftPct = 0.0,
            lambda = 1.000,
            readinessResult = readiness,
            activeProfile = RegulatoryVehicleProfile(modelYear = 2005),
            physicalSampleCount = 20
        )

        val output = engine.evaluate(input)

        assertEquals(Evaluation.FAIL, output.readinessEvaluation)
        assertNotEquals("Incomplete monitors MUST NOT result in LOW_RISK", PreItvVerdict.LOW_RISK, output.overallVerdict)
        assertEquals(PreItvVerdict.HIGH_RISK, output.overallVerdict)
        assertTrue(output.causalExplanations.any { it.contains("Monitores de emisión incompletos") })
    }

    @Test
    fun `when Mode 06 has fails verdict is high risk`() {
        val mode06Fails = listOf(
            Mode06TestResult(
                mid = "\$09",
                tid = "\$8A",
                componentName = "Monitor Legacy \$09 (CID \$8A)",
                testName = "Prueba de sensor de oxígeno",
                value = 166.0f,
                minLimit = null,
                maxLimit = 163.0f,
                unit = "raw",
                passed = false,
                verdict = Mode06Verdict.FAIL
            )
        )

        val input = EmissionsEngineInput(
            rpm = 750.0,
            ectC = 90.0,
            stftPct = 0.0,
            ltftPct = 0.0,
            lambda = 1.000,
            mode06Results = mode06Fails,
            activeProfile = RegulatoryVehicleProfile(modelYear = 2005),
            physicalSampleCount = 20
        )

        val output = engine.evaluate(input)

        assertEquals(Evaluation.FAIL, output.mode06Evaluation)
        assertEquals(PreItvVerdict.HIGH_RISK, output.overallVerdict)
        assertTrue(output.causalExplanations.any { it.contains("Mode \$06") })
    }

    @Test
    fun `when physical samples insufficient ignores sluggish O2 penalty to avoid flatline false positives`() {
        val dummySluggishO2 = OxygenSignalAnalyzer().analyze(
            listOf(
                OxygenSample(1000L, 0.70),
                OxygenSample(2000L, 0.70),
                OxygenSample(3000L, 0.70),
                OxygenSample(4000L, 0.70)
            )
        )

        // Only 3 physical samples captured
        val input = EmissionsEngineInput(
            rpm = 750.0,
            ectC = 88.0,
            stftPct = 0.0,
            ltftPct = 0.0,
            lambda = 1.000,
            o2UpstreamFeatures = dummySluggishO2,
            activeProfile = RegulatoryVehicleProfile(modelYear = 2005),
            physicalSampleCount = 3
        )

        val output = engine.evaluate(input)

        // Base idle CO should be 0.15% without artificial 0.20 penalty
        assertEquals(0.15, output.combustionAssessment.coEstimate.pointEstimate, 0.05)
    }

    @Test
    fun `when trims are strongly negative and lambda is high flags unmetered air and exhaust leak correlation`() {
        val input = EmissionsEngineInput(
            rpm = 2500.0,
            ectC = 92.0,
            stftPct = -8.0,
            ltftPct = -8.3, // Combined -16.3%
            lambda = 1.25, // Lean according to sensor
            activeProfile = RegulatoryVehicleProfile(modelYear = 2005),
            isAcceleratedRpm = true,
            physicalSampleCount = 25
        )

        val output = engine.evaluate(input)

        assertTrue(
            "Should identify exhaust leak or false lean signature",
            output.causalExplanations.any { it.contains("fuga en escape") || it.contains("aire falso") || it.contains("Lambda alta") }
        )
    }

    @Test
    fun `reproduces realistic 2005 Accent failure matching physical DEKRA audit`() {
        val monitors: List<MonitorStatus> = listOf(
            MonitorStatus("Misfire", available = true, complete = true),
            MonitorStatus("Fuel System", available = true, complete = true),
            MonitorStatus("Components", available = true, complete = true),
            MonitorStatus("Catalyst", available = true, complete = false),
            MonitorStatus("EVAP System", available = true, complete = false),
            MonitorStatus("O2 Sensor", available = true, complete = true),
            MonitorStatus("O2 Heater", available = true, complete = true)
        )
        val readiness = ReadinessResult(milOn = true, dtcCount = 2, monitors = monitors)

        // 1. Idle test (MIL ON and incomplete monitors cause HIGH_RISK)
        val idleInput = EmissionsEngineInput(
            rpm = 750.0,
            ectC = 88.0,
            stftPct = -8.0,
            ltftPct = -8.3,
            lambda = 1.124,
            readinessResult = readiness,
            activeProfile = RegulatoryVehicleProfile(
                modelYear = 2005,
                costaRicaEntryDate = "2012-10-29"
            ),
            isAcceleratedRpm = false,
            physicalSampleCount = 30
        )
        val idleOutput = engine.evaluate(idleInput)
        assertEquals(PreItvVerdict.HIGH_RISK, idleOutput.overallVerdict)
        assertEquals(Evaluation.FAIL, idleOutput.readinessEvaluation)

        // 2. Accelerated test (DEKRA 2500 RPM: Lambda 1.293 fails official 1.00 ± 0.07 limit)
        val accelInput = idleInput.copy(
            rpm = 2500.0,
            isAcceleratedRpm = true,
            lambda = 1.293
        )
        val accelOutput = engine.evaluate(accelInput)
        assertEquals(PreItvVerdict.HIGH_RISK, accelOutput.overallVerdict)
        assertEquals(Evaluation.FAIL, accelOutput.lambdaEvaluation)
    }
}
