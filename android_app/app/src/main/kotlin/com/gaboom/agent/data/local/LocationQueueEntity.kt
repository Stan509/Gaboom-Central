package com.gaboom.agent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
2. LocationQueueEntity stores offline GPS updates.
*/
@Entity(tableName = "location_queue")
data class LocationQueueEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
