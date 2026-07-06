package com.gaboom.agent.data.sync

import android.util.Log
import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.api.DynamicRetrofitProvider
import com.gaboom.agent.data.local.PendingTicketDao
import com.gaboom.agent.data.local.PendingTicketEntity
import com.gaboom.agent.data.local.SyncStatus
import com.gaboom.agent.data.model.MultiTicketCreateRequest
import com.gaboom.agent.data.model.MultiTicketEntry
import com.gaboom.agent.data.model.MultiTicketOverride
import com.gaboom.agent.data.model.FailedTirageInfo
import com.gaboom.agent.data.network.NetworkMonitor
import com.gaboom.agent.data.config.AgentConfigDataStore
import com.gaboom.agent.data.config.DeviceCredentials
import com.gaboom.agent.util.HmacUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncManager"

/**
 * Represents a batch of tickets to sync together (same multi-tirage operation)
 */
data class TicketBatch(
    val batchId: String,
    val batchLabel: String,
    val tickets: List<PendingTicketEntity>
) {
    val totalTickets: Int get() = tickets.size
    val isSingleTicket: Boolean get() = tickets.size == 1
}

/**
 * Result of a batch sync attempt
 */
sealed class BatchSyncResult {
    data class Success(val batchId: String) : BatchSyncResult()
    data class Partial(val batchId: String, val successCount: Int, val failedCount: Int) : BatchSyncResult()
    data class Failure(val batchId: String, val error: String) : BatchSyncResult()
}

/**
 * Overall sync status for UI
 */
data class SyncState(
    val isSyncing: Boolean = false,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val lastSyncTime: Long? = null,
    val lastError: String? = null
)

/**
 * Manages offline ticket synchronization.
 * 
 * Phase I-A2: Batch sync support - groups tickets by batchId and syncs them
 * together via create-multi endpoint.
 */
