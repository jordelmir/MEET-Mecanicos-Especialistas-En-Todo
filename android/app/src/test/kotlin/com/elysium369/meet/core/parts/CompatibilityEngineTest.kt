package com.elysium369.meet.core.parts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity tests: the Kotlin CompatibilityEngine MUST produce the same
 * verdicts and warnings as `lib/parts/compatibility.ts` for the same
 * input. The headline test is the spec's acceptance scenario for P0230.
 */
class CompatibilityEngineTest {

    private val baseVehicle = VehicleFingerprint(
        brand = "Hyundai",
        model = "Accent Verna",
        year = 2005,
        engine = "1.6 AT",
        transmission = "AUTOMATIC",
        fuel = "GASOLINE",
    )

    @Test
    fun `returns UNKNOWN when no context`() {
        val result = CompatibilityEngine.evaluate(
            CompatibilityContext(vehicle = VehicleFingerprint(), partName = "repuesto")
        )
        assertEquals(CompatibilityConfidence.UNKNOWN, result.confidence)
        assertTrue(result.requiredConfirmations.isNotEmpty())
    }

    @Test
    fun `promotes to EXACT only with VIN+OEM or closed tuple`() {
        // VIN + OEM => EXACT (and not fuel-pump)
        val r1 = CompatibilityEngine.evaluate(
            CompatibilityContext(
                vehicle = baseVehicle.copy(
                    vin = "KMHCN46C18U123456",
                    oemNumber = "27301-2B100",
                ),
                partName = "Bobina de encendido",
            ),
        )
        assertEquals(CompatibilityConfidence.EXACT, r1.confidence)

        // Closed tuple without VIN => EXACT.
        val r2 = CompatibilityEngine.evaluate(
            CompatibilityContext(
                vehicle = baseVehicle.copy(oemNumber = "27301-2B100"),
                partName = "Bobina de encendido",
            ),
        )
        assertEquals(CompatibilityConfidence.EXACT, r2.confidence)

        // brand+model+year+engine but no OEM => MEDIUM, not EXACT, not HIGH.
        val r3 = CompatibilityEngine.evaluate(
            CompatibilityContext(vehicle = baseVehicle, partName = "Bobina de encendido")
        )
        assertEquals(CompatibilityConfidence.MEDIUM, r3.confidence)
    }

    @Test
    fun `produces MEDIUM for brand+model+year without OEM and surfaces safety for brakes`() {
        val result = CompatibilityEngine.evaluate(
            CompatibilityContext(vehicle = baseVehicle, partName = "Pastilla de freno delantero")
        )
        assertEquals(CompatibilityConfidence.MEDIUM, result.confidence)
        assertTrue(result.warnings.any { it.code == "CRITICAL_SAFETY_PART" })
    }

    @Test
    fun `produces LOW for brand+model alone`() {
        val result = CompatibilityEngine.evaluate(
            CompatibilityContext(
                vehicle = VehicleFingerprint(brand = "Hyundai", model = "Accent Verna"),
                partName = "Bobina de encendido",
            )
        )
        assertEquals(CompatibilityConfidence.LOW, result.confidence)
    }

    @Test
    fun `produces LOW when only position and part name are known`() {
        val result = CompatibilityEngine.evaluate(
            CompatibilityContext(
                vehicle = VehicleFingerprint(),
                partName = "Filtro de aire",
                position = PartPosition.ENGINE,
            )
        )
        assertEquals(CompatibilityConfidence.LOW, result.confidence)
    }

    /* ----- P0230 + fuel pump (the headline scenario) ----- */

    private val p0230Ctx = CompatibilityContext(
        vehicle = baseVehicle,
        partName = "Bomba de combustible",
        dtcCodes = listOf("P0230"),
    )

    @Test
    fun `P0230 + fuel pump does NOT mark EXACT`() {
        val result = CompatibilityEngine.evaluate(p0230Ctx)
        assertNotEquals(CompatibilityConfidence.EXACT, result.confidence)
    }

    @Test
    fun `P0230 + fuel pump emits the verbatim anti-fraud warning`() {
        val result = CompatibilityEngine.evaluate(p0230Ctx)
        val match = result.warnings.any { w ->
            w.message.contains("No reemplazar bomba de combustible sin confirmar antes") &&
                w.message.contains("alimentación") &&
                w.message.contains("presión") &&
                w.message.contains("relé")
        }
        assertTrue("expected the verbatim P0230 fuel-pump warning", match)
    }

    @Test
    fun `P0230 + fuel pump still asks for missing VIN and OEM`() {
        val result = CompatibilityEngine.evaluate(p0230Ctx)
        assertTrue(result.warnings.any { it.code == "NO_VIN" })
        assertTrue(result.warnings.any { it.code == "NO_OEM" })
    }

    @Test
    fun `P0230 + fuel pump recommends verification questions for the supplier`() {
        val result = CompatibilityEngine.evaluate(p0230Ctx)
        assertTrue(result.recommendedQuestions.isNotEmpty())
        assertTrue(result.recommendedQuestions.any { it.lowercase().contains("relé") })
    }

    @Test
    fun `fuel pump without P0230 still emits safety warning but no BLOCK`() {
        val result = CompatibilityEngine.evaluate(
            CompatibilityContext(vehicle = baseVehicle, partName = "Bomba de combustible")
        )
        assertTrue(result.warnings.any { it.code == "CRITICAL_SAFETY_PART" })
        assertTrue(result.warnings.none { it.code == "DTC_P0230_PUMP_REQUIRES_CONFIRMATION" })
    }

    @Test
    fun `relay for P0230 does NOT trigger the BLOCK warning`() {
        val result = CompatibilityEngine.evaluate(
            CompatibilityContext(
                vehicle = baseVehicle,
                partName = "Relé de bomba de gasolina",
                position = PartPosition.FUSE_BOX,
                dtcCodes = listOf("P0230"),
            )
        )
        assertTrue(result.warnings.none { it.code == "DTC_P0230_PUMP_REQUIRES_CONFIRMATION" })
    }

    @Test
    fun `isCriticalSafetyPart classifier matches the spec taxonomy`() {
        assertTrue(isCriticalSafetyPart("Bomba de combustible"))
        assertTrue(isCriticalSafetyPart("Pastilla de freno delantero"))
        assertTrue(isCriticalSafetyPart("Rotula suspension"))
        assertTrue(isCriticalSafetyPart("Bolsa de aire conductor"))
        assertTrue(isCriticalSafetyPart("Bateria alta tensión"))
        assertTrue(!isCriticalSafetyPart("Filtro de aire"))
        assertTrue(!isCriticalSafetyPart("Espejo retrovisor"))
    }
}
