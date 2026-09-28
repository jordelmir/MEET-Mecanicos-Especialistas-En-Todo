package com.elysium.nexus.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [BatteryState] validation.
 */
class BatteryStateTest {

    @Test
    fun zeroPercentIsValid() {
        assertTrue(BatteryState.validate(BatteryState(0, false)) is ValidationResult.Valid)
    }

    @Test
    fun oneHundredPercentIsValid() {
        assertTrue(BatteryState.validate(BatteryState(100, true)) is ValidationResult.Valid)
    }

    @Test
    fun negativeLevelIsRejected() {
        val r = BatteryState.validate(BatteryState(-1, false)) as ValidationResult.Invalid
        assertTrue(r.errors.single() is ValidationError.IntegerOutOfRange)
    }

    @Test
    fun aboveOneHundredIsRejected() {
        val r = BatteryState.validate(BatteryState(101, false)) as ValidationResult.Invalid
        assertTrue(r.errors.single() is ValidationError.IntegerOutOfRange)
    }
}
