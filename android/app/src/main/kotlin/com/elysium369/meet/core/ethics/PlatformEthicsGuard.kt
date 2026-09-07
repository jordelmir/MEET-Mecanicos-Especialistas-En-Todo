package com.elysium369.meet.core.ethics

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  P L A T F O R M   E T H I C S   G U A R D
 *  ─────────────────────────────────────────────
 *  Constitutional protection against platform exploitation.
 *
 *  "If we eliminate intermediaries but ELYSIUM becomes one, we fail."
 *
 *  CONSTITUTIONAL LAW (decided by Jor, 2026-09-07):
 *  ┌─────────────────────────────────────────────────────┐
 *  │  MAXIMUM PLATFORM FEE: 5%                          │
 *  │  This is a CONSTITUTIONAL LIMIT.                   │
 *  │  It CANNOT be changed by code, only by the founder.│
 *  └─────────────────────────────────────────────────────┘
 *
 *  Rights of every human on ELYSIUM:
 *  1. RIGHT TO LEAVE — export all your data and reputation
 *  2. RIGHT TO TRANSPARENCY — see every fee, every calculation
 *  3. RIGHT TO FAIR PRICING — platform never inflates prices
 *  4. RIGHT TO PRIVACY — your data is yours
 *  5. RIGHT TO DIGNITY — never degraded, always respected
 * ══════════════════════════════════════════════════════════════════════
 */

object PlatformEthicsGuard {

    // ═══ CONSTITUTIONAL LIMITS ═══

    /** Maximum platform fee. CONSTITUTIONAL. Cannot be overridden. */
    const val MAX_PLATFORM_FEE_PERCENT = 5.0

    /** Maximum fee in absolute terms for any single transaction */
    const val MAX_ABSOLUTE_FEE_USD = 50.0

    /** Minimum transaction value to charge ANY fee */
    const val MIN_TRANSACTION_FOR_FEE_USD = 5.0

    /** Fee-free threshold for micro-transactions */
    const val MICRO_TRANSACTION_THRESHOLD_USD = 10.0

    // ═══ Fee Calculation ═══

    /**
     * Calculates the platform fee for a transaction.
     * INVARIANT: Never exceeds MAX_PLATFORM_FEE_PERCENT (5%).
     * INVARIANT: Micro-transactions (< $10) are FREE.
     * INVARIANT: Fee is always transparently disclosed BEFORE commitment.
     */
    fun calculateFee(transactionAmountUsd: Double): FeeCalculation {
        // Micro-transactions are free
        if (transactionAmountUsd < MICRO_TRANSACTION_THRESHOLD_USD) {
            return FeeCalculation(
                transactionAmount = transactionAmountUsd,
                feePercent = 0.0,
                feeAmount = 0.0,
                providerReceives = transactionAmountUsd,
                isMicroTransaction = true,
                disclosure = "Micro-transacción: sin comisión de plataforma.",
            )
        }

        // Progressive fee: lower for higher amounts
        val feePercent = when {
            transactionAmountUsd < 50.0 -> 5.0          // 5% for small
            transactionAmountUsd < 200.0 -> 4.0         // 4% for medium
            transactionAmountUsd < 500.0 -> 3.0         // 3% for larger
            transactionAmountUsd < 1000.0 -> 2.5        // 2.5% for large
            else -> 2.0                                  // 2% for very large
        }

        val calculatedFee = transactionAmountUsd * (feePercent / 100.0)

        // Cap at absolute maximum
        val actualFee = calculatedFee.coerceAtMost(MAX_ABSOLUTE_FEE_USD)
        val actualPercent = (actualFee / transactionAmountUsd * 100.0)
            .coerceAtMost(MAX_PLATFORM_FEE_PERCENT)

        return FeeCalculation(
            transactionAmount = transactionAmountUsd,
            feePercent = actualPercent,
            feeAmount = actualFee,
            providerReceives = transactionAmountUsd - actualFee,
            isMicroTransaction = false,
            disclosure = buildDisclosure(transactionAmountUsd, actualFee, actualPercent),
        )
    }

    private fun buildDisclosure(amount: Double, fee: Double, percent: Double): String {
        return "Monto: \$${String.format("%.2f", amount)} | " +
            "Comisión: \$${String.format("%.2f", fee)} (${String.format("%.1f", percent)}%) | " +
            "Usted recibe: \$${String.format("%.2f", amount - fee)} | " +
            "Límite constitucional: ${MAX_PLATFORM_FEE_PERCENT.toInt()}% máximo"
    }

