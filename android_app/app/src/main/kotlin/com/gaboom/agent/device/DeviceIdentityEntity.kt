package com.gaboom.agent.device

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent entity storing a unique device identifier and optional fingerprint.
 */
@Entity(tableName = "device_identity")
data class DeviceIdentityEntity(
    @PrimaryKey val id: Long = 1L, // singleton row
    val deviceUuid: String,
    val fingerprint: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
