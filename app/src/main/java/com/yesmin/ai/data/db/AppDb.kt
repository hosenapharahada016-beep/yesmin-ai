package com.yesmin.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MemoryEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context.applicationContext, AppDb::class.java, "yesmin.db").build()
    }
}
