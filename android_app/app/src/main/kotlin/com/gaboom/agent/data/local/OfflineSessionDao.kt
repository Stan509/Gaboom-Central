package com.gaboom.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: OfflineSession)

    @Update
    suspend fun update(session: OfflineSession)

    @Delete
    suspend fun delete(session: OfflineSession)

    @Query("SELECT * FROM offline_sessions WHERE uuid = :uuid LIMIT 1")
    suspend fun getById(uuid: String): OfflineSession?

    @Query("SELECT * FROM offline_sessions")
    fun getAllFlow(): Flow<List<OfflineSession>>

    @Query("SELECT * FROM offline_sessions WHERE locked = 1 LIMIT 1")
    suspend fun getLockedSession(): OfflineSession?
}