    // ═══ Data Portability ═══

    /**
     * RIGHT TO LEAVE: generates a complete export of a user's data.
     * The user can take their reputation, history, and skills to any platform.
     */
    fun generateDataExport(userId: String): DataExportManifest {
        return DataExportManifest(
            userId = userId,
            exportedAtEpochMs = System.currentTimeMillis(),
            sections = listOf(
                ExportSection("economic_passport", "Pasaporte Económico completo"),
                ExportSection("trust_graph", "Red de confianza (quién te respalda)"),
                ExportSection("skill_credentials", "Credenciales y habilidades verificadas"),
                ExportSection("work_history", "Historial de trabajos completados"),
                ExportSection("reviews_received", "Evaluaciones recibidas"),
                ExportSection("reviews_given", "Evaluaciones que diste"),
                ExportSection("learning_progress", "Progreso de aprendizaje"),
                ExportSection("memory_personal", "Memoria personal (no compartida)"),
            ),
            format = "JSON + SHA-256 integrity hash",
            portabilityNotice = "Estos datos son TUYOS. Puedes llevarlos a cualquier " +
                "plataforma. ELYSIUM no retiene datos después de la exportación si " +
                "decides eliminar tu cuenta.",
        )
    }

    // ═══ Ethics Audit ═══

    /**
     * Audits a transaction for ethical compliance.
     * Returns violations if any constitutional limits are breached.
     */
    fun auditTransaction(
        transactionAmountUsd: Double,
        proposedFeePercent: Double,
    ): EthicsAuditResult {
        val violations = mutableListOf<EthicsViolation>()

        if (proposedFeePercent > MAX_PLATFORM_FEE_PERCENT) {
            violations.add(EthicsViolation(
                code = "FEE_EXCEEDS_CONSTITUTIONAL_LIMIT",
                description = "Fee ${proposedFeePercent}% exceeds constitutional maximum of ${MAX_PLATFORM_FEE_PERCENT}%",
                severity = EthicsViolationSeverity.CONSTITUTIONAL,
                remedy = "Reduce fee to ${MAX_PLATFORM_FEE_PERCENT}% or below",
            ))
        }

        val absoluteFee = transactionAmountUsd * (proposedFeePercent / 100.0)
        if (absoluteFee > MAX_ABSOLUTE_FEE_USD) {
            violations.add(EthicsViolation(
                code = "ABSOLUTE_FEE_CAP_EXCEEDED",
                description = "Absolute fee \$${String.format("%.2f", absoluteFee)} exceeds cap of \$${MAX_ABSOLUTE_FEE_USD}",
                severity = EthicsViolationSeverity.HIGH,
                remedy = "Cap fee at \$${MAX_ABSOLUTE_FEE_USD}",
            ))
        }

        if (transactionAmountUsd < MICRO_TRANSACTION_THRESHOLD_USD && proposedFeePercent > 0) {
            violations.add(EthicsViolation(
                code = "MICRO_TRANSACTION_SHOULD_BE_FREE",
                description = "Transactions under \$${MICRO_TRANSACTION_THRESHOLD_USD} must be fee-free",
                severity = EthicsViolationSeverity.MEDIUM,
                remedy = "Set fee to 0% for micro-transactions",
            ))
        }

        return EthicsAuditResult(
            isCompliant = violations.isEmpty(),
            violations = violations,
        )
    }
}

// ─── Data Types ───

@Serializable
data class FeeCalculation(
    val transactionAmount: Double,
    val feePercent: Double,
    val feeAmount: Double,
    val providerReceives: Double,
    val isMicroTransaction: Boolean,
    val disclosure: String,
)

@Serializable
data class DataExportManifest(
    val userId: String,
    val exportedAtEpochMs: Long,
    val sections: List<ExportSection>,
    val format: String,
    val portabilityNotice: String,
)

@Serializable
data class ExportSection(
    val key: String,
    val description: String,
)

@Serializable
data class EthicsAuditResult(
    val isCompliant: Boolean,
    val violations: List<EthicsViolation>,
)

@Serializable
data class EthicsViolation(
    val code: String,
    val description: String,
    val severity: EthicsViolationSeverity,
    val remedy: String,
)

enum class EthicsViolationSeverity {
    CONSTITUTIONAL,  // Platform CANNOT proceed
    HIGH,            // Must fix before processing
    MEDIUM,          // Warning, can proceed with disclosure
    LOW,             // Informational
}
