package com.wananjiaoda.assistant.automation

data class AppWindowCandidate(val packageName: String, val application: Boolean, val focused: Boolean, val layer: Int)
object KeyboardWindowPolicy {
    fun focusedWechatIndex(activePackage: String, inputMethodPackage: String,
        windows: List<AppWindowCandidate>): Int? {
        if (inputMethodPackage.isBlank() || activePackage != inputMethodPackage) return null
        val focused = windows.withIndex().filter { it.value.application && it.value.focused }
            .maxByOrNull { it.value.layer } ?: return null
        return focused.index.takeIf { focused.value.packageName == "com.tencent.mm" }
    }
}
