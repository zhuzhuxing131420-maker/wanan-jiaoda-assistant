package com.wananjiaoda.assistant.domain.repository

import com.wananjiaoda.assistant.domain.model.CheckInRecord
import kotlinx.coroutines.flow.Flow

interface CheckInHistoryRepository {
    fun observeRecords(): Flow<List<CheckInRecord>>
    suspend fun addRecord(record: CheckInRecord)
}
