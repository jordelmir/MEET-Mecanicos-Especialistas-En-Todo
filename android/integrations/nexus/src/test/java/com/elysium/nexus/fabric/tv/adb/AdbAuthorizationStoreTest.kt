package com.elysium.nexus.fabric.tv.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbAuthorizationStoreTest {

    @Test
    fun resolveIdentity_isStableAcrossCalls() {
        val store = MemoryAdbAuthorizationStore()
        val first = store.resolveIdentity()
        assertNotNull(first.toPem())
        // The persisted identity is returned forever after.
        val second = store.resolveIdentity()
        assertEquals(first.publicKeyB64, second.publicKeyB64)
        assertEquals(first.privateKeyB64, second.privateKeyB64)
    }

    @Test
    fun saveThenLoad_roundTripsThePem() {
        val store = MemoryAdbAuthorizationStore()
        val auth = AdbAuthorization.generate()
        assertTrue(store.save(auth.toPem()))
        val loaded = AdbAuthorization.loadFromPem(requireNotNull(store.load()))
        assertNotNull(loaded)
        assertEquals(auth.privateKeyB64, requireNotNull(loaded).privateKeyB64)
    }

    @Test
    fun clear_removesTheIdentity() {
        val store = MemoryAdbAuthorizationStore()
        store.save(AdbAuthorization.generate().toPem())
        store.clear()
        assertEquals(null, store.load())
    }
}