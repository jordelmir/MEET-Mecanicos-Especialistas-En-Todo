package com.elysium369.meet.communications

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CommunicationTransportCipherTest {
    @Test fun twoIndependentKeysDecryptAndWrongContextFailsClosed() {
        val cipher = CommunicationTransportCipher()
        val first="test-"+UUID.randomUUID()
        val second="test-"+UUID.randomUUID()
        try {
            val aad="meet.communication.v1|conversation|event|sender|device|TEXT"
            val envelope=cipher.encrypt("Hola entre dos usuarios",aad,mapOf("one" to cipher.publicKey(first),"two" to cipher.publicKey(second)))
            assertTrue(cipher.isValidRecipientKey(cipher.publicKey(first)))
            val signature=cipher.sign(first,"authenticated payload")
            assertTrue(cipher.verify(cipher.publicKey(first),"authenticated payload",signature))
            assertFalse(cipher.verify(cipher.publicKey(first),"tampered payload",signature))
            assertFalse(cipher.verify(cipher.publicKey(second),"authenticated payload",signature))
            assertFalse(envelope.contains("Hola entre dos usuarios"))
            assertEquals("Hola entre dos usuarios",cipher.decrypt(envelope,first,"one",aad))
            assertEquals("Hola entre dos usuarios",cipher.decrypt(envelope,second,"two",aad))
            assertTrue(runCatching { cipher.decrypt(envelope,first,"one",aad+"changed") }.isFailure)
            assertTrue(runCatching { cipher.decrypt(envelope,first,"two",aad) }.isFailure)
            assertTrue(runCatching { cipher.decrypt(envelope,second,"missing",aad) }.isFailure)
        } finally {
            val store=java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry("meet.communication.transport.v2.$first")
            store.deleteEntry("meet.communication.transport.v2.$second")
        }
    }
}
