package com.elysium369.meet.core.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdDisconnectSemanticsTest {
    @Test fun userDisconnectNotCountedAsFailure() {
        assertTrue(DisconnectSemantics.isExpected(DisconnectReason.USER_REQUESTED))
        assertFalse(DisconnectSemantics.countsAsPhysicalLinkLoss(DisconnectReason.USER_REQUESTED))
    }

    @Test fun classicEofCreatesDisconnectEvent() {
        assertFalse(DisconnectSemantics.isExpected(DisconnectReason.CLASSIC_STREAM_EOF))
        assertTrue(DisconnectSemantics.countsAsPhysicalLinkLoss(DisconnectReason.CLASSIC_STREAM_EOF))
    }

    @Test fun bleGattDisconnectCreatesDisconnectEvent() {
        assertFalse(DisconnectSemantics.isExpected(DisconnectReason.BLE_GATT_DISCONNECTED))
        assertTrue(DisconnectSemantics.countsAsPhysicalLinkLoss(DisconnectReason.BLE_GATT_DISCONNECTED))
    }

    @Test fun everyUnexpectedDisconnectHasReason() {
        val unexpected = DisconnectReason.entries.filterNot(DisconnectSemantics::isExpected)
        assertTrue(unexpected.isNotEmpty())
        assertTrue(unexpected.none { it.name.isBlank() })
    }
}
