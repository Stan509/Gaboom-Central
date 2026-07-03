package com.gaboom.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the persistent sync queue used by SyncScheduler.
 */
@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: SyncQueueEntity)

    @Update
    suspend fun update(entry: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE uuid = :uuid LIMIT 1")
    suspend fun getById(uuid: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue ORDER BY next_attempt_at ASC")
    fun getAll(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE retry_count < max_retries AND next_attempt_at <= :now ORDER BY next_attempt_at ASC")
    suspend fun getReady(now: Long = System.currentTimeMillis()): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE uuid = :uuid")
    suspend fun deleteById(uuid: String)
}
