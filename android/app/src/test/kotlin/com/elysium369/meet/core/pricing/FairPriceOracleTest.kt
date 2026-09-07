package com.elysium369.meet.core.pricing

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import org.junit.Assert.*
import org.junit.Test

class FairPriceOracleTest {

    private fun dataPoint(
        price: Double,
        category: String = "brake_repair",
        providerId: String = "p-${(Math.random()*1000).toInt()}",
    ) = PriceDataPoint(
        providerId = providerId,
        domain = UniversalServiceDomain.AUTO_MECHANICAL,
        serviceDescription = "Brake repair",
        serviceCategory = category,
        priceUsd = price,
        locationRegion = "San José",
    )

    private fun oracleWith10Points(): FairPriceOracle {
        val oracle = FairPriceOracle()
        listOf(100.0, 120.0, 130.0, 150.0, 160.0, 170.0, 180.0, 200.0, 220.0, 250.0).forEach {
            oracle.recordPrice(dataPoint(it))
        }
        return oracle
    }

    @Test
    fun `quote below median is EXCELLENT`() {
        val oracle = oracleWith10Points() // median ~165
        val analysis = oracle.analyzeQuote(100.0, "brake_repair", UniversalServiceDomain.AUTO_MECHANICAL)
        assertEquals(PriceFairness.EXCELLENT, analysis.fairness)
    }

    @Test
    fun `quote at median is EXCELLENT`() {
        val oracle = oracleWith10Points()
        val analysis = oracle.analyzeQuote(165.0, "brake_repair", UniversalServiceDomain.AUTO_MECHANICAL)
        assertEquals(PriceFairness.EXCELLENT, analysis.fairness)
    }

    @Test
    fun `quote 40 percent above median is FAIR`() {
        val oracle = oracleWith10Points() // median ~170
        val analysis = oracle.analyzeQuote(230.0, "brake_repair", UniversalServiceDomain.AUTO_MECHANICAL)
        assertEquals(PriceFairness.FAIR, analysis.fairness)
    }

    @Test
    fun `quote 200 percent above median is OUTLIER`() {
        val oracle = oracleWith10Points()
        val analysis = oracle.analyzeQuote(800.0, "brake_repair", UniversalServiceDomain.AUTO_MECHANICAL)
        assertEquals(PriceFairness.OUTLIER, analysis.fairness)
    }

    @Test
    fun `insufficient data when less than 2 points`() {
        val oracle = FairPriceOracle()
        oracle.recordPrice(dataPoint(100.0))
        val analysis = oracle.analyzeQuote(100.0, "brake_repair", UniversalServiceDomain.AUTO_MECHANICAL)
        assertEquals(PriceFairness.INSUFFICIENT_DATA, analysis.fairness)
        assertTrue(analysis.disclaimer.contains("Dato no capturado"))
    }

    @Test
    fun `disclosure is transparent`() {
        val oracle = oracleWith10Points()
        val analysis = oracle.analyzeQuote(150.0, "brake_repair", UniversalServiceDomain.AUTO_MECHANICAL)
        assertTrue(analysis.disclosure.contains("cotización"))
        assertTrue(analysis.disclosure.contains("Mediana"))
    }

    @Test
    fun `deviation percent calculated correctly`() {
        val analysis = FairPriceAnalysis(
            quotedPriceUsd = 300.0, fairness = PriceFairness.HIGH,
            networkAverageUsd = 200.0, networkMedianUsd = 200.0,
            networkMinUsd = 100.0, networkMaxUsd = 400.0,
            dataPoints = 10, percentile = 80,
            suggestedRangeMin = 150.0, suggestedRangeMax = 250.0,
            disclosure = "", disclaimer = "",
        )
        assertEquals(50.0, analysis.deviationPercent, 0.001) // 50% above average
    }

    @Test
    fun `anomaly detection finds outliers`() {
        val oracle = oracleWith10Points()
        oracle.recordPrice(dataPoint(900.0)) // clear outlier
        val anomalies = oracle.detectAnomalies(UniversalServiceDomain.AUTO_MECHANICAL, "brake_repair")
        assertTrue(anomalies.isNotEmpty())
        assertTrue(anomalies.first().note.contains("NO significa fraude"))
    }

    @Test
    fun `anomaly note is honest — not accusatory`() {
        val anomaly = PriceAnomaly("p1", 500.0, 200.0, 150.0, "brakes",
            "Cotización 150% por encima. Esto NO significa fraude.")
        assertTrue(anomaly.note.contains("NO significa fraude"))
    }

    @Test
    fun `all 5 fairness levels exist`() {
        assertEquals(5, PriceFairness.entries.size)
    }
}
