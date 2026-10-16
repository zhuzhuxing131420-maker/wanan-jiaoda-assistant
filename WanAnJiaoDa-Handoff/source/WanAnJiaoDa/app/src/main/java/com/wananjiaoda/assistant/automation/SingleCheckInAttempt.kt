package com.wananjiaoda.assistant.automation

import com.wananjiaoda.assistant.domain.model.CheckInTimeZone
import java.time.Instant
import java.time.LocalTime
import kotlin.math.abs

enum class AttemptPhase { WAITING, TAP_REQUESTED, VERIFYING, SUCCESS, SKIPPED, FAILED }
enum class AttemptEffect { WAIT, TAP, VERIFY, FINISH }
data class RecognizedFrame(val sessionId: Long, val requestedAtMillis: Long, val width: Int, val height: Int,
    val recognition: CheckInPageRecognition, val lines: List<ScreenTextLine> = emptyList(), val windowId: Int = -1,
    val packageName: String = "com.tencent.mm")

class SingleCheckInAttempt(val startedAtMillis: Long, val targetConfirmed: Boolean) {
    var phase = AttemptPhase.WAITING
        private set
    var reason = "等待签到页面"
        private set
    var tapRequestedAtMillis: Long? = null
        private set
    val running: Boolean get() = phase in setOf(AttemptPhase.WAITING, AttemptPhase.TAP_REQUESTED, AttemptPhase.VERIFYING)
    private var firstReady: RecognizedFrame? = null

    fun onFrame(frame: RecognizedFrame, nowMillis: Long, targetWindow: Boolean): AttemptEffect {
        if (!running) return AttemptEffect.WAIT
        if (!targetConfirmed || !targetWindow || nowMillis - frame.requestedAtMillis !in 0..10000) {
            firstReady = null
            return AttemptEffect.WAIT
        }
        val now = Instant.ofEpochMilli(nowMillis).atZone(CheckInTimeZone.zoneId)
        if (now.toLocalDate() != Instant.ofEpochMilli(startedAtMillis).atZone(CheckInTimeZone.zoneId).toLocalDate())
            return cancel("测试期间日期发生变化")
        if (phase == AttemptPhase.VERIFYING) {
            if (frame.requestedAtMillis > (tapRequestedAtMillis ?: Long.MAX_VALUE) &&
                frame.recognition.state == CheckInPageState.ALREADY_DONE) {
                phase = AttemptPhase.SUCCESS
                reason = "已核对今天的打卡记录"
                return AttemptEffect.FINISH
            }
            return AttemptEffect.WAIT
        }
        if (phase != AttemptPhase.WAITING) return AttemptEffect.WAIT
        if (frame.recognition.state == CheckInPageState.ALREADY_DONE) {
            phase = AttemptPhase.SKIPPED
            reason = "今天已打卡，本次跳过点击"
            return AttemptEffect.FINISH
        }
        val time = now.toLocalTime()
        val box = frame.recognition.button
        if (time.isBefore(LocalTime.of(19, 0)) || !time.isBefore(LocalTime.of(22, 0)) ||
            frame.recognition.state != CheckInPageState.READY || box == null ||
            box.left < 0 || box.top < 0 || box.right <= box.left || box.bottom <= box.top ||
            box.right > frame.width || box.bottom > frame.height) {
            firstReady = null
            return AttemptEffect.WAIT
        }
        val previous = firstReady
        firstReady = frame
        val oldBox = previous?.recognition?.button
        if (previous == null || oldBox == null || frame.requestedAtMillis - previous.requestedAtMillis !in 500..10000 ||
            previous.width != frame.width || previous.height != frame.height ||
            abs((oldBox.left + oldBox.right) / 2 - (box.left + box.right) / 2) > 40 ||
            abs(oldBox.centerY - box.centerY) > 40) {
            reason = "已识别按钮，等待第二次位置确认"
            return AttemptEffect.WAIT
        }
        phase = AttemptPhase.TAP_REQUESTED
        reason = "已请求一次点击"
        tapRequestedAtMillis = nowMillis
        return AttemptEffect.TAP
    }

    fun gestureResult(completed: Boolean): AttemptEffect {
        if (phase != AttemptPhase.TAP_REQUESTED) return AttemptEffect.WAIT
        if (!completed) return cancel("手势未完成，签到结果未确认")
        phase = AttemptPhase.VERIFYING
        reason = "正在核对今天的打卡记录"
        return AttemptEffect.VERIFY
    }

    fun cancel(message: String): AttemptEffect {
        if (!running) return AttemptEffect.WAIT
        phase = AttemptPhase.FAILED
        reason = message
        return AttemptEffect.FINISH
    }
}
