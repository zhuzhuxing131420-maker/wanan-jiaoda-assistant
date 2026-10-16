package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class BoundedTreeWalkerTest {
    private data class Node(val label: String, val children: List<Node> = emptyList())
    private val root = Node("root", listOf(Node("a", listOf(Node("aa"))), Node("b")))

    private fun walk(maxNodes: Int = 200, maxDepth: Int = 12, released: MutableList<String> = mutableListOf()) =
        BoundedTreeWalker.collect(root, { it.children.size }, { node, i -> node.children[i] },
            { node, i, parent, depth -> "$i/$parent/$depth:${node.label}" },
            { released.add(it.label) }, maxNodes, maxDepth)

    @Test fun preservesParentRelationships() {
        assertEquals(listOf("0/-1/0:root", "1/0/1:a", "2/1/2:aa", "3/0/1:b"), walk().values)
        assertFalse(walk().truncated)
    }

    @Test fun limitsNodesAndReleasesOnlyAcquiredChildren() {
        val released = mutableListOf<String>()
        val result = walk(maxNodes = 3, released = released)
        assertEquals(3, result.values.size)
        assertTrue(result.truncated)
        assertEquals(listOf("aa", "a"), released)
    }

    @Test fun limitsDepthWithoutObtainingDeeperNodes() {
        val released = mutableListOf<String>()
        val result = walk(maxDepth = 1, released = released)
        assertEquals(listOf("0/-1/0:root", "1/0/1:a", "2/0/1:b"), result.values)
        assertTrue(result.truncated)
        assertEquals(listOf("a", "b"), released)
    }

    @Test fun releasesChildWhenDescriptionFails() {
        val released = mutableListOf<String>()
        try {
            BoundedTreeWalker.collect(root, { it.children.size }, { node, i -> node.children[i] },
                { node, _, _, _ -> if (node.label == "a") error("read failed") else node.label },
                { released.add(it.label) })
            fail("Expected read failure")
        } catch (expected: IllegalStateException) {
            assertEquals("read failed", expected.message)
        }
        assertEquals(listOf("a"), released)
    }
}
