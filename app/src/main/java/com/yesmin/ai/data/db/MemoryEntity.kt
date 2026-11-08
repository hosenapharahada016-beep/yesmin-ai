package com.yesmin.ai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "fact", "preference" or "summary" */
    val type: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
