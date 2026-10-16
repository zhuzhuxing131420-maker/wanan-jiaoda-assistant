package com.wananjiaoda.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wananjiaoda.assistant.data.model.CheckInRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInRecordDao {
    @Query("SELECT * FROM check_in_records ORDER BY occurred_at_millis DESC")
    fun observeAll(): Flow<List<CheckInRecordEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: CheckInRecordEntity): Long
}
