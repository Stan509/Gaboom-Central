package com.gaboom.agent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for accessing configuration key/value pairs stored in the local Room database.
 * This is used by the offline engine to persist lightweight config values such as
 * last sync timestamp, clock offset, etc. All callers should guard usage with
 * [OfflineFeatureGuard] to ensure the offline engine is enabled.
 */
@Dao
interface ConfigLocalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ConfigLocal)

    @Query("SELECT * FROM config_local WHERE `key` = :key LIMIT 1")
    suspend fun getConfig(key: String): ConfigLocal?

    @Query("SELECT * FROM config_local")
    fun getAllConfigs(): Flow<List<ConfigLocal>>
}
