package com.elysium369.meet.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProprietaryKnowledgeSearchTest {
    @Test
    fun `builds safe prefix query from words and accents`() {
        assertEquals("text:sensor* AND text:presión* AND text:ckp*", "Sensor presión CKP".toSafeFtsQuery())
        assertEquals("text:ignorar* AND text:select* AND text:drop*", "ignorar' SELECT -- DROP".toSafeFtsQuery())
    }

    @Test
    fun `blank punctuation cannot become an fts expression`() {
        assertNull(" -- () \" ".toSafeFtsQuery())
    }
}
