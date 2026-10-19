package com.wananjiaoda.assistant.automation

/** Bounded entry-label diagnostics only; never supplies tap targets. */
object NavigationLabelDiagnostics {
    fun candidates(lines: List<ScreenTextLine>, height: Int): List<ScreenTextLine> {
        if (height <= 0) return emptyList()
        val knownPage = lines.any {
            it.bounds.bottom < height * 0.3 && ScreenTextResolver.normalize(it.text) in setOf("小程序", "最近使用")
        }
        if (!knownPage) return emptyList()
        return lines.filter {
            val text = it.text.replace(Regex("\\s+"), "")
            it.bounds.top >= 0 && it.bounds.bottom < height * 0.45 && text.length in 2..16 &&
                (text.startsWith("最近") || text.startsWith("晚安")) &&
                text.matches(Regex("[\\u4e00-\\u9fff…⋯.·<>＞]+")) &&
                !text.contains("姓名") && !text.contains("学号")
        }.take(8)
    }
}
