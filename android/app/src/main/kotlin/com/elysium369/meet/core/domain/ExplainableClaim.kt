package com.elysium369.meet.core.domain

/**
 * MEET Vehicle Life OS — Proof-Carrying Claim Interface.
 * Answers the fundamental user question: "¿POR QUÉ MEET DICE ESTO?".
 * Every health score deduction, repair recommendation, maintenance prediction, and fitment assertion
 * must implement this contract.
 */
interface ExplainableClaim {
    val claimId: String
    val claimTitle: String
    val claimStatement: String
    val nature: ClaimNature
    val authority: SourceAuthority
    val confidencePercent: Int? // 0..100, or null if strictly authoritative/observed
    val evidenceRefs: List<EntityRef.EvidenceRef>
    val derivationSummary: String?
    val timestampUtc: Long // Unix epoch ms

    fun whyMeetSaysThis(): String {
        val confidenceText = confidencePercent?.let { " [Confianza: $it%]" } ?: ""
        val derivationText = derivationSummary?.let { "\n• Derivación: $it" } ?: ""
        val evidenceCount = evidenceRefs.size
        return "• Afirmación: $claimStatement ($claimTitle)\n" +
               "• Naturaleza: ${nature.title} (${nature.badgeGlyph})$confidenceText\n" +
               "• Fuente Autoritativa: ${authority.displayName}\n" +
               "• Evidencias vinculadas: $evidenceCount elemento(s)$derivationText"
    }
}

data class GenericExplainableClaim(
    override val claimId: String,
    override val claimTitle: String,
    override val claimStatement: String,
    override val nature: ClaimNature,
    override val authority: SourceAuthority,
    override val confidencePercent: Int? = null,
    override val evidenceRefs: List<EntityRef.EvidenceRef> = emptyList(),
    override val derivationSummary: String? = null,
    override val timestampUtc: Long = System.currentTimeMillis()
) : ExplainableClaim
