package com.gaboom.agent.data.sync

import android.util.Log
import com.gaboom.agent.data.api.DynamicRetrofitProvider
import com.gaboom.agent.data.local.LocationQueueDao
import com.gaboom.agent.data.local.LocationQueueEntity
import com.gaboom.agent.data.model.HeartbeatRequest
import com.gaboom.agent.data.network.NetworkMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val TAG = "LocationSyncManager"

@Singleton
class LocationSyncManager @Inject constructor(
    private val locationQueueDao: LocationQueueDao,
    private val networkMonitor: NetworkMonitor,
    private val syncManagerProvider: Provider<SyncManager>,
    private val dynamicRetrofitProvider: DynamicRetrofitProvider,
    private val offlineLimitEnforcer: OfflineLimitEnforcer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    init {
        scope.launch {
            networkMonitor.connectivityFlow
                .distinctUntilChanged()
                .collect { isOnline ->
                    if (isOnline) {
                        Log.d(TAG, "Network restored, starting GPS history upload")
                        syncQueuedLocations()
                    }
                }
        }
    }

    suspend fun queueLocation(latitude: Double, longitude: Double) {
        val entry = LocationQueueEntity(
            id = UUID.randomUUID().toString(),
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis()
        )
        locationQueueDao.insert(entry)
        Log.d(TAG, "Queued offline GPS update: ($latitude, $longitude)")
        
        if (networkMonitor.isCurrentlyOnline()) {
            syncQueuedLocations()
        }
    }

    fun syncQueuedLocations() {
        if (syncJob?.isActive == true) return
        val syncManager = syncManagerProvider.get()
        if (syncManager.syncState.value.isSyncing) {
            Log.d(TAG, "SyncManager is active, deferring GPS upload to prioritize critical business data")
            return
        }
        syncJob = scope.launch {
            try {
                val queued = locationQueueDao.getAll()
                if (queued.isEmpty()) return@launch
                Log.d(TAG, "Uploading ${queued.size} queued location(s) to server...")
                
                val apiService = dynamicRetrofitProvider.getApiService()
                for (location in queued) {
                    if (!networkMonitor.isCurrentlyOnline()) {
                        Log.d(TAG, "Lost connection, stopping GPS upload")
                        break
                    }
                    val req = HeartbeatRequest(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    try {
                        val response = apiService.heartbeat(req)
                        if (response.isSuccessful) {
                            locationQueueDao.deleteById(location.id)
                            offlineLimitEnforcer.recordServerContact()
                            Log.d(TAG, "Successfully uploaded queued location: (${location.latitude}, ${location.longitude})")
                        } else {
                            Log.e(TAG, "Failed uploading queued location: HTTP ${response.code()}")
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Network exception uploading queued location", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in GPS queue sync: ${e.message}")
            }
        }
    }
}
