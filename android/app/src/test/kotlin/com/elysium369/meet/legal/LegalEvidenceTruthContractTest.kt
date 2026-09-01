package com.elysium369.meet.legal

import com.elysium369.meet.legal.domain.LegalJournalSource
import com.elysium369.meet.legal.domain.LegalJournalTruthState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalEvidenceTruthContractTest {
    @Test
    fun `declared observed documented and externally verified remain distinct`() {
        val states = LegalJournalTruthState.entries.toSet()
        assertTrue(LegalJournalTruthState.DECLARED in states)
        assertTrue(LegalJournalTruthState.OBSERVED in states)
        assertTrue(LegalJournalTruthState.DOCUMENTED in states)
        assertTrue(LegalJournalTruthState.VERIFIED_EXTERNALLY in states)
    }

    @Test
    fun `ai is not an evidence source and cannot upgrade truth`() {
        assertFalse(LegalJournalSource.entries.any { it.name.contains("AI") })
        assertFalse(LegalJournalTruthState.entries.any { it.name == "AI_VERIFIED" })
    }
}
