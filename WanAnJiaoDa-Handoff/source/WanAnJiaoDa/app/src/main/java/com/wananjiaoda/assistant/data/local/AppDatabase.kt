package com.wananjiaoda.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wananjiaoda.assistant.data.model.CheckInRecordEntity

@Database(entities = [CheckInRecordEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInRecordDao(): CheckInRecordDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "wan_an_jiao_da.db",
            ).build().also { instance = it }
        }
    }
}
