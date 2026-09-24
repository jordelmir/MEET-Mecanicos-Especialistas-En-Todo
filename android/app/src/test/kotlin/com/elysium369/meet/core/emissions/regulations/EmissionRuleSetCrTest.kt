package com.elysium369.meet.core.emissions.regulations

import com.elysium369.meet.core.emissions.domain.Evaluation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStreamReader

class EmissionRuleSetCrTest {

    @Test
    fun `boundary 1994 vehicle receives pre-1995 limits`() {
        val profile = RegulatoryVehicleProfile(modelYear = 1994)
        val ruleSet = CostaRicaGasolineRules.resolveRuleSet(profile)

        val idleCo = ruleSet.idleLimits.find { it.metric == GasMetric.CO }
        val idleHc = ruleSet.idleLimits.find { it.metric == GasMetric.HC }

        assertEquals(4.50, idleCo!!.max!!, 0.001)
        assertEquals(650.0, idleHc!!.max!!, 0.001)
    }

    @Test
    fun `boundary 1998 vehicle receives 1995-1998 limits`() {
        val profile = RegulatoryVehicleProfile(modelYear = 1998)
        val ruleSet = CostaRicaGasolineRules.resolveRuleSet(profile)

        val idleCo = ruleSet.idleLimits.find { it.metric == GasMetric.CO }
        val idleHc = ruleSet.idleLimits.find { it.metric == GasMetric.HC }
        val accelCo = ruleSet.acceleratedLimits.find { it.metric == GasMetric.CO }

        assertEquals(1.00, idleCo!!.max!!, 0.001)
        assertEquals(300.0, idleHc!!.max!!, 0.001)
        assertEquals(0.80, accelCo!!.max!!, 0.001)
    }

    @Test
    fun `boundary 1999 vehicle receives post-1999 strict limits`() {
        val profile = RegulatoryVehicleProfile(modelYear = 1999)
        val ruleSet = CostaRicaGasolineRules.resolveRuleSet(profile)

        val idleCo = ruleSet.idleLimits.find { it.metric == GasMetric.CO }
        val idleHc = ruleSet.idleLimits.find { it.metric == GasMetric.HC }
        val idleCo2 = ruleSet.idleLimits.find { it.metric == GasMetric.CO2 }

        val accelCo = ruleSet.acceleratedLimits.find { it.metric == GasMetric.CO }
        val accelHc = ruleSet.acceleratedLimits.find { it.metric == GasMetric.HC }
        val accelCo2 = ruleSet.acceleratedLimits.find { it.metric == GasMetric.CO2 }

        assertEquals(0.50, idleCo!!.max!!, 0.001)
        assertEquals(125.0, idleHc!!.max!!, 0.001)
        assertEquals(10.0, idleCo2!!.min!!, 0.001)

        assertEquals(0.30, accelCo!!.max!!, 0.001)
        assertEquals(100.0, accelHc!!.max!!, 0.001)
        assertEquals(12.0, accelCo2!!.min!!, 0.001)
    }

    @Test
    fun `lambda regulation boundary 2012-10-25 versus 2012-10-26`() {
        val beforeCutoff = RegulatoryVehicleProfile(
            modelYear = 2012,
            costaRicaEntryDate = "2012-10-25"
        )
        assertFalse(
            "Vehicles entered on or before 2012-10-25 are exempt from mandatory lambda limit",
            CostaRicaGasolineRules.isLambdaRegulationApplicable(beforeCutoff)
        )

        val atCutoff = RegulatoryVehicleProfile(
            modelYear = 2012,
            costaRicaEntryDate = "2012-10-26"
        )
        assertTrue(
            "Vehicles entered on or after 2012-10-26 must comply with lambda regulation",
            CostaRicaGasolineRules.isLambdaRegulationApplicable(atCutoff)
        )

        val year2013 = RegulatoryVehicleProfile(modelYear = 2013)
        assertTrue(
            "Model year 2013 and above defaults to lambda applicable",
            CostaRicaGasolineRules.isLambdaRegulationApplicable(year2013)
        )
    }

