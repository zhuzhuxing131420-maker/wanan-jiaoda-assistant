package com.wananjiaoda.assistant.automation

data class TreeWalkResult<R>(val values: List<R>, val truncated: Boolean)

object BoundedTreeWalker {
    fun <T : Any, R> collect(
        root: T,
        childCount: (T) -> Int,
        childAt: (T, Int) -> T?,
        describe: (T, Int, Int, Int) -> R,
        release: (T) -> Unit = {},
        maxNodes: Int = 200,
        maxDepth: Int = 12,
    ): TreeWalkResult<R> {
        require(maxNodes > 0 && maxDepth >= 0)
        val values = mutableListOf<R>()
        var truncated = false
        fun visit(node: T, parent: Int, depth: Int) {
            val index = values.size
            values.add(describe(node, index, parent, depth))
            val count = childCount(node)
            if (depth >= maxDepth) {
                if (count > 0) truncated = true
                return
            }
            for (i in 0 until count) {
                if (values.size >= maxNodes) {
                    truncated = true
                    break
                }
                val child = childAt(node, i) ?: continue
                try { visit(child, index, depth + 1) } finally { release(child) }
            }
        }
        visit(root, -1, 0)
        return TreeWalkResult(values.toList(), truncated)
    }
}
