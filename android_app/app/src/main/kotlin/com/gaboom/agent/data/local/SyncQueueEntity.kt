package com.gaboom.agent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Persistent queue entry for pending sync actions.
 * The scheduler reads from this table, builds batches, and updates retry info.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "type") val type: String, // e.g., "LEDGER_SYNC", "CONFIG_UPDATE"
    @ColumnInfo(name = "payload") val payload: String, // JSON action details
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "max_retries") val maxRetries: Int = 5
)
