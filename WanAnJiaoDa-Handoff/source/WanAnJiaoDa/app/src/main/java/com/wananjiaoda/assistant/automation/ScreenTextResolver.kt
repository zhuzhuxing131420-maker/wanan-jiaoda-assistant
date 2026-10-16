package com.wananjiaoda.assistant.automation

import java.text.Normalizer
import kotlin.math.abs

object ScreenTextResolver {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace(Regex("\\s+"), "").let { if (it == "最近便用") "最近使用" else it }

    fun unique(lines: List<ScreenTextLine>, expected: String,
        region: (ScreenBounds) -> Boolean = { true }): ScreenTextLine? {
        val text = normalize(expected)
        val values = lines.map { it.copy(text = normalize(it.text)) }
            .filter { it.text.isNotEmpty() && region(it.bounds) }.take(160)
        val candidates = values.filter { it.text == text }.toMutableList()
        fun append(parts: List<ScreenTextLine>, combined: String) {
            if (combined == text) {
                candidates.add(ScreenTextLine(text, ScreenBounds(parts.minOf { it.bounds.left }, parts.minOf { it.bounds.top },
                    parts.maxOf { it.bounds.right }, parts.maxOf { it.bounds.bottom })))
                return
            }
            if (parts.size >= 3) return
            val previous = parts.last()
            values.filter { it !in parts && text.startsWith(combined + it.text) && follows(previous.bounds, it.bounds) }
                .forEach { append(parts + it, combined + it.text) }
        }
        values.filter { it.text != text && text.startsWith(it.text) }.forEach { append(listOf(it), it.text) }
        if (candidates.isEmpty()) return null
        val chosen = candidates.minBy { area(it.bounds) }
        return if (candidates.all { overlap(chosen.bounds, it.bounds) >= 0.5 }) chosen else null
    }

    private fun follows(a: ScreenBounds, b: ScreenBounds): Boolean {
        val height = maxOf(a.height, b.height)
        val sameRow = abs(a.centerY - b.centerY) <= height / 2 && b.left - a.right in -10..(height * 1.5).toInt()
        val wrapped = b.centerY > a.centerY && b.top - a.bottom in -5..height && abs(a.left - b.left) <= height
        return sameRow || wrapped
    }
    private fun area(box: ScreenBounds): Long = maxOf(1, box.right - box.left).toLong() * maxOf(1, box.height)
    private fun overlap(a: ScreenBounds, b: ScreenBounds): Double {
        val intersection = maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left)).toLong() *
            maxOf(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
        return intersection.toDouble() / (area(a) + area(b) - intersection)
    }
}
