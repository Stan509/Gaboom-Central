package com.gaboom.agent.device

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for persisting the device identity.
 */
@Dao
interface DeviceIdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeviceIdentityEntity)

    @Query("SELECT * FROM device_identity WHERE id = 1 LIMIT 1")
    suspend fun getIdentity(): DeviceIdentityEntity?
}
