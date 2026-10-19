package com.wananjiaoda.assistant.automation

import org.junit.Assert.*
import org.junit.Test

class NavigationLabelDiagnosticsTest {
    private fun line(text: String, y: Int = 400) = ScreenTextLine(text, ScreenBounds(40, y, 600, y + 70))

    @Test fun savesMisreadEntryLabelsOnlyOnKnownNavigationPages() {
        val labels = listOf(line("小程序", 190), line("最近便用 >"), line("晚安交大地学入", 600))
        assertEquals(listOf("最近便用 >", "晚安交大地学入"),
            NavigationLabelDiagnostics.candidates(labels, 2800).map { it.text })
    }

    @Test fun chatAndPersonalFieldsAreNotCollected() {
        assertTrue(NavigationLabelDiagnostics.candidates(listOf(line("晚安测试同学")), 2800).isEmpty())
        val labels = listOf(line("最近使用", 190), line("姓名：测试同学"), line("学号：123456"),
            line("晚安123456"), line("晚安交大地学人"), line("晚安" + "测试".repeat(15)))
        assertEquals(listOf("最近使用", "晚安交大地学人"),
            NavigationLabelDiagnostics.candidates(labels, 2800).map { it.text })
    }

    @Test fun lowerPageAndInvalidDimensionsAreRejected() {
        val labels = listOf(line("小程序", 190), line("晚安测试", 2000))
        assertTrue(NavigationLabelDiagnostics.candidates(labels, 2800).isEmpty())
        assertTrue(NavigationLabelDiagnostics.candidates(labels, 0).isEmpty())
    }
}
