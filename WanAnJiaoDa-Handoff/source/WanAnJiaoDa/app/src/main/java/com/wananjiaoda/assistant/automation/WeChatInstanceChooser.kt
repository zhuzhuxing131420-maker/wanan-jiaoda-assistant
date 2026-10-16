package com.wananjiaoda.assistant.automation

object WeChatInstanceChooser {
    const val PACKAGE = "com.vivo.doubleinstance"
    fun ordinaryBounds(packageName: String, lines: List<ScreenTextLine>): ScreenBounds? {
        if (packageName != PACKAGE) return null
        val values = lines.map { it.copy(text = it.text.replace(Regex("\\s+"), "")) }
        if (values.none { it.text == "选择要使用的应用" }) return null
        val ordinary = values.singleOrNull { it.text == "微信" } ?: return null
        val clone = values.singleOrNull { it.text in setOf("Ⅱ·微信", "II·微信", "Ⅱ微信", "II微信") } ?: return null
        if (ordinary.bounds.right > clone.bounds.left ||
            kotlin.math.abs(ordinary.bounds.centerY - clone.bounds.centerY) > 120) return null
        return ordinary.bounds
    }
}
