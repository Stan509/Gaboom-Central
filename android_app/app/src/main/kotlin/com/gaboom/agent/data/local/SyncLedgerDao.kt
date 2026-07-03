package com.gaboom.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for accessing the persistent sync ledger.
 * All writes must go through this DAO; synchronization reads from it.
 */
@Dao
interface SyncLedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: SyncLedgerEntity)

    @Update
    suspend fun update(entry: SyncLedgerEntity)

    @Query("SELECT * FROM sync_ledger WHERE uuid = :uuid LIMIT 1")
    suspend fun getById(uuid: String): SyncLedgerEntity?

    @Query("SELECT * FROM sync_ledger ORDER BY seq_num ASC")
    fun getAll(): Flow<List<SyncLedgerEntity>>

    @Query("SELECT * FROM sync_ledger WHERE retry_count < :maxRetries ORDER BY seq_num ASC")
    suspend fun getPending(maxRetries: Int = Int.MAX_VALUE): List<SyncLedgerEntity>

    @Query("DELETE FROM sync_ledger WHERE uuid = :uuid")
    suspend fun deleteById(uuid: String)
}