    @Test
    fun `golden test - Accent 2005 DEKRA test sheet evaluation replicates official results`() {
        val inputStream = javaClass.classLoader?.getResourceAsStream("emissions/accent_2005_dekra_case_001.json")
            ?: error("Missing golden fixture accent_2005_dekra_case_001.json")

        val jsonStr = InputStreamReader(inputStream).readText()
        val root = Json.parseToJsonElement(jsonStr).jsonObject

        val vehicleJson = root["vehicle"]!!.jsonObject
        val modelYear = vehicleJson["year"]!!.jsonPrimitive.int
        val entryDate = vehicleJson["costaRicaEntryDate"]!!.jsonPrimitive.content

        val profile = RegulatoryVehicleProfile(
            modelYear = modelYear,
            costaRicaEntryDate = entryDate
        )
        val ruleSet = CostaRicaGasolineRules.resolveRuleSet(profile)

        val measured = root["measuredData"]!!.jsonObject
        val idle = measured["idle"]!!.jsonObject
        val accel = measured["accelerated"]!!.jsonObject

        val idleCoVal = idle["coVolPct"]!!.jsonPrimitive.double
        val idleHcVal = idle["hcPpm"]!!.jsonPrimitive.double
        val idleCo2Val = idle["co2VolPct"]!!.jsonPrimitive.double

        val accelCoVal = accel["coVolPct"]!!.jsonPrimitive.double
        val accelHcVal = accel["hcPpm"]!!.jsonPrimitive.double
        val accelCo2Val = accel["co2VolPct"]!!.jsonPrimitive.double

        // 1. Evaluate Idle against limits (CO <= 0.50, HC <= 125, CO2 >= 10.0)
        val idleCoLimit = ruleSet.idleLimits.first { it.metric == GasMetric.CO }
        val idleHcLimit = ruleSet.idleLimits.first { it.metric == GasMetric.HC }
        val idleCo2Limit = ruleSet.idleLimits.first { it.metric == GasMetric.CO2 }

        assertEquals("Idle CO (0.62%) > 0.50% must FAIL", Evaluation.FAIL, idleCoLimit.evaluatePoint(idleCoVal))
        assertEquals("Idle HC (320 ppm) > 125 ppm must FAIL", Evaluation.FAIL, idleHcLimit.evaluatePoint(idleHcVal))
        assertEquals("Idle CO2 (12.9%) >= 10.0% must PASS", Evaluation.PASS, idleCo2Limit.evaluatePoint(idleCo2Val))

        // 2. Evaluate Accelerated against limits (CO <= 0.30, HC <= 100, CO2 >= 12.0)
        val accelCoLimit = ruleSet.acceleratedLimits.first { it.metric == GasMetric.CO }
        val accelHcLimit = ruleSet.acceleratedLimits.first { it.metric == GasMetric.HC }
        val accelCo2Limit = ruleSet.acceleratedLimits.first { it.metric == GasMetric.CO2 }

        assertEquals("Accel CO (0.64%) > 0.30% must FAIL", Evaluation.FAIL, accelCoLimit.evaluatePoint(accelCoVal))
        assertEquals("Accel HC (381 ppm) > 100 ppm must FAIL", Evaluation.FAIL, accelHcLimit.evaluatePoint(accelHcVal))
        assertEquals("Accel CO2 (11.9%) < 12.0% must FAIL", Evaluation.FAIL, accelCo2Limit.evaluatePoint(accelCo2Val))

        // 3. Evaluate Accelerated Lambda (entry date 2012-10-29 >= 2012-10-26 cutoff)
        val accelLambdaLimit = ruleSet.acceleratedLimits.firstOrNull { it.metric == GasMetric.LAMBDA }
        assertNotNull("Vehicle entering on 2012-10-29 must have Lambda regulation limit", accelLambdaLimit)
        val accelLambdaVal = accel["lambda"]!!.jsonPrimitive.double
        assertEquals("Accel Lambda (1.293) outside 0.93..1.07 must FAIL", Evaluation.FAIL, accelLambdaLimit!!.evaluatePoint(accelLambdaVal))
    }
}
