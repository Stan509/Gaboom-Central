package com.gaboom.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.gaboom.agent.data.clock.ClockHistoryEntity
import com.gaboom.agent.device.DeviceIdentityEntity
import com.gaboom.agent.device.DeviceIdentityDao

/**
 * Room Database pour cache local éphémère
 */

// ═══════════════════════════════════════════════════════════════════════════
// ENTITIES
// ═══════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "local_ticket_cache",
    indices = [Index(value = ["tirage_id", "session_key"])]
)
data class LocalTicketCache(
    @PrimaryKey val ticketUuid: String,
    @ColumnInfo(name = "tirage_id") val tirageId: Int,
    @ColumnInfo(name = "session_key") val sessionKey: String,
    @ColumnInfo(name = "ticket_no") val ticketNo: String,
    @ColumnInfo(name = "total_mise") val totalMise: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "raw_json") val rawJson: String? = null,
    // Phase 3 — Local-First
    /** Unix ms at which this record expires locally (created_at + 95 days). */
    @ColumnInfo(name = "expires_at") val expiresAt: Long = createdAt + 95L * 24 * 60 * 60 * 1000,
    /** Whether this ticket has been uploaded to the server. */
    @ColumnInfo(name = "upload_status") val uploadStatus: String = "PENDING"
)

@Entity(tableName = "tirage_session_cache")
data class TirageSessionCache(
    @PrimaryKey val tirageId: Int,
    @ColumnInfo(name = "session_key") val sessionKey: String,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Sync status for offline tickets
 */
enum class SyncStatus {
    LOCAL_PENDING,       // Waiting to sync
    PRINTED,             // Printed locally
    SYNCING,             // Currently being synced
    SYNCED,              // Successfully synced
    FAILED,              // Sync failed (conflict or error)
    CONFLICT,            // Sync rejected due to conflict
    PENDING,             // Alias/legacy for LOCAL_PENDING
    VALIDATION_PENDING,  // Legacy validation pending
    UPLOAD_FAILED        // Phase 3: background upload failed (server store)
}

@Entity(tableName = "offline_sessions")
data class OfflineSession(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "last_sync") val lastSync: Long,
    @ColumnInfo(name = "device_state") val deviceState: String,
    @ColumnInfo(name = "clock_confidence") val clockConfidence: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "locked") val locked: Boolean = false,
    @ColumnInfo(name = "hash") val hash: String = ""
)

@Entity(tableName = "transaction_local")
data class TransactionLocal(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "recovery_id") val recoveryId: String?, // UUID for crash recovery
    @ColumnInfo(name = "ticket_uuid") val ticketUuid: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "hash") val hash: String = ""
)

@Entity(tableName = "config_local")
data class ConfigLocal(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "hash") val hash: String = ""
)

@Entity(tableName = "clock_snapshots")
data class ClockSnapshot(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "server_time") val serverTime: Long,
    @ColumnInfo(name = "offset") val offset: Long,
    @ColumnInfo(name = "confidence") val confidence: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "hash") val hash: String = ""
)

@Entity(tableName = "security_metadata")
data class SecurityMetadata(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "key_id") val keyId: String,
    @ColumnInfo(name = "public_key") val publicKey: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "hash") val hash: String = ""
)


/**
 * Pending ticket for offline mode.
 * Stores complete ticket payload for later sync.
 */
