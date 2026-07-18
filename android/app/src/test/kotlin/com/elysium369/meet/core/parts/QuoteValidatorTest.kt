package com.elysium369.meet.core.parts

import com.elysium369.meet.core.parts.CompatibilityConfidence
import com.elysium369.meet.core.parts.DraftQuote
import com.elysium369.meet.core.parts.PartAvailability
import com.elysium369.meet.core.parts.PartCondition
import com.elysium369.meet.core.parts.QuoteValidator
import com.elysium369.meet.core.parts.ValidationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteValidatorTest {

    private val cleanDraft = DraftQuote(
        partName = "Bobina de encendido",
        brand = "NGK",
        partNumber = "U5156",
        oemNumber = "27301-2B100",
        condition = PartCondition.NEW_OEM,
        availability = PartAvailability.IN_STOCK,
        price = 85.0,
        currency = "CRC",
        includesDelivery = false,
        deliveryFee = 0.0,
        estimatedDeliveryHours = 24,
        warrantyDays = 90,
        photoUrls = emptyList(),
        compatibilityConfidence = CompatibilityConfidence.EXACT,
        compatibilityNotes = "Verificado contra Hyundai Accent Verna 2005 1.6 G4FC",
        expiresInHours = 48,
        vehicleBrand = "Hyundai",
        vehicleModel = "Accent Verna",
        vehicleYear = 2005,
        vehicleEngine = "1.6 G4FC",
    )

    @Test
    fun `clean OEM quote is OK`() {
        assertEquals(ValidationLevel.OK, QuoteValidator.validate(cleanDraft).level)
    }

    @Test
    fun `USED part without photos is BLOCK`() {
        val r = QuoteValidator.validate(
            cleanDraft.copy(condition = PartCondition.USED, price = 30.0, photoUrls = emptyList())
        )
        assertEquals(ValidationLevel.BLOCK, r.level)
        assertTrue(r.errors.any { it.code == "USED_REQUIRES_PHOTO" })
    }

    @Test
    fun `REFURBISHED part without photos is BLOCK`() {
        val r = QuoteValidator.validate(
            cleanDraft.copy(condition = PartCondition.REFURBISHED, photoUrls = emptyList())
        )
        assertEquals(ValidationLevel.BLOCK, r.level)
    }

    @Test
    fun `EXACT compat without OEM and part number is BLOCK`() {
        val r = QuoteValidator.validate(
            cleanDraft.copy(oemNumber = "", partNumber = "", compatibilityConfidence = CompatibilityConfidence.EXACT)
        )
        assertEquals(ValidationLevel.BLOCK, r.level)
        assertTrue(r.errors.any { it.code == "EXACT_REQUIRES_OEM" })
    }

    @Test
    fun `EXACT with OEM and notes but no structured vehicle evidence is BLOCK`() {
        val r = QuoteValidator.validate(
            cleanDraft.copy(
                vehicleBrand = "",
                vehicleModel = "",
                vehicleYear = null,
                vehicleEngine = "",
                vehicleVin = null,
            ),
        )

        assertEquals(ValidationLevel.BLOCK, r.level)
        assertTrue(r.errors.any { it.code == "EXACT_REQUIRES_VEHICLE_EVIDENCE" })
    }

    @Test
    fun `EXACT with valid VIN and part identity is OK`() {
        val r = QuoteValidator.validate(
            cleanDraft.copy(
                vehicleBrand = "",
                vehicleModel = "",
                vehicleYear = null,
                vehicleEngine = "",
                vehicleVin = "KMHCN46C18U123456",
            ),
        )

        assertEquals(ValidationLevel.OK, r.level)
    }

    @Test
    fun `invalid VIN blocks EXACT even when the closed tuple is complete`() {
        val r = QuoteValidator.validate(
            cleanDraft.copy(vehicleVin = "KMHCN46C18O123456"),
        )

        assertEquals(ValidationLevel.BLOCK, r.level)
        assertTrue(r.errors.any { it.code == "INVALID_VIN" })
    }

    @Test
    fun `EXACT compat without notes is WARN`() {
        val r = QuoteValidator.validate(cleanDraft.copy(compatibilityNotes = ""))
        assertTrue(r.warnings.any { it.code == "EXACT_NO_NOTES" })
    }

    @Test
    fun `zero price is BLOCK`() {
        val r = QuoteValidator.validate(cleanDraft.copy(price = 0.0))
        assertEquals(ValidationLevel.BLOCK, r.level)
    }

    @Test
    fun `negative warranty is BLOCK`() {
        val r = QuoteValidator.validate(cleanDraft.copy(warrantyDays = -1))
        assertEquals(ValidationLevel.BLOCK, r.level)
    }

    @Test
    fun `safety-critical part emits install warning`() {
        val r = QuoteValidator.validate(cleanDraft.copy(partName = "Bomba de combustible", condition = PartCondition.NEW_OEM))
        assertTrue(r.warnings.any { it.code == "CRITICAL_SAFETY_PART" })
    }

    @Test
    fun `USED without warranty days is WARN`() {
        val r = QuoteValidator.validate(
            cleanDraft.copy(
                condition = PartCondition.USED,
                warrantyDays = 0,
                photoUrls = listOf("file://used.jpg"),
            )
        )
        assertTrue(r.warnings.any { it.code == "USED_NO_WARRANTY" })
    }

    @Test
    fun `IMPORT_REQUIRED with short ETA is WARN`() {
        val r = QuoteValidator.validate(
            cleanDraft.copy(availability = PartAvailability.IMPORT_REQUIRED, estimatedDeliveryHours = 48)
        )
        assertTrue(r.warnings.any { it.code == "IMPORT_SHORT_ETA" })
    }

    @Test
    fun `IMPORT_REQUIRED with long ETA is OK`() {
        val r = QuoteValidator.validate(
            cleanDraft.copy(availability = PartAvailability.IMPORT_REQUIRED, estimatedDeliveryHours = 24 * 14)
        )
        assertTrue(r.warnings.none { it.code == "IMPORT_SHORT_ETA" })
    }

    @Test
    fun `excessive price emits a warning`() {
        val r = QuoteValidator.validate(cleanDraft.copy(price = 5_000_000.0))
        assertTrue(r.warnings.any { it.code == "PRICE_UNUSUAL" })
    }
}
