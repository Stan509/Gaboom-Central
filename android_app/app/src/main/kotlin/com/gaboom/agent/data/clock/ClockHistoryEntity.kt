package com.gaboom.agent.data.clock

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to store clock drift history for diagnostics and security.
 */
@Entity(tableName = "clock_history")
data class ClockHistoryEntity(
    @PrimaryKey val uuid: String,
    val observedAt: Long,
    val driftMs: Long,
    val confidence: String,
    val version: Int = 1,
    val hash: String = ""
)
