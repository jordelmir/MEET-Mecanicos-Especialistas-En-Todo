package com.elysium369.meet.core.access

/**
 * STUB FILE — Tipos de comisión para transacciones marketplace (mecánicos, grúas, repuestos).
 * CommissionEngine.decide() aplica reglas de pricing por tipo y volumen.
 * Implementación real vive en core/billing/. Este stub sólo satisface el compile.
 */

enum class TransactionKind {
    REPAIR_SERVICE,
    TOW_SERVICE,
    PART_SALE,
    INSPECTION,
    CONSULTING,
    OTHER
}

data class CommissionDecision(
    val transactionKind: TransactionKind,
    val grossCents: Long,
    val commissionCents: Long,
    val netCents: Long,
    val platformFeeBps: Int = 0,
    val notes: String = "",
    val rateBps: Int = platformFeeBps,
    val policyCode: String = "DEFAULT",
    val platformCommissionCents: Long = commissionCents,
    val providerPayoutCents: Long = netCents
)

object CommissionEngine {
    /** Decide comisión según tipo de transacción y volumen. Stub: 10% flat. */
    fun decide(
        transactionKind: TransactionKind,
        grossCents: Long,
        overrides: Map<String, Any> = emptyMap()
    ): CommissionDecision {
        val bps = 1000 // 10% en basis points
        val commissionCents = (grossCents * bps) / 10_000L
        return CommissionDecision(
            transactionKind = transactionKind,
            grossCents = grossCents,
            commissionCents = commissionCents,
            netCents = grossCents - commissionCents,
            platformFeeBps = bps
        )
    }
}