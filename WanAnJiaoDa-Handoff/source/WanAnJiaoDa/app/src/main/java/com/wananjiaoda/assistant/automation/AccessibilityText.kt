package com.wananjiaoda.assistant.automation

object AccessibilityText {
    private val whitespace = Regex("\\s+")
    fun summarize(values: Iterable<CharSequence?>): String =
        values.asSequence().filterNotNull().joinToString(" ", limit = 3, truncated = "…") { it.toString() }
            .replace(whitespace, " ").take(160)
}
