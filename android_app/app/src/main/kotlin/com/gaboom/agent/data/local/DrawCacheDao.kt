package com.gaboom.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DrawCacheDao {
    @Query("SELECT * FROM draw_cache ORDER BY id ASC")
    suspend fun getAllDraws(): List<DrawCacheEntity>

    @Query("SELECT * FROM draw_cache WHERE id = :id")
    suspend fun getDrawById(id: Int): DrawCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(draws: List<DrawCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draw: DrawCacheEntity)

    @Query("DELETE FROM draw_cache")
    suspend fun deleteAll()
}
