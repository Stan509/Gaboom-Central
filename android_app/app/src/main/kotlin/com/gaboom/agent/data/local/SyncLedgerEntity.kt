package com.gaboom.agent.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Ledger entry for all offline actions that need to be synchronized later.
 * Each entry is immutable once created.
 */
@Entity(tableName = "sync_ledger")
data class SyncLedgerEntity(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "seq_num") val sequenceNumber: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "hash") val integrityHash: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "station_id") val stationId: String,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "payload") val payload: String // JSON representation of the action
)
