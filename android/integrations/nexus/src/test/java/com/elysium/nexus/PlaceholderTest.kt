package com.elysium.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Smoke test for Phase 0.1.
 *
 * The point of these tests is *not* to assert business logic — there
 * is none yet. The point is to prove the JVM test source set compiles
 * and runs. A red test here means the toolchain is broken, not that a
 * feature is wrong.
 *
 * Replace this file with real unit tests in Phase 0.2.
 */
class PlaceholderTest {

    @Test
    fun placeholderObjectIsAccessible() {
        assertNotNull(Placeholder)
    }

    @Test
    fun buildLabelIsPinned() {
        // Pinning the build label means a future iteration that
        // accidentally removes the constant will fail loudly here
        // instead of silently shipping an empty label.
        assertEquals("0.1.0-foundation", Placeholder.BUILD)
    }
}
