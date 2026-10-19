package com.wananjiaoda.assistant

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.wananjiaoda.assistant.scheduler.CheckInScheduler
import com.wananjiaoda.assistant.scheduler.ScheduledExecution
import com.wananjiaoda.assistant.service.ConnectionProtectionController
import com.wananjiaoda.assistant.ui.navigation.AppNavigation
import com.wananjiaoda.assistant.ui.theme.WanAnJiaoDaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        ConnectionProtectionController.resume(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ScheduledExecution.resume(applicationContext)
        lifecycleScope.launch {
            try {
                CheckInScheduler.refresh(applicationContext)
            } catch (error: Exception) {
                Log.e(CheckInScheduler.TAG, "启动时恢复任务失败", error)
            }
        }
        setContent { WanAnJiaoDaTheme { AppNavigation() } }
    }
}
