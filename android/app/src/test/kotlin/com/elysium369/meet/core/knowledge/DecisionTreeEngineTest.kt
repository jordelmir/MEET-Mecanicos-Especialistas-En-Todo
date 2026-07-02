package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionTreeEngineTest {

    private val engine = DecisionTreeEngine()

    private fun sampleTree() = DecisionTree(
        id = "tree_test",
        title = "Test tree",
        rootId = "n1",
        nodes = listOf(
            DecisionNode(id = "n1", question = "Battery OK?", yesId = "n2", noId = "n3", unknownId = "n2"),
            DecisionNode(id = "n2", question = "Fuse OK?", yesId = "n4", noId = "n5"),
            DecisionNode(id = "n3", question = "Charge battery", isTerminal = true),
            DecisionNode(id = "n4", question = "Done", isTerminal = true),
            DecisionNode(id = "n5", question = "Replace fuse", isTerminal = true)
        )
    )

    @Test
    fun `walk with all YES answers reaches terminal`() {
        val path = engine.walk(sampleTree(), mapOf(
            "n1" to DecisionAnswer.YES,
            "n2" to DecisionAnswer.YES
        ))
        // 3 nodes: n1, n2, n4
        assertEquals(3, path.size)
        assertEquals("n1", path[0].first.id)
        assertEquals("n2", path[1].first.id)
        assertEquals("n4", path[2].first.id)
        assertTrue(path.last().first.isTerminal)
    }

    @Test
    fun `walk with NO on n1 jumps directly to n3 terminal`() {
        val path = engine.walk(sampleTree(), mapOf("n1" to DecisionAnswer.NO))
        assertEquals(2, path.size)
        assertEquals("n1", path[0].first.id)
        assertEquals("n3", path[1].first.id)
    }

    @Test
    fun `UNKNOWN answer falls back to yesId`() {
        val path = engine.walk(sampleTree(), mapOf(
            "n1" to DecisionAnswer.UNKNOWN,
            "n2" to DecisionAnswer.YES
        ))
        assertEquals(3, path.size)
        assertEquals("n2", path[1].first.id)
    }

    @Test
    fun `nextNode returns the first unanswered node`() {
        val tree = sampleTree()
        val first = engine.nextNode(tree, emptyMap())
        assertEquals("n1", first?.id)
        val second = engine.nextNode(tree, mapOf("n1" to DecisionAnswer.YES))
        assertEquals("n2", second?.id)
    }

    @Test
    fun `nextNode returns null when complete`() {
        val tree = sampleTree()
        val done = mapOf(
            "n1" to DecisionAnswer.YES,
            "n2" to DecisionAnswer.YES
        )
        assertNull(engine.nextNode(tree, done))
    }

    @Test
    fun `walk detects cycle and bails out`() {
        val cyclicTree = DecisionTree(
            id = "tree_cycle",
            title = "Cyclic",
            rootId = "a",
            nodes = listOf(
                DecisionNode(id = "a", question = "A", yesId = "b", noId = "b"),
                DecisionNode(id = "b", question = "B", yesId = "a", noId = "a")
            )
        )
        val path = engine.walk(cyclicTree, emptyMap())
        // Safety bound: 100 nodes max
        assertTrue("must not loop forever, got ${path.size}", path.size <= 100)
    }
}
