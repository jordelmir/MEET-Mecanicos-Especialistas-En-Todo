package com.elysium369.meet.core.exchange

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  S K I L L   E X C H A N G E   /   T I M E   B A N K
 *  ─────────────────────────────────────────────────────
 *  "I'll teach you math if you fix my sink."
 *
 *  Not everything needs money. Communities run on reciprocity.
 *  This is a time banking system where work has value beyond currency.
 *
 *  1 hour of ANY honest work = 1 TimeCredit.
 *  A plumber's hour equals a tutor's hour equals a cook's hour.
 *  This is the dignity equation: all honest work has equal value.
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Time Credits ───

@Serializable
data class TimeCredit(
    val amount: Double,
) {
    init { require(amount >= 0) { "TimeCredits cannot be negative" } }

    operator fun plus(other: TimeCredit) = TimeCredit(amount + other.amount)
    operator fun minus(other: TimeCredit): TimeCredit {
        require(amount >= other.amount) { "Insufficient TimeCredits" }
        return TimeCredit(amount - other.amount)
    }

    companion object {
        val ZERO = TimeCredit(0.0)
        fun ofHours(hours: Double) = TimeCredit(hours)
        fun ofMinutes(minutes: Int) = TimeCredit(minutes / 60.0)
    }
}

// ─── Exchange Offer ───

enum class ExchangeStatus {
    OPEN,           // Offer is available
    MATCHED,        // Someone accepted
    IN_PROGRESS,    // Exchange is happening
    COMPLETED,      // Both parties satisfied
    DISPUTED,       // One party has a complaint
    CANCELLED,      // Offer withdrawn
    EXPIRED,        // Offer timed out
}

@Serializable
data class ExchangeOffer(
    val offerId: String,
    val offererId: String,
    val offererName: String,
    /** What I can DO for you */
    val offering: SkillOffer,
    /** What I NEED from you */
    val seeking: SkillOffer,
    val status: ExchangeStatus = ExchangeStatus.OPEN,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000),
    val matchedWithUserId: String? = null,
) {
    val isActive: Boolean
        get() = status == ExchangeStatus.OPEN &&
            System.currentTimeMillis() < expiresAtEpochMs
}

@Serializable
data class SkillOffer(
    val domain: UniversalServiceDomain,
    val description: String,
    val estimatedHours: Double,
    val location: String = "",
)

// ─── Time Bank Account ───

@Serializable
data class TimeBankAccount(
    val userId: String,
    val balance: TimeCredit = TimeCredit.ZERO,
    val totalEarned: TimeCredit = TimeCredit.ZERO,
    val totalSpent: TimeCredit = TimeCredit.ZERO,
    val transactions: List<TimeBankTransaction> = emptyList(),
) {
    val transactionCount: Int get() = transactions.size
}

@Serializable
data class TimeBankTransaction(
    val transactionId: String,
    val fromUserId: String,
    val toUserId: String,
    val amount: TimeCredit,
    val description: String,
    val domain: UniversalServiceDomain,
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

// ─── Skill Exchange Engine ───

class SkillExchangeEngine {

    private val offers = mutableListOf<ExchangeOffer>()
    private val accounts = mutableMapOf<String, TimeBankAccount>()

    /**
     * Creates a new skill exchange offer.
     * "I can teach math (2 hours) and I need plumbing help (1 hour)"
     */
    fun createOffer(offer: ExchangeOffer): ExchangeOffer {
        offers.add(offer)
        // Ensure account exists
        accounts.getOrPut(offer.offererId) {
            TimeBankAccount(userId = offer.offererId)
        }
        return offer
    }

    /**
     * Finds matching offers for a user's needs.
     * Matching: someone OFFERING what I SEEK, and SEEKING what I OFFER.
     */
    fun findMatches(
        seekingDomain: UniversalServiceDomain,
        offeringDomain: UniversalServiceDomain,
    ): List<ExchangeOffer> {
        return offers.filter { offer ->
            offer.isActive &&
                offer.offering.domain == seekingDomain &&
                offer.seeking.domain == offeringDomain
        }
    }

    /**
     * Accepts a match — both parties agree to exchange.
     */
    fun acceptMatch(offerId: String, acceptorId: String): Boolean {
        val idx = offers.indexOfFirst { it.offerId == offerId }
        if (idx < 0) return false

        val offer = offers[idx]
        if (!offer.isActive) return false
        if (offer.offererId == acceptorId) return false // can't match with yourself

        offers[idx] = offer.copy(
            status = ExchangeStatus.MATCHED,
            matchedWithUserId = acceptorId,
        )

        accounts.getOrPut(acceptorId) {
            TimeBankAccount(userId = acceptorId)
        }

        return true
    }

    /**
     * Completes an exchange — both parties fulfilled their commitment.
     * Time credits are transferred based on actual hours worked.
     */
    fun completeExchange(
        offerId: String,
        actualHoursOfferer: Double,
        actualHoursAcceptor: Double,
    ): Boolean {
        val idx = offers.indexOfFirst { it.offerId == offerId }
        if (idx < 0) return false

        val offer = offers[idx]
        if (offer.status != ExchangeStatus.MATCHED) return false
        val acceptorId = offer.matchedWithUserId ?: return false

        // Record time credits
        val offererCredit = TimeCredit.ofHours(actualHoursOfferer)
        val acceptorCredit = TimeCredit.ofHours(actualHoursAcceptor)

        // Offerer earns credits for work done
        val offererAccount = accounts[offer.offererId]!!
        accounts[offer.offererId] = offererAccount.copy(
            balance = offererAccount.balance + offererCredit,
            totalEarned = offererAccount.totalEarned + offererCredit,
            transactions = offererAccount.transactions + TimeBankTransaction(
                transactionId = "tx-${System.currentTimeMillis()}-off",
                fromUserId = acceptorId,
                toUserId = offer.offererId,
                amount = offererCredit,
                description = offer.offering.description,
                domain = offer.offering.domain,
            ),
        )

        // Acceptor earns credits for work done
        val acceptorAccount = accounts[acceptorId]!!
        accounts[acceptorId] = acceptorAccount.copy(
            balance = acceptorAccount.balance + acceptorCredit,
            totalEarned = acceptorAccount.totalEarned + acceptorCredit,
            transactions = acceptorAccount.transactions + TimeBankTransaction(
                transactionId = "tx-${System.currentTimeMillis()}-acc",
                fromUserId = offer.offererId,
                toUserId = acceptorId,
                amount = acceptorCredit,
                description = offer.seeking.description,
                domain = offer.seeking.domain,
            ),
        )

        offers[idx] = offer.copy(status = ExchangeStatus.COMPLETED)
        return true
    }

    fun getAccount(userId: String): TimeBankAccount? = accounts[userId]

    val activeOffers: List<ExchangeOffer>
        get() = offers.filter { it.isActive }

    val totalExchanges: Int
        get() = offers.count { it.status == ExchangeStatus.COMPLETED }
}
