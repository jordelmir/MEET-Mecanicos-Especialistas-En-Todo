package com.elysium369.meet.core.emergency

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import org.junit.Assert.*
import org.junit.Test

class EmergencyProtocolTest {

    private fun responder(
        id: String = "mech-1",
        domain: UniversalServiceDomain = UniversalServiceDomain.AUTO_MECHANICAL,
        lat: Double = 9.93,
        lon: Double = -84.08,
        verified: Boolean = true,
        trustScore: Double = 0.8,
        rate: Double = 25.0,
    ) = EmergencyResponder(
        userId = id, displayName = "Mecánico $id",
        domain = domain, latitude = lat, longitude = lon,
        verifiedIdentity = verified, trustScore = trustScore,
        baseRatePerHour = rate,
    )

    @Test
    fun `create emergency request`() {
        val engine = EmergencyEngine()
        val request = engine.createEmergency(
            "user-1", EmergencyTier.CRITICAL,
            "Carro muerto en carretera",
            UniversalServiceDomain.AUTO_MECHANICAL,
            9.93, -84.08,
        )
        assertEquals(EmergencyStatus.SEARCHING, request.status)
        assertEquals(EmergencyTier.CRITICAL, request.tier)
    }

    @Test
    fun `find nearest responders sorted by distance`() {
        val engine = EmergencyEngine()
        engine.registerResponder(responder("far", lat = 10.0, lon = -84.0))
        engine.registerResponder(responder("near", lat = 9.931, lon = -84.081))
        val request = engine.createEmergency(
            "user-1", EmergencyTier.URGENT, "Flat tire",
            UniversalServiceDomain.AUTO_MECHANICAL, 9.93, -84.08,
        )
        val matches = engine.findResponders(request)
        assertEquals(2, matches.size)
        assertTrue(matches[0].distanceKm < matches[1].distanceKm)
    }

    @Test
    fun `CRITICAL tier only shows verified responders`() {
        val engine = EmergencyEngine()
        engine.registerResponder(responder("verified", verified = true))
        engine.registerResponder(responder("unverified", verified = false))
        val request = engine.createEmergency(
            "user-1", EmergencyTier.CRITICAL, "Emergency",
            UniversalServiceDomain.AUTO_MECHANICAL, 9.93, -84.08,
        )
        val matches = engine.findResponders(request)
        assertEquals(1, matches.size)
        assertTrue(matches.first().isVerified)
    }

    @Test
    fun `emergency surcharge never exceeds 50 percent of base`() {
        val engine = EmergencyEngine()
        engine.registerResponder(responder(rate = 30.0))
        val request = engine.createEmergency(
            "user-1", EmergencyTier.CRITICAL, "Critical",
            UniversalServiceDomain.AUTO_MECHANICAL, 9.93, -84.08,
        )
        val match = engine.findResponders(request).first()
        assertTrue(match.priceEstimate.isFairPriced)
        assertTrue(match.priceEstimate.emergencySurcharge <= 30.0 * 0.5)
    }

    @Test
    fun `price disclosure is transparent`() {
        val engine = EmergencyEngine()
        engine.registerResponder(responder(rate = 20.0))
        val request = engine.createEmergency(
            "user-1", EmergencyTier.STANDARD, "Help",
            UniversalServiceDomain.AUTO_MECHANICAL, 9.93, -84.08,
        )
        val match = engine.findResponders(request).first()
        assertTrue(match.priceEstimate.disclosure.contains("Base"))
        assertTrue(match.priceEstimate.disclosure.contains("5% max"))
    }

    @Test
    fun `assign responder updates status`() {
        val engine = EmergencyEngine()
        engine.registerResponder(responder("m1"))
        val request = engine.createEmergency(
            "user-1", EmergencyTier.URGENT, "Stranded",
            UniversalServiceDomain.AUTO_MECHANICAL, 9.93, -84.08,
        )
        assertTrue(engine.assignResponder(request.requestId, "m1"))
    }

    @Test
    fun `SMS encoding fits in 160 chars`() {
        val engine = EmergencyEngine()
        val request = engine.createEmergency(
            "user-1", EmergencyTier.CRITICAL,
            "Mi carro se descompuso en la carretera a Guanacaste necesito ayuda urgente",
            UniversalServiceDomain.ROADSIDE, 10.4, -85.1,
        )
        val sms = engine.encodeForSms(request)
        assertTrue("SMS length ${sms.length} exceeds 160", sms.length <= 160)
    }

    @Test
    fun `SMS decode roundtrip`() {
        val engine = EmergencyEngine()
        val original = engine.createEmergency(
            "user-1", EmergencyTier.URGENT, "Flat tire on Route 27",
            UniversalServiceDomain.ROADSIDE, 9.95, -84.20,
        )
        val sms = engine.encodeForSms(original)
        val decoded = engine.decodeFromSms(sms, "user-1")
        assertNotNull(decoded)
        assertEquals(EmergencyChannel.SMS, decoded!!.channel)
    }

    @Test
    fun `haversine distance San Jose to Escazu is about 7 km`() {
        val engine = EmergencyEngine()
        // San José centro → Escazú
        val dist = engine.haversineDistance(9.9325, -84.0796, 9.9214, -84.1394)
        assertTrue("Distance $dist should be 5-10km", dist in 5.0..10.0)
    }

    @Test
    fun `all 4 emergency tiers exist`() {
        assertEquals(4, EmergencyTier.entries.size)
        assertEquals(15, EmergencyTier.CRITICAL.maxResponseMinutes)
    }
}