@Singleton
class SyncManager @Inject constructor(
    private val pendingTicketDao: PendingTicketDao,
    private val dynamicRetrofitProvider: DynamicRetrofitProvider,
    private val networkMonitor: NetworkMonitor,
    private val syncPolicy: com.gaboom.agent.policy.SyncPolicy,
    private val gson: Gson,
    private val agentConfigDataStore: AgentConfigDataStore,
    private val localTicketCacheDao: com.gaboom.agent.data.local.LocalTicketCacheDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _syncEvents = MutableSharedFlow<SyncEvent>()
    val syncEvents: SharedFlow<SyncEvent> = _syncEvents.asSharedFlow()
    
    private var syncJob: Job? = null
    
    // Adaptive batch size controls
    private var currentAdaptiveBatchSize = 50
    
    // Backoff configuration
    private val initialBackoffMs = 5_000L      // 5 seconds
    private val maxBackoffMs = 5 * 60_000L     // 5 minutes
    private val maxRetries = 10
    
    init {
        // Observe network changes and auto-sync
        scope.launch {
            networkMonitor.connectivityFlow
                .distinctUntilChanged()
                .collect { isOnline ->
                    if (isOnline) {
                        Log.d(TAG, "Network restored, triggering auto-sync")
                        syncPendingTickets()
                    }
                }
        }
        
        // Update pending counts
        scope.launch {
            pendingTicketDao.getAllFlow().collect { tickets ->
                val pending = tickets.count { it.syncStatus == SyncStatus.LOCAL_PENDING || it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.PRINTED }
                val failed = tickets.count { it.syncStatus == SyncStatus.FAILED || it.syncStatus == SyncStatus.CONFLICT }
                _syncState.update { it.copy(pendingCount = pending, failedCount = failed) }
            }
        }
    }
    
    /**
     * Sync all pending tickets grouped by batch.
     */
    fun syncPendingTickets() {
        if (syncJob?.isActive == true) {
            Log.d(TAG, "Sync already in progress")
            return
        }
        
        if (!networkMonitor.isCurrentlyOnline()) {
            Log.d(TAG, "Cannot sync: offline")
            return
        }
        
        syncJob = scope.launch {
            _syncState.update { it.copy(isSyncing = true) }
            
            try {
                val pendingTickets = pendingTicketDao.getPending()
                val queueSize = pendingTickets.size
                if (queueSize == 0) {
                    _syncState.update { it.copy(lastSyncTime = System.currentTimeMillis(), lastError = null) }
                    return@launch
                }
                
                val batchSize = when {
                    queueSize <= 20 -> queueSize
                    queueSize in 21..100 -> 20
                    queueSize in 101..500 -> 100
                    else -> currentAdaptiveBatchSize
                }
                
                Log.d(TAG, "Syncing $queueSize pending tickets in batches of size $batchSize (adaptive: $currentAdaptiveBatchSize)")
                val batches = chunkPendingTicketsIntoBatches(pendingTickets, batchSize)
                
                for (batch in batches) {
                    if (!networkMonitor.isCurrentlyOnline()) {
                        Log.d(TAG, "Lost connection during sync, stopping")
                        break
                    }
                    
                    val startTime = System.currentTimeMillis()
                    val result = syncBatch(batch)
                    val duration = System.currentTimeMillis() - startTime
                    
                    if (queueSize > 1000 && result is BatchSyncResult.Success) {
                        if (duration < 1500L) {
                            currentAdaptiveBatchSize = when (currentAdaptiveBatchSize) {
                                50 -> 100
                                100 -> 250
                                250 -> 500
                                else -> 500
                            }
                            Log.d(TAG, "Server responded quickly (${duration}ms). Increased adaptive batch size to $currentAdaptiveBatchSize")
                        } else if (duration > 4000L) {
                            currentAdaptiveBatchSize = when (currentAdaptiveBatchSize) {
                                500 -> 250
                                250 -> 100
                                100 -> 50
                                else -> 50
                            }
                            Log.w(TAG, "Server response slow (${duration}ms). Decreased adaptive batch size to $currentAdaptiveBatchSize")
                        }
                    }
                }
                
                val failedTickets = pendingTicketDao.getFailed().filter { shouldRetry(it) }
                if (failedTickets.isNotEmpty()) {
                    val failedBatches = chunkPendingTicketsIntoBatches(failedTickets, batchSize)
                    for (batch in failedBatches) {
                        if (!networkMonitor.isCurrentlyOnline()) break
                        syncBatch(batch)
                    }
                }
                
                _syncState.update { it.copy(lastSyncTime = System.currentTimeMillis(), lastError = null) }
                
            } catch (e: Exception) {
                Log.e(TAG, "Sync error", e)
                _syncState.update { it.copy(lastError = e.message) }
            } finally {
                _syncState.update { it.copy(isSyncing = false) }
            }
        }
    }
    
    private fun chunkPendingTicketsIntoBatches(tickets: List<PendingTicketEntity>, batchSize: Int): List<TicketBatch> {
        val result = mutableListOf<TicketBatch>()
        val userBatches = tickets.filter { it.batchId != null }.groupBy { it.batchId!! }
        userBatches.forEach { (batchId, list) ->
            val label = list.firstOrNull()?.batchLabel ?: "Multi-ticket"
            result.add(TicketBatch(batchId, label, list))
        }
        
        val individualTickets = tickets.filter { it.batchId == null }
        if (individualTickets.isNotEmpty()) {
            val groupedByTirage = individualTickets.groupBy { it.tirageId ?: 0 }
            var hasMore = true
            val iterators = groupedByTirage.map { it.value.iterator() }
            
            while (hasMore) {
                val currentChunk = mutableListOf<PendingTicketEntity>()
                hasMore = false
                for (it in iterators) {
                    if (it.hasNext()) {
                        currentChunk.add(it.next())
                        hasMore = true
                    }
                }
                if (currentChunk.isNotEmpty()) {
                    currentChunk.chunked(batchSize).forEach { subChunk ->
                        val combinedBatchId = "combined_" + UUID.randomUUID().toString().take(8)
                        result.add(TicketBatch(combinedBatchId, "Lot adaptif", subChunk))
                    }
                }
            }
        }
        return result
    }
    
    private fun groupTicketsByBatch(tickets: List<PendingTicketEntity>): List<TicketBatch> {
        val grouped = tickets.groupBy { it.batchId ?: "single_${it.id}" }
        return grouped.map { (batchId, ticketList) ->
            val label = ticketList.firstOrNull()?.batchLabel ?: "Ticket unique"
            TicketBatch(batchId = batchId, batchLabel = label, tickets = ticketList)
        }
    }
    
    private suspend fun syncBatch(batch: TicketBatch): BatchSyncResult {
        Log.d(TAG, "Syncing batch ${batch.batchId} with ${batch.totalTickets} tickets")
        val deviceCreds = agentConfigDataStore.getDeviceCredentials()
        if (deviceCreds == null) {
            Log.e(TAG, "No device credentials, cannot sync offline tickets")
            batch.tickets.forEach { ticket ->
                pendingTicketDao.updateSyncFailed(
                    id = ticket.id,
                    status = SyncStatus.FAILED,
                    error = "Appareil non enregistre. Veuillez vous reconnecter."
                )
                _syncEvents.emit(SyncEvent.TicketFailed(
                    localId = ticket.id,
                    error = "Appareil non enregistre",
                    isRetryable = false
                ))
            }
            return BatchSyncResult.Failure(batch.batchId, "No device credentials")
        }
        
        batch.tickets.forEach { pendingTicketDao.markSyncing(it.id) }
        
        try {
            dynamicRetrofitProvider.refreshIfNeeded()
            val apiService = dynamicRetrofitProvider.getApiService()
            
            val firstTicket = batch.tickets.first()
            val request = buildCreateMultiRequest(batch)
            val payloadJson = gson.toJson(request)
            
            val signature = HmacUtil.signPayload(
                deviceSecret = deviceCreds.deviceSecret,
                payloadJson = payloadJson,
                sessionKey = firstTicket.sessionKey ?: ""
            )
            
            val response = apiService.createMultiTicketWithHeaders(
                request = request,
                deviceId = deviceCreds.deviceId,
                payloadSign = signature
            )
            
            return if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                handleSyncSuccess(batch, body, deviceCreds)
                BatchSyncResult.Success(batch.batchId)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                handleSyncError(batch, response.code(), errorMsg, deviceCreds)
                BatchSyncResult.Failure(batch.batchId, errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Batch ${batch.batchId} sync exception", e)
            batch.tickets.forEach { ticket ->
                pendingTicketDao.updateSyncFailed(
                    id = ticket.id,
                    status = SyncStatus.FAILED,
                    error = e.message ?: "Erreur reseau"
                )
                _syncEvents.emit(SyncEvent.TicketFailed(
                    localId = ticket.id,
                    error = e.message ?: "Erreur reseau",
                    isRetryable = true
                ))
            }
            return BatchSyncResult.Failure(batch.batchId, e.message ?: "Network error")
        }
    }
    
    private fun buildCreateMultiRequest(batch: TicketBatch): MultiTicketCreateRequest {
        val firstTicket = batch.tickets.first()
        val allTirageIds = batch.tickets.mapNotNull { it.tirageId }.distinct()
        val overrides = mutableMapOf<String, MultiTicketOverride>()
        
        batch.tickets.forEach { ticket ->
            val ticketReq = gson.fromJson(ticket.payloadJson, MultiTicketCreateRequest::class.java)
            val entries = if (batch.batchId.startsWith("combined_")) {
                ticketReq.entries
            } else {
                val overrideForTirage = ticketReq.overrides?.get(ticket.tirageId.toString())
                overrideForTirage?.entries ?: ticketReq.entries
            }
            val tid = ticket.tirageId
            if (tid != null) {
                val ticketNo = ticket.localTicketNo ?: "HL-${ticket.id.take(8).uppercase()}"
                overrides[tid.toString()] = MultiTicketOverride(
                    entries = entries,
                    ticketUuid = ticket.id,
                    ticketNumber = ticketNo
                )
            }
        }
        
        return MultiTicketCreateRequest(
            tirageIds = allTirageIds,
            entries = emptyList(),
            overrides = overrides,
            sessionKey = firstTicket.sessionKey
        )
    }
    
    private suspend fun handleSyncSuccess(
        batch: TicketBatch,
        body: com.gaboom.agent.data.model.MultiTicketCreateResponse,
        deviceCreds: DeviceCredentials
    ) {
        val createdTickets = body.tickets ?: emptyList()
        val failedTirages = body.failed ?: emptyList()
        val tirageToLocalTicket = batch.tickets.associateBy { it.tirageId }
        
        var successCount = 0
        createdTickets.forEach { createdTicket ->
            val localTicket = tirageToLocalTicket[createdTicket.tirageId]
            if (localTicket != null) {
                pendingTicketDao.markSynced(
                    id = localTicket.id,
                    serverTicketId = createdTicket.ticketId,
                    serverTicketNo = createdTicket.ticketNo
                )
                // Phase 3: Mark as uploaded in the local cache
                localTicketCacheDao.markUploaded(localTicket.id)
                
                _syncEvents.emit(SyncEvent.TicketSynced(
                    localId = localTicket.id,
                    serverTicketNo = createdTicket.ticketNo
                ))
                successCount++
                Log.d(TAG, "Ticket ${localTicket.id} synced -> ${createdTicket.ticketNo}")
            }
        }
        
        var failedCount = 0
        failedTirages.forEach { failed ->
            val localTicket = tirageToLocalTicket[failed.tirageId]
            if (localTicket != null) {
                val isConflict = failed.error.lowercase().contains("conflict") || failed.error.lowercase().contains("déjà") || failed.error.lowercase().contains("deja")
                val targetStatus = if (isConflict) SyncStatus.CONFLICT else SyncStatus.FAILED
                val isRetryable = if (isConflict) false else isRetryableError(0, failed.error)
                pendingTicketDao.updateSyncFailed(
                    id = localTicket.id,
                    status = targetStatus,
                    error = failed.error
                )
                _syncEvents.emit(SyncEvent.TicketFailed(
                    localId = localTicket.id,
                    error = failed.error,
                    isRetryable = isRetryable
                ))
                failedCount++
                Log.w(TAG, "Ticket ${localTicket.id} failed: ${failed.error}")
            }
        }
        
        if (failedCount > 0 && successCount > 0) {
            _syncEvents.emit(SyncEvent.BatchPartial(
                batchId = batch.batchId,
                successCount = successCount,
                failedCount = failedCount
            ))
        } else if (failedCount > 0) {
            _syncEvents.emit(SyncEvent.BatchFailed(
                batchId = batch.batchId,
                error = "$failedCount ticket(s) échoué(s)"
            ))
        } else {
            _syncEvents.emit(SyncEvent.BatchSuccess(batchId = batch.batchId))
        }
    }
    
    private suspend fun handleSyncError(
        batch: TicketBatch,
        httpCode: Int,
        errorBody: String?,
        deviceCreds: DeviceCredentials
    ) {
        val errorMsg = errorBody ?: "HTTP $httpCode"
        if (httpCode == 403) {
            batch.tickets.forEach { ticket ->
                pendingTicketDao.updateSyncFailed(
                    id = ticket.id,
                    status = SyncStatus.FAILED,
                    error = "Sécurité: données modifiées. Contact admin."
                )
                _syncEvents.emit(SyncEvent.TicketFailed(
                    localId = ticket.id,
                    error = "Sécurité: données modifiées. Contact admin.",
                    isRetryable = false
                ))
            }
            _syncEvents.emit(SyncEvent.BatchFailed(
                batchId = batch.batchId,
                error = "Sécurité: données modifiées"
            ))
            return
        }
        
        val isConflict = errorMsg.lowercase().contains("conflict") || errorMsg.lowercase().contains("déjà") || errorMsg.lowercase().contains("deja")
        val targetStatus = if (isConflict) SyncStatus.CONFLICT else SyncStatus.FAILED
        val isRetryable = if (isConflict) false else isRetryableError(httpCode, errorMsg)
        
        batch.tickets.forEach { ticket ->
            pendingTicketDao.updateSyncFailed(
                id = ticket.id,
                status = targetStatus,
                error = errorMsg
            )
            // Phase 3: Mark as failed in local cache
            localTicketCacheDao.markUploadFailed(ticket.id)
            
            _syncEvents.emit(SyncEvent.TicketFailed(
                localId = ticket.id,
                error = errorMsg,
                isRetryable = isRetryable
            ))
        }
        
        _syncEvents.emit(SyncEvent.BatchFailed(
            batchId = batch.batchId,
            error = errorMsg
        ))
    }
    
    /**
     * Retry a specific batch manually.
     */
    suspend fun retryBatch(batchId: String) {
        if (!networkMonitor.isCurrentlyOnline()) {
            _syncEvents.emit(SyncEvent.Error("Pas de connexion internet"))
            return
        }
        
        val batchTickets = pendingTicketDao.getAll().filter { 
            it.batchId == batchId && (it.syncStatus == SyncStatus.FAILED || it.syncStatus == SyncStatus.PENDING)
        }
        
        if (batchTickets.isEmpty()) {
            Log.w(TAG, "Batch $batchId not found or empty for retry")
            return
        }
        
        val label = batchTickets.firstOrNull()?.batchLabel ?: "Lot"
        val batch = TicketBatch(
            batchId = batchId,
            batchLabel = label,
            tickets = batchTickets
        )
        
        batchTickets.forEach { pendingTicketDao.resetToPending(it.id) }
        
        syncBatch(batch)
    }
    
    /**
     * Delete all tickets in a batch.
     */
    suspend fun deleteBatch(batchId: String) {
        val batchTickets = pendingTicketDao.getAll().filter { it.batchId == batchId }
        batchTickets.forEach { pendingTicketDao.deleteById(it.id) }
        _syncEvents.emit(SyncEvent.BatchDeleted(batchId))
        Log.d(TAG, "Batch $batchId deleted by user (${batchTickets.size} tickets)")
    }
    
    /**
     * Retry a specific failed ticket manually (legacy - use retryBatch instead).
     */
    suspend fun retryTicket(ticketId: String) {
        val ticket = pendingTicketDao.getById(ticketId)
        if (ticket == null) {
            Log.w(TAG, "Ticket $ticketId not found for retry")
            return
        }
        
        if (ticket.batchId != null) {
            retryBatch(ticket.batchId)
        } else {
            if (!networkMonitor.isCurrentlyOnline()) {
                _syncEvents.emit(SyncEvent.Error("Pas de connexion internet"))
                return
            }
            
            pendingTicketDao.resetToPending(ticketId)
            val batch = TicketBatch(
                batchId = "single_$ticketId",
                batchLabel = "Ticket unique",
                tickets = listOf(ticket)
            )
            syncBatch(batch)
        }
    }
    
    /**
     * Delete a failed ticket (legacy - use deleteBatch instead).
     */
    suspend fun deleteTicket(ticketId: String) {
        val ticket = pendingTicketDao.getById(ticketId)
        if (ticket?.batchId != null) {
            deleteBatch(ticket.batchId)
        } else {
            pendingTicketDao.deleteById(ticketId)
            _syncEvents.emit(SyncEvent.TicketDeleted(ticketId))
            Log.d(TAG, "Ticket $ticketId deleted by user")
        }
    }
    
    /**
     * Clean up synced tickets (optional, for maintenance).
     */
    suspend fun cleanupSynced() {
        pendingTicketDao.deleteSynced()
        Log.d(TAG, "Synced tickets cleaned up")
    }
    
    /**
     * Check if a ticket should be retried based on retry count and backoff.
     */
    private fun shouldRetry(ticket: PendingTicketEntity): Boolean {
        if (ticket.retryCount >= maxRetries) {
            return false
        }
        
        val lastRetry = ticket.lastRetryAt ?: return true
        val backoffMs = calculateBackoff(ticket.retryCount)
        val elapsed = System.currentTimeMillis() - lastRetry
        
        return elapsed >= backoffMs
    }
    
    /**
     * Calculate exponential backoff duration.
     */
    private fun calculateBackoff(retryCount: Int): Long {
        val backoff = initialBackoffMs * (1 shl retryCount.coerceAtMost(10))
        return backoff.coerceAtMost(maxBackoffMs)
    }
    
    /**
     * Check if an error is retryable.
     */
    private fun isRetryableError(httpCode: Int, errorMessage: String): Boolean {
        if (httpCode == 403) return false
        
        if (httpCode in 400..499) {
            return httpCode == 408 || httpCode == 429
        }
        
        if (httpCode >= 500) {
            return true
        }
        
        val nonRetryablePatterns = listOf(
            "session_key",
            "tirage ferm",
            "tirage clos",
            "plafond",
            "dpass",
            "invalide",
            "non trouv",
            "modifies"
        )
        
        val lowerError = errorMessage.lowercase()
        return nonRetryablePatterns.none { lowerError.contains(it) }
    }
}

/**
 * Events emitted by SyncManager for UI updates.
 */
sealed class SyncEvent {
    data class TicketSynced(val localId: String, val serverTicketNo: String) : SyncEvent()
    data class TicketFailed(val localId: String, val error: String, val isRetryable: Boolean) : SyncEvent()
    data class TicketDeleted(val localId: String) : SyncEvent()
    
    data class BatchSuccess(val batchId: String) : SyncEvent()
    data class BatchPartial(val batchId: String, val successCount: Int, val failedCount: Int) : SyncEvent()
    data class BatchFailed(val batchId: String, val error: String) : SyncEvent()
    data class BatchDeleted(val batchId: String) : SyncEvent()
    
    data class Error(val message: String) : SyncEvent()
}