@Entity(
    tableName = "pending_tickets",
    indices = [
        Index(value = ["tirage_id", "session_key"]),
        Index(value = ["sync_status"]),
        Index(value = ["batch_id"])
    ]
)
data class PendingTicketEntity(
    @PrimaryKey val id: String,  // UUID generated locally
    @ColumnInfo(name = "payload_json") val payloadJson: String,  // Full create request body
    @ColumnInfo(name = "tirage_ids") val tirageIds: String,  // Comma-separated tirage IDs
    @ColumnInfo(name = "tirage_id") val tirageId: Int,  // Primary tirage ID
    @ColumnInfo(name = "session_key") val sessionKey: String?,
    @ColumnInfo(name = "total_mise") val totalMise: Double,
    @ColumnInfo(name = "lines_summary") val linesSummary: String,  // Human-readable summary
    @ColumnInfo(name = "created_at") val createdAt: Long = com.gaboom.agent.data.clock.SecuredClock.now(),
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_retry_at") val lastRetryAt: Long? = null,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "server_ticket_id") val serverTicketId: String? = null,  // Set after successful sync
    @ColumnInfo(name = "server_ticket_no") val serverTicketNo: String? = null,  // Set after successful sync
    // Phase I-A: Batch support for multi-tirage coherence
    @ColumnInfo(name = "batch_id") val batchId: String? = null,  // Group ID for multi-tirage batch
    @ColumnInfo(name = "batch_label") val batchLabel: String? = null,  // Display label (e.g., "Georgia+Tennessee")
    // Phase I-A: HMAC signature for anti-tamper
    @ColumnInfo(name = "hmac_signature") val hmacSignature: String? = null,  // HMAC-SHA256 signature
    @ColumnInfo(name = "local_ticket_no") val localTicketNo: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════
// DAO
// ═══════════════════════════════════════════════════════════════════════════

@Dao
interface LocalTicketCacheDao {
    
    @Query("SELECT * FROM local_ticket_cache WHERE tirage_id = :tirageId ORDER BY created_at DESC")
    fun getTicketsForTirage(tirageId: Int): Flow<List<LocalTicketCache>>
    
    @Query("SELECT * FROM local_ticket_cache WHERE tirage_id = :tirageId AND session_key = :sessionKey ORDER BY created_at DESC")
    fun getTicketsForTirageSession(tirageId: Int, sessionKey: String): Flow<List<LocalTicketCache>>

    @Query("SELECT * FROM local_ticket_cache ORDER BY created_at DESC")
    suspend fun getAllTickets(): List<LocalTicketCache>

    @Query("SELECT * FROM local_ticket_cache ORDER BY created_at DESC")
    fun getAllTicketsFlow(): Flow<List<LocalTicketCache>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ticket: LocalTicketCache)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tickets: List<LocalTicketCache>)
    
    @Query("DELETE FROM local_ticket_cache WHERE tirage_id = :tirageId")
    suspend fun deleteForTirage(tirageId: Int)
    
    @Query("DELETE FROM local_ticket_cache WHERE tirage_id = :tirageId AND session_key != :currentSessionKey")
    suspend fun purgeOldSessions(tirageId: Int, currentSessionKey: String)
    
    @Query("DELETE FROM local_ticket_cache")
    suspend fun deleteAll()

    // Phase 3 — expiry and upload tracking
    @Query("DELETE FROM local_ticket_cache WHERE expires_at > 0 AND expires_at < :nowMs")
    suspend fun deleteExpired(nowMs: Long)

    @Query("UPDATE local_ticket_cache SET upload_status = 'UPLOADED' WHERE ticketUuid = :uuid")
    suspend fun markUploaded(uuid: String)

    @Query("UPDATE local_ticket_cache SET upload_status = 'FAILED' WHERE ticketUuid = :uuid")
    suspend fun markUploadFailed(uuid: String)

    @Query("SELECT * FROM local_ticket_cache WHERE upload_status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingUpload(): List<LocalTicketCache>
}

@Dao
interface TirageSessionCacheDao {
    
    @Query("SELECT * FROM tirage_session_cache WHERE tirageId = :tirageId")
    suspend fun getSessionForTirage(tirageId: Int): TirageSessionCache?
    
    @Query("SELECT session_key FROM tirage_session_cache WHERE tirageId = :tirageId")
    suspend fun getSessionKey(tirageId: Int): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: TirageSessionCache)
    
    @Query("DELETE FROM tirage_session_cache")
    suspend fun deleteAll()
}

@Dao
interface PendingTicketDao {
    
