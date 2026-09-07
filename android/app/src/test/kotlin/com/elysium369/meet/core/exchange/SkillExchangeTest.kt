package com.elysium369.meet.core.exchange

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import org.junit.Assert.*
import org.junit.Test

class SkillExchangeTest {

    private fun offer(
        id: String = "offer-1",
        offererId: String = "alice",
        offeringDomain: UniversalServiceDomain = UniversalServiceDomain.TUTORING,
        seekingDomain: UniversalServiceDomain = UniversalServiceDomain.PLUMBING,
    ) = ExchangeOffer(
        offerId = id, offererId = offererId, offererName = "Alice",
        offering = SkillOffer(offeringDomain, "Teach math", 2.0),
        seeking = SkillOffer(seekingDomain, "Fix my sink", 1.0),
    )

    @Test
    fun `1 hour of any work equals 1 time credit`() {
        val credit = TimeCredit.ofHours(1.0)
        assertEquals(1.0, credit.amount, 0.001)
    }

    @Test
    fun `time credits cannot be negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            TimeCredit(-1.0)
        }
    }

    @Test
    fun `create and find matching offers`() {
        val engine = SkillExchangeEngine()
        engine.createOffer(offer())
        // Bob offers plumbing and seeks tutoring — perfect match!
        val matches = engine.findMatches(
            seekingDomain = UniversalServiceDomain.TUTORING,
            offeringDomain = UniversalServiceDomain.PLUMBING,
        )
        assertEquals(1, matches.size)
    }

    @Test
    fun `accept match creates bilateral agreement`() {
        val engine = SkillExchangeEngine()
        val created = engine.createOffer(offer())
        assertTrue(engine.acceptMatch(created.offerId, "bob"))
    }

    @Test
    fun `cannot match with yourself`() {
        val engine = SkillExchangeEngine()
        val created = engine.createOffer(offer(offererId = "alice"))
        assertFalse(engine.acceptMatch(created.offerId, "alice"))
    }

    @Test
    fun `complete exchange transfers time credits to both parties`() {
        val engine = SkillExchangeEngine()
        val created = engine.createOffer(offer())
        engine.acceptMatch(created.offerId, "bob")
        assertTrue(engine.completeExchange(created.offerId, 2.0, 1.0))

        val aliceAccount = engine.getAccount("alice")!!
        assertEquals(2.0, aliceAccount.balance.amount, 0.001)

        val bobAccount = engine.getAccount("bob")!!
        assertEquals(1.0, bobAccount.balance.amount, 0.001)
    }

    @Test
    fun `time credit arithmetic works`() {
        val a = TimeCredit.ofHours(3.0)
        val b = TimeCredit.ofHours(1.5)
        assertEquals(4.5, (a + b).amount, 0.001)
        assertEquals(1.5, (a - b).amount, 0.001)
    }

    @Test
    fun `cannot subtract more credits than available`() {
        assertThrows(IllegalArgumentException::class.java) {
            TimeCredit.ofHours(1.0) - TimeCredit.ofHours(2.0)
        }
    }

    @Test
    fun `minutes conversion works`() {
        val credit = TimeCredit.ofMinutes(90)
        assertEquals(1.5, credit.amount, 0.001)
    }

    @Test
    fun `total exchanges count only completed`() {
        val engine = SkillExchangeEngine()
        engine.createOffer(offer("o1"))
        engine.createOffer(offer("o2", offererId = "charlie"))
        engine.acceptMatch("o1", "bob")
        engine.completeExchange("o1", 1.0, 1.0)
        assertEquals(1, engine.totalExchanges)
    }
}
