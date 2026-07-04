package com.gaboom.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationQueueEntity)

    @Query("SELECT * FROM location_queue ORDER BY timestamp ASC")
    suspend fun getAll(): List<LocationQueueEntity>

    @Query("DELETE FROM location_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM location_queue")
    suspend fun deleteAll()
}
