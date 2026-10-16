package com.wananjiaoda.assistant.automation

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** Read only the explicitly known Vivo chooser; return copied values, not live nodes. */
object WeChatChooserNodes {
    fun collect(root: AccessibilityNodeInfo): List<ScreenTextLine> {
        if (root.packageName?.toString() != WeChatInstanceChooser.PACKAGE) return emptyList()
        return BoundedTreeWalker.collect(root, { it.childCount }, { node, index -> node.getChild(index) },
            { node, _, _, _ ->
                val rect = Rect().also(node::getBoundsInScreen)
                if (!node.isVisibleToUser || !node.isEnabled || rect.isEmpty) emptyList() else
                    listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                        .filter { it.isNotBlank() }.map { ScreenTextLine(it.take(160),
                            ScreenBounds(rect.left, rect.top, rect.right, rect.bottom)) }
            }, release = { if (Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION") it.recycle() } })
            .values.flatten().distinctBy { it.text to it.bounds }.take(80)
    }
}
