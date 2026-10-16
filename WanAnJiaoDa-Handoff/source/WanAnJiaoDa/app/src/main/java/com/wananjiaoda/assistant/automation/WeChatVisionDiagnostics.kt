package com.wananjiaoda.assistant.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object WeChatVisionDiagnostics {
    private val _report = MutableStateFlow("尚未识别小程序画面")
    val report = _report.asStateFlow()
    fun resetReport() { _report.value = "尚未识别小程序画面" }
    fun update(message: String) { _report.value = message }
}
