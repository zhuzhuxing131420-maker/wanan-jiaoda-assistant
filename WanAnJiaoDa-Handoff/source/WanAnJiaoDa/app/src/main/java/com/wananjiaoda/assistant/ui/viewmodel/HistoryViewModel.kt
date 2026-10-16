package com.wananjiaoda.assistant.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wananjiaoda.assistant.data.local.AppDatabase
import com.wananjiaoda.assistant.data.repository.OfflineCheckInHistoryRepository
import com.wananjiaoda.assistant.domain.model.CheckInRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    val records = OfflineCheckInHistoryRepository(
        AppDatabase.getInstance(application).checkInRecordDao(),
    ).observeRecords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<CheckInRecord>())
}
