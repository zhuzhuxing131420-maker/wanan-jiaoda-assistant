package com.wananjiaoda.assistant.data.repository

import com.wananjiaoda.assistant.data.local.CheckInRecordDao
import com.wananjiaoda.assistant.data.model.CheckInRecordEntity
import com.wananjiaoda.assistant.domain.model.CheckInRecord
import com.wananjiaoda.assistant.domain.repository.CheckInHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineCheckInHistoryRepository(private val dao: CheckInRecordDao) : CheckInHistoryRepository {
    override fun observeRecords(): Flow<List<CheckInRecord>> =
        dao.observeAll().map { rows -> rows.map(CheckInRecordEntity::toDomain) }
    override suspend fun addRecord(record: CheckInRecord) { dao.insert(record.toEntity()) }
}

private fun CheckInRecordEntity.toDomain() = CheckInRecord(id, occurredAtMillis, outcome, detail)
private fun CheckInRecord.toEntity() = CheckInRecordEntity(id, occurredAtMillis, outcome, detail)
