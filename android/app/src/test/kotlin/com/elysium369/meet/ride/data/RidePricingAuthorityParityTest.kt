package com.elysium369.meet.ride.data

import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.ride.domain.RideFareEngine
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RidePricingAuthorityParityTest {
    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    private fun fixtureLong(json: String, key: String): Long =
        Regex("\\\"$key\\\"\\s*:\\s*(\\d+)")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toLong()
            ?: error("Missing numeric pricing fixture key $key")

    @Test
    fun `CRC pricing is identical across Kotlin server policy and money semantics`() {
        val fixture = projectFile("tests/parity/fixtures/crc-ride-pricing-v2.json").readText()
        val policySql = projectFile(
            "supabase/migrations/20260922090000_crc_ride_pricing_authority_parity.sql",
        ).readText()
        val rideCreateSql = projectFile(
            "supabase/migrations/20260918020000_ride_create_request_preferences_support.sql",
        ).readText()

        val version = fixtureLong(fixture, "rateCardVersion")
        val base = fixtureLong(fixture, "baseFareMinor")
        val distance = fixtureLong(fixture, "distanceRateMinorPerKm")
        val time = fixtureLong(fixture, "timeRateMinorPerMinute")
        val minimum = fixtureLong(fixture, "minimumFareMinor")
        val booking = fixtureLong(fixture, "bookingFeeMinor")
        val commission = fixtureLong(fixture, "platformCommissionBasisPoints")

        assertEquals(fixtureLong(fixture, "currencyDecimalPlaces").toInt(), CurrencyCode.CRC.decimalPlaces)
        assertEquals(distance, RideFareEngine.CRC_DISTANCE_RATE_MINOR_PER_KM)
        assertEquals(time, RideFareEngine.CRC_TIME_RATE_MINOR_PER_MINUTE)
        assertTrue(rideCreateSql.contains("p_distance_rate_minor_per_km <> $distance"))
        assertTrue(rideCreateSql.contains("p_time_rate_minor_per_minute <> $time"))

        val marker = "PRICING_PARITY_RATE_CARD=CR_GAM|STD_RIDE|$version|CRC|$base|$distance|$time|$minimum|$booking|$commission"
        assertTrue(policySql.contains(marker))
        assertTrue(policySql.contains("active = FALSE"))
        val normalizedPolicySql = policySql.replace(Regex("\\s+"), " ")
        assertTrue(
            normalizedPolicySql.contains(
                "VALUES ( 'CR_GAM', 'STD_RIDE', 2, 'CRC', 0, 300, 1000, 60, 60, 0, 0, 0, 0, 10000, TRUE )",
            ),
        )

        val canonical = "currency=CRC;decimalPlaces=${CurrencyCode.CRC.decimalPlaces};base=$base;distancePerKm=$distance;timePerMinute=$time;commissionBps=$commission;rateCardVersion=$version"
        val output = buildString {
            appendLine("[OK] CR_GAM STD_RIDE canonical pricing parity v2")
            appendLine("  canonical: $canonical")
        }
        val outputFile = File("build/reports/parity/pricing-crc.txt")
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(output)
    }
}
