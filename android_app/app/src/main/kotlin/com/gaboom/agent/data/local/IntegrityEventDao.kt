package com.gaboom.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IntegrityEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: IntegrityEventEntity)

    @Query("SELECT * FROM integrity_event ORDER BY timestamp DESC")
    fun getAll(): Flow<List<IntegrityEventEntity>>

    @Query("SELECT * FROM integrity_event WHERE severity = :severity ORDER BY timestamp DESC")
    fun getBySeverity(severity: IntegritySeverity): Flow<List<IntegrityEventEntity>>
}
