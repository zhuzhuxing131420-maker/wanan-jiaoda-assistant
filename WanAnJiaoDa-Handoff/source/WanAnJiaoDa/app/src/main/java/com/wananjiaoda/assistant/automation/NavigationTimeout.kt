package com.wananjiaoda.assistant.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A normal callback keeps coordinator cancellation outside the launch receiver's scope. */
internal fun navigationTimeout(scope: CoroutineScope, timeoutMillis: Long, onTimeout: () -> Unit): Job =
    scope.launch {
        delay(timeoutMillis)
        onTimeout()
    }
