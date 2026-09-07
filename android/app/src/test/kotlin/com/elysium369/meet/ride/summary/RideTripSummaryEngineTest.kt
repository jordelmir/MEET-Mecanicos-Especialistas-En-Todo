package com.elysium369.meet.ride.summary

import com.elysium369.meet.ride.domain.RideFareMode
import org.junit.Assert.*
import org.junit.Test

class RideTripSummaryEngineTest {

    private fun engineWithReceipt(): Pair<RideTripSummaryEngine, TripReceipt> {
        val e = RideTripSummaryEngine()
        val r = e.generateReceipt(
            rideId = "ride-1", passengerId = "p1", driverId = "d1",
            driverName = "Carlos", pickupName = "San José Centro",
            dropoffName = "Aeropuerto SJO", distanceKm = 18.5,
            durationMinutes = 25, baseFare = 800, distanceFare = 2400,
            timeFare = 1300, fareMode = RideFareMode.METERED_TIME_DISTANCE,
            paymentMethod = "Efectivo",
        )
        return e to r
    }

    @Test
    fun `generate receipt with fare breakdown`() {
        val (_, r) = engineWithReceipt()
        assertEquals(4500L, r.totalFare)  // 800+2400+1300
        assertEquals(3, r.fareBreakdown.size)
    }

    @Test
    fun `fare breakdown includes surcharge and discount`() {
        val e = RideTripSummaryEngine()
        val r = e.generateReceipt(
            "r1", "p1", "d1", "Driver", "A", "B", 10.0, 15,
            500, 1000, 500, RideFareMode.METERED_TIME_DISTANCE, "Cash",
            surcharge = 300, discount = 200,
        )
        assertEquals(2100L, r.totalFare) // 500+1000+500+300-200
        assertEquals(5, r.fareBreakdown.size) // base+dist+time+surcharge+discount
    }

    @Test
    fun `add tip updates total`() {
        val (e, r) = engineWithReceipt()
        val tipped = e.addTip(r.receiptId, 1000)!!
        assertEquals(5500L, tipped.totalFare) // 4500 + 1000
        assertTrue(tipped.hasTip)
    }

    @Test
    fun `calculate percentage tip`() {
        val (e, r) = engineWithReceipt()
        val tip15 = e.calculatePercentageTip(r.receiptId, 15)
        assertEquals(675L, tip15) // 4500 * 15%
    }

    @Test
    fun `split fare evenly`() {
        val (e, r) = engineWithReceipt()
        val split = e.splitFare(r.receiptId, listOf("u2" to "María", "u3" to "Pedro"))!!
        assertTrue(split.isSplit)
        assertEquals(2, split.splitWith.size)
        assertEquals(1500L, split.splitWith[0].shareAmount) // 4500/3
    }

    @Test
    fun `rate driver with compliments`() {
        val (e, r) = engineWithReceipt()
        assertTrue(e.rateDriver(r.receiptId, 5, listOf("Excelente servicio", "Vehículo limpio")))
        val rated = e.getReceipt(r.receiptId)!!
        assertEquals(5, rated.driverRatingGiven)
        assertEquals(2, rated.compliments.size)
    }

    @Test
    fun `report issue`() {
        val (e, r) = engineWithReceipt()
        val issue = e.reportIssue(r.receiptId, IssueCategory.OVERCHARGED, "Cobró doble")!!
        assertEquals(IssueCategory.OVERCHARGED, issue.category)
        assertTrue(e.getReceipt(r.receiptId)!!.hasIssue)
    }

    @Test
    fun `search trip history`() {
        val (e, _) = engineWithReceipt()
        val results = e.searchTrips("p1", "aeropuerto")
        assertEquals(1, results.size)
    }

    @Test
    fun `compute trip statistics`() {
        val (e, _) = engineWithReceipt()
        val stats = e.computeStatistics("p1")
        assertEquals(1, stats.totalTrips)
        assertEquals(4500L, stats.totalSpent)
        assertEquals(18.5, stats.averageDistance, 0.1)
    }

    @Test
    fun `shareable receipt text`() {
        val (e, r) = engineWithReceipt()
        val text = e.generateShareText(r.receiptId)
        assertTrue(text.contains("ELYSIUM"))
        assertTrue(text.contains("Aeropuerto SJO"))
        assertTrue(text.contains("Carlos"))
    }

    @Test
    fun `tip presets include percentage options`() {
        val e = RideTripSummaryEngine()
        assertTrue(e.defaultTipPresets.any { it.isPercentage })
        assertTrue(e.defaultTipPresets.any { it.amount == 0L && !it.isPercentage }) // "Sin propina"
    }
}
