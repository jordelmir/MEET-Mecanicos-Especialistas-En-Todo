package com.elysium369.meet.safety.science.provenance

import org.junit.Assert.*
import org.junit.Test

class MerkleTreeTest {

    private val merkle = MerkleTree()

    @Test
    fun `single hash returns itself normalized`() {
        val root = merkle.root(listOf("ABC123"))
        // Single-element tree: level.size == 1, while loop skips,
        // returns normalize("ABC123") = "abc123"
        assertEquals("abc123", root)
    }

    @Test
    fun `two hashes produce deterministic root`() {
        val root = merkle.root(listOf("hash_a", "hash_b"))
        assertEquals(sha256("hash_a:hash_b"), root)
    }

    @Test
    fun `three hashes — odd leaf duplicates`() {
        val root = merkle.root(listOf("a", "b", "c"))
        // Level 1: [sha256("a:b"), sha256("c:c")]
        // Level 2: sha256("${sha256("a:b")}:${sha256("c:c")}")
        val ab = sha256("a:b")
        val cc = sha256("c:c")
        assertEquals(sha256("$ab:$cc"), root)
    }

    @Test
    fun `same inputs always produce same root`() {
        val inputs = (1..50).map { sha256("event-$it") }
        val root1 = merkle.root(inputs)
        val root2 = merkle.root(inputs)
        assertEquals(root1, root2)
    }

    @Test
    fun `different inputs produce different roots`() {
        val a = merkle.root(listOf("x", "y"))
        val b = merkle.root(listOf("x", "z"))
        assertNotEquals(a, b)
    }

    @Test
    fun `changing one leaf changes root`() {
        val inputs = (1..20).map { sha256("leaf-$it") }
        val original = merkle.root(inputs)

        val tampered = inputs.toMutableList().apply {
            this[10] = sha256("forged-leaf")
        }
        val modified = merkle.root(tampered)

        assertNotEquals(original, modified)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty list throws`() {
        merkle.root(emptyList())
    }

    @Test
    fun `large tree 1000 leaves completes`() {
        val inputs = (1..1000).map { sha256("leaf-$it") }
        val root = merkle.root(inputs)
        assertTrue(root.length == 64) // SHA-256 hex
    }
}
