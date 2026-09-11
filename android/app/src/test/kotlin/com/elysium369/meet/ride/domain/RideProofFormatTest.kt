package com.elysium369.meet.ride.domain

import org.junit.Assert.*
import org.junit.Test

class RideProofFormatTest {
    @Test fun rejectsDisguisedAndOversizedFiles() {
        assertNull(RideProofFormat.extension("<html>fake jpg</html>".toByteArray()))
        assertNull(RideProofFormat.extension(ByteArray(RideProofFormat.MAX_BYTES + 1)))
        assertNull(RideProofFormat.extension(byteArrayOf(-1, -40)))
    }
    @Test fun preservesActualSupportedFormat() {
        assertEquals("pdf", RideProofFormat.extension("%PDF-1.7".toByteArray()))
        assertEquals("png", RideProofFormat.extension(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        assertEquals("jpg", RideProofFormat.extension(byteArrayOf(-1, -40, -1, -32)))
    }
}
