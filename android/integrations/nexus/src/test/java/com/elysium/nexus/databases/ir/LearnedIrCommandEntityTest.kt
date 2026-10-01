package com.elysium.nexus.databases.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [LearnedIrCommandEntity] data class.
 */
class LearnedIrCommandEntityTest {

    @Test
    fun entityHasExpectedDefaults() {
        val entity = LearnedIrCommandEntity(
            label = "Test",
            templateId = "tv-samsung",
            protocolName = "Samsung",
            address = 0x07,
            command = 0x02,
            carrierHz = 38000,
            rawPattern = "4500,4500,560,560",
            confidence = 0.95f,
            capturedAtMs = System.currentTimeMillis()
        )
        assertEquals(0L, entity.id)
        assertEquals("Test", entity.label)
        assertEquals("tv-samsung", entity.templateId)
        assertEquals("Samsung", entity.protocolName)
        assertEquals(0x07, entity.address)
        assertEquals(0x02, entity.command)
        assertEquals(38000, entity.carrierHz)
        assertEquals(0.95f, entity.confidence, 0.001f)
        assertEquals("", entity.extras)
    }

    @Test
    fun entityWithId() {
        val entity = LearnedIrCommandEntity(
            id = 42,
            label = "TV Power",
            templateId = "tv-lg",
            protocolName = "Nec",
            address = 0x04,
            command = 0x08,
            carrierHz = 38000,
            rawPattern = "9000,4500,560,560",
            confidence = 0.88f,
            capturedAtMs = 1000L
        )
        assertEquals(42L, entity.id)
    }

    @Test
    fun entityWithExtras() {
        val entity = LearnedIrCommandEntity(
            label = "AC Cool",
            templateId = "ac-daikin",
            protocolName = "Daikin",
            address = 0x12,
            command = 0x01,
            carrierHz = 38000,
            rawPattern = "5800,2000",
            confidence = 0.92f,
            capturedAtMs = 2000L,
            extras = "toggle=0|repeat=1"
        )
        assertEquals("toggle=0|repeat=1", entity.extras)
    }
}
