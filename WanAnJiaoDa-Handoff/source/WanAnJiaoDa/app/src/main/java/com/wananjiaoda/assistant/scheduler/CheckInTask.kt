package com.wananjiaoda.assistant.scheduler

import android.util.Log

/** Legacy immediate receipt probe. Formal and deferred navigation alarms use ScheduledExecution. */
object CheckInTask {
    const val TAG = "WanAnJiaoDaTask"

    fun run() {
        Log.i(TAG, "立即触发记录测试完成，不执行签到")
    }
}
