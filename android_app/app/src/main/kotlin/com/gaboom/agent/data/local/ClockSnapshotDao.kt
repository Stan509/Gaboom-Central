package com.gaboom.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gaboom.agent.data.local.ClockSnapshot

@Dao
interface ClockSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ClockSnapshot)

    @Query("SELECT * FROM clock_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): ClockSnapshot?

    @Query("DELETE FROM clock_snapshots WHERE uuid = :uuid")
    suspend fun deleteById(uuid: String)

    @Query("DELETE FROM clock_snapshots")
    suspend fun deleteAll()
}
