package com.wananjiaoda.assistant.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityTextTest {
    @Test fun observedNullElementDoesNotCrashAndKeepsValidText() {
        assertEquals("微信", AccessibilityText.summarize(listOf(null, "微信")))
    }
    @Test fun allNullAndEmptyEventsProduceEmptySummary() {
        assertEquals("", AccessibilityText.summarize(listOf(null, null)))
        assertEquals("", AccessibilityText.summarize(emptyList()))
    }
    @Test fun nullsDoNotConsumeTheThreeEntryBudget() {
        assertEquals("一 二 三 …", AccessibilityText.summarize(listOf(null, "一", null, "二", "三", "四")))
    }
    @Test fun normalizesWhitespaceAcrossEntries() {
        assertEquals("第一行 第二行", AccessibilityText.summarize(listOf("第一行\n", "\t第二行")))
    }
    @Test fun capsLargePlatformText() {
        assertEquals("字".repeat(160), AccessibilityText.summarize(listOf("字".repeat(400))))
    }
}
