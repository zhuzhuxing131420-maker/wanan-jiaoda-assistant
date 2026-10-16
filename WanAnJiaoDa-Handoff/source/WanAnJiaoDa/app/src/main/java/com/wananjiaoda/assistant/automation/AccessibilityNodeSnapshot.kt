package com.wananjiaoda.assistant.automation

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** A value-only copy of the event source. No node reference is retained. */
data class AccessibilityNodeSnapshot(
    val viewIdResourceName: String,
    val bounds: String,
) {
    companion object {
        fun from(source: AccessibilityNodeInfo?): AccessibilityNodeSnapshot {
            if (source == null) return AccessibilityNodeSnapshot("未提供", "未提供")
            return try {
                val rect = Rect()
                source.getBoundsInScreen(rect)
                AccessibilityNodeSnapshot(
                    viewIdResourceName = source.viewIdResourceName?.take(160) ?: "未提供",
                    bounds = "[${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom}]",
                )
            } catch (_: Exception) {
                AccessibilityNodeSnapshot("未提供", "未提供")
            } finally {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    source.recycle()
                }
            }
        }
    }
}
