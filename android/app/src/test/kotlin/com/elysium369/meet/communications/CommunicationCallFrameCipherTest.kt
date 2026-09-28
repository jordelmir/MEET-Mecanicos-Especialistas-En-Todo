package com.elysium369.meet.communications

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class CommunicationCallFrameCipherTest {
    private val cipher=CommunicationCallFrameCipher(Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))
    @Test fun bidirectionalAudioPreservesBytesAndBindsCallIdentity() {
        val bytes=ByteArray(CommunicationCallFrameCipher.MAX_AUDIO_BYTES) { (it%127).toByte() }
        val frame=cipher.encrypt("call-a","sender-a",1,"AUDIO",bytes)
        assertArrayEquals(bytes,cipher.decrypt(frame))
        assertTrue(runCatching { cipher.decrypt(frame.copy(call="call-b")) }.isFailure)
        assertTrue(runCatching { cipher.decrypt(frame.copy(sender="sender-b")) }.isFailure)
        assertTrue(runCatching { cipher.decrypt(frame.copy(sequence=2)) }.isFailure)
    }
    @Test fun connectionHandshakeHasAnAuthenticatedEmptyPayload() {
        val frame=cipher.encrypt("call-a","sender-a",1,"HELLO",byteArrayOf())
        assertEquals(0,cipher.decrypt(frame).size)
        assertTrue(runCatching { cipher.decrypt(frame.copy(kind="AUDIO")) }.isFailure)
    }
    @Test fun framesCannotAllocateUnboundedAudioOrUseInvalidKeys() {
        assertTrue(runCatching { cipher.encrypt("call-a","sender-a",1,"AUDIO",ByteArray(CommunicationCallFrameCipher.MAX_AUDIO_BYTES+1)) }.isFailure)
        assertTrue(runCatching { CommunicationCallFrameCipher("invalid") }.isFailure)
    }
}
