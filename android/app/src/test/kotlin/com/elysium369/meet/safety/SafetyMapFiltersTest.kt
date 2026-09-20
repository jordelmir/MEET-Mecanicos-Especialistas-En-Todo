package com.elysium369.meet.safety

import com.elysium369.meet.safety.data.local.SafetyPublicPointEntity
import com.elysium369.meet.safety.ui.map.*
import org.junit.Assert.*
import org.junit.Test

class SafetyMapFiltersTest {
    private val now = 1_800_000_000_000L
    private fun point() = SafetyPublicPointEntity(
        publicPointId = "public-id", category = "THREAT", displayLatitude = 9.9,
        displayLongitude = -84.0, geoDisclosure = "APPROXIMATE_1000M", locationAccuracyMeters = 1000,
        label = "Zona pública", claimState = "DOCUMENTED", independentSourceCount = 1,
        civilSourceCount = 1, journalisticSourceCount = 0, publicRecordSourceCount = 0,
        documentarySourceCount = 0, institutionalSourceCount = 0, countryCode = "CR",
        admin1Code = null, admin2Code = null, publicH3Cell = null, firstDocumentedAt = null,
        lastReviewedAt = now, publishedAt = now, serverVersion = 1, syncedAt = now,
    )
    @Test fun suppressedAndUnknownDisclosureNeverReachPublicMap() {
        val points = listOf(point(), point().copy(geoDisclosure = "SUPPRESSED"), point().copy(geoDisclosure = "NEW_UNKNOWN"))
        assertEquals(listOf(point()), points.filterFor(SafetyMapLayer.ALL, SafetyTimeRange.ALL, now))
    }
    @Test fun rejectsUnversionedAndMalformedCoordinates() {
        val points = listOf(point().copy(serverVersion = 0), point().copy(displayLatitude = Double.NaN), point().copy(displayLongitude = 181.0))
        assertTrue(points.filterFor(SafetyMapLayer.ALL, SafetyTimeRange.ALL, now).isEmpty())
    }
    @Test fun filtersCategoryAndExactTimeBoundaryWithoutTreatingSyncAsPublication() {
        val cutoff = now - 7 * 86_400_000L
        val matching = point().copy(publishedAt = cutoff)
        val stale = point().copy(publishedAt = cutoff - 1, syncedAt = now)
        assertEquals(listOf(matching), listOf(matching, stale, point().copy(category = "HOMICIDE")).filterFor(SafetyMapLayer.THREAT, SafetyTimeRange.DAYS_7, now))
    }
    @Test fun documentedDateCanQualifyButMissingDateDoesNotInventRecency() {
        val old = point().copy(publishedAt = 1)
        val recent = old.copy(firstDocumentedAt = now)
        assertEquals(listOf(recent), listOf(old, recent).filterFor(SafetyMapLayer.ALL, SafetyTimeRange.DAYS_7, now))
    }
}
