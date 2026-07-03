package com.gaboom.agent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Persistent device identity information used to detect APK cloning and device spoofing.
 */
@Entity(tableName = "device_identity")
data class DeviceIdentityEntity(
    @PrimaryKey val id: String = "singleton", // only one row
    val stationUuid: String,
    val hardwareUuid: String,
    val androidId: String,
    val installUuid: String,
    val apkUuid: String,
    val keystoreUuid: String,
    val createdAt: Long = Instant.now().toEpochMilli()
)
