package com.elysium.nexus.fabric.infrared.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-PTG-01 §2 — Schema-v5 gate policy tests (manifest is the sole authority).
 *
 * Proves the catalog-version acceptance contract:
 * - v5+ accepted; v4 and below rejected
 * - missing/non-numeric manifest version REJECTED (fail-closed: the hardcoded
 *   SHA-256 fallback was removed — a catalog without a declared version has
 *   no authoritative identity and must not install)
 */
class CatalogSchemaVersionGateTest {

    @Test
    fun `accepts schema v5`() {
        assertTrue(CatalogManifest.isSchemaVersionAccepted(5))
    }

    @Test
    fun `accepts schema v6 and above`() {
        assertTrue(CatalogManifest.isSchemaVersionAccepted(6))
        assertTrue(CatalogManifest.isSchemaVersionAccepted(99))
    }

    @Test
    fun `rejects schema v4`() {
        assertFalse(CatalogManifest.isSchemaVersionAccepted(4))
    }

    @Test
    fun `rejects schema v0 through v4`() {
        for (v in 0..4) {
            assertFalse("v$v must be rejected", CatalogManifest.isSchemaVersionAccepted(v))
        }
    }

    @Test
    fun `rejects negative schema versions`() {
        assertFalse(CatalogManifest.isSchemaVersionAccepted(-1))
    }

    @Test
    fun `absent manifest version is rejected - fail closed`() {
        // PTG-01: the hardcoded hash gate no longer exists. The manifest is
        // the authority, so a catalog without a declared schema version must
        // NOT install — its identity is unknown.
        assertFalse(CatalogManifest.isSchemaVersionAccepted(null))
    }
}