package com.gaboom.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gaboom.agent.data.clock.ClockHistoryEntity

@Dao
interface ClockHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClockHistoryEntity)

    @Query("SELECT * FROM clock_history ORDER BY observedAt DESC")
    suspend fun getAll(): List<ClockHistoryEntity>

    @Query("DELETE FROM clock_history WHERE uuid = :uuid")
    suspend fun deleteById(uuid: String)

    @Query("DELETE FROM clock_history")
    suspend fun deleteAll()
}