    // ─── Insert ─────────────────────────────────────────────────────────────────
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ticket: PendingTicketEntity)
    @Query("SELECT * FROM pending_tickets WHERE sync_status IN ('LOCAL_PENDING', 'PENDING', 'FAILED', 'PRINTED') ORDER BY created_at ASC")
    suspend fun getPendingAndFailed(): List<PendingTicketEntity>
    
    @Query("SELECT * FROM pending_tickets WHERE sync_status IN ('LOCAL_PENDING', 'PENDING', 'PRINTED') ORDER BY created_at ASC")
    suspend fun getPending(): List<PendingTicketEntity>
    
    @Query("SELECT * FROM pending_tickets WHERE sync_status = 'FAILED' ORDER BY created_at ASC")
    suspend fun getFailed(): List<PendingTicketEntity>
    
    @Query("SELECT * FROM pending_tickets ORDER BY created_at DESC")
    suspend fun getAll(): List<PendingTicketEntity>
    
    @Query("SELECT * FROM pending_tickets WHERE id = :id")
    suspend fun getById(id: String): PendingTicketEntity?
    
    @Query("SELECT * FROM pending_tickets ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<PendingTicketEntity>>
    
    @Query("SELECT COUNT(*) FROM pending_tickets WHERE sync_status IN ('LOCAL_PENDING', 'PENDING', 'FAILED', 'PRINTED')")
    fun getPendingCountFlow(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM pending_tickets WHERE sync_status IN ('LOCAL_PENDING', 'PENDING', 'PRINTED')")
    suspend fun getPendingCount(): Int
    
    @Query("SELECT * FROM pending_tickets WHERE batch_id = :batchId ORDER BY created_at ASC")
    suspend fun getByBatchId(batchId: String): List<PendingTicketEntity>
    
    @Query("SELECT DISTINCT batch_id FROM pending_tickets WHERE batch_id IS NOT NULL AND sync_status IN ('PENDING', 'FAILED', 'SYNCING') ORDER BY created_at DESC")
    suspend fun getActiveBatchIds(): List<String>
    
    @Query("SELECT * FROM pending_tickets WHERE batch_id = :batchId AND sync_status IN ('PENDING', 'FAILED') ORDER BY created_at ASC")
    suspend fun getPendingByBatchId(batchId: String): List<PendingTicketEntity>
    
    // ─── Update ─────────────────────────────────────────────────────────────────
    
    @Update
    suspend fun update(ticket: PendingTicketEntity)
    
    @Query("UPDATE pending_tickets SET sync_status = :status, error_message = :error, retry_count = retry_count + 1, last_retry_at = :timestamp WHERE id = :id")
    suspend fun updateSyncFailed(id: String, status: SyncStatus = SyncStatus.FAILED, error: String?, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE pending_tickets SET sync_status = 'SYNCED', server_ticket_id = :serverTicketId, server_ticket_no = :serverTicketNo, error_message = NULL WHERE id = :id")
    suspend fun markSynced(id: String, serverTicketId: String, serverTicketNo: String)
    
    @Query("UPDATE pending_tickets SET sync_status = 'SYNCING' WHERE id = :id")
    suspend fun markSyncing(id: String)
    
    @Query("UPDATE pending_tickets SET sync_status = 'PENDING' WHERE id = :id")
    suspend fun resetToPending(id: String)
    
    // ─── Delete ─────────────────────────────────────────────────────────────────
    
    @Query("DELETE FROM pending_tickets WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM pending_tickets WHERE sync_status = 'SYNCED'")
    suspend fun deleteSynced()

    // Phase 3 — expiry: returns synced rows older than the given timestamp for batch deletion
    @Query("SELECT * FROM pending_tickets WHERE sync_status = 'SYNCED' AND created_at < :cutoffMs")
    suspend fun getSyncedBefore(cutoffMs: Long): List<PendingTicketEntity>

    @Query("DELETE FROM pending_tickets")
    suspend fun deleteAll()
}

// ═══════════════════════════════════════════════════════════════════════════
// DATABASE
// ═══════════════════════════════════════════════════════════════════════════

val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pending_tickets ADD COLUMN local_ticket_no TEXT")
    }
}

/** Phase 3: adds 95-day expiry and upload status to the local ticket cache. Preserves all existing rows. */
val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Add expires_at: default = created_at + 95 days (in ms)
        db.execSQL(
            "ALTER TABLE local_ticket_cache ADD COLUMN expires_at INTEGER NOT NULL DEFAULT 0"
        )
        // Backfill existing rows: expires_at = created_at + 95 days
        db.execSQL(
            "UPDATE local_ticket_cache SET expires_at = created_at + ${95L * 24 * 60 * 60 * 1000} WHERE expires_at = 0"
        )
        // Add upload_status: existing rows are considered PENDING (they may need re-upload)
        db.execSQL(
            "ALTER TABLE local_ticket_cache ADD COLUMN upload_status TEXT NOT NULL DEFAULT 'PENDING'"
        )
    }
}

@Database(
    entities = [
        LocalTicketCache::class, 
        TirageSessionCache::class, 
        PendingTicketEntity::class,
        OfflineSession::class,
        TransactionLocal::class,
        ConfigLocal::class,
        ClockSnapshot::class,
        SecurityMetadata::class,
        SyncLedgerEntity::class,
        SyncQueueEntity::class,
        IntegrityEventEntity::class,
        DeviceIdentityEntity::class,
        ClockHistoryEntity::class,
        DrawCacheEntity::class,
        LocationQueueEntity::class
    ],
    version = 10,  // Phase 3: expires_at + upload_status on local_ticket_cache
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun localTicketCacheDao(): LocalTicketCacheDao
    abstract fun tirageSessionCacheDao(): TirageSessionCacheDao
    abstract fun pendingTicketDao(): PendingTicketDao
    abstract fun configLocalDao(): ConfigLocalDao
    abstract fun syncLedgerDao(): SyncLedgerDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun clockSnapshotDao(): ClockSnapshotDao
    abstract fun clockHistoryDao(): ClockHistoryDao
    abstract fun offlineSessionDao(): OfflineSessionDao
    abstract fun integrityEventDao(): IntegrityEventDao
    abstract fun deviceIdentityDao(): DeviceIdentityDao
    abstract fun drawCacheDao(): DrawCacheDao
    abstract fun locationQueueDao(): LocationQueueDao
}

/**
 * Type converters for Room
 */
class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name
    
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
