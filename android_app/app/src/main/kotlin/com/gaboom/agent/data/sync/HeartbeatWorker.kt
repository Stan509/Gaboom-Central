package com.gaboom.agent.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import androidx.hilt.work.HiltWorker
import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.api.DynamicRetrofitProvider
import com.gaboom.agent.data.clock.SecuredClock
import com.gaboom.agent.data.config.AgentConfigDataStore
import com.gaboom.agent.data.model.HeartbeatRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.assisted.AssistedInject
import dagger.assisted.Assisted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "HeartbeatWorker"

/**
 * Phase 3 — Local-First Architecture.
 *
 * Runs every 2 minutes to:
 *   1. Ping the server and record the last successful contact time.
 *   2. Synchronize [SecuredClock] with the server's reported timestamp.
 *   3. Detect maintenance mode (HTTP 503) and suspend the 25-min offline timer.
 *   4. Trigger a range-extension request if the device number range is nearly exhausted.
 *   5. Recompute the [OfflineLimitEnforcer] gate state after every attempt.
 *
 * Must be enqueued as a UniquePeriodicWork at app startup (see GaboomAgentApp).
 */
@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val dynamicRetrofitProvider: DynamicRetrofitProvider,
    private val agentConfigDataStore: AgentConfigDataStore,
    private val offlineLimitEnforcer: OfflineLimitEnforcer
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val api: AgentApiService = dynamicRetrofitProvider.getApiService()
            
            // Fetch last known location
            val location = getLastKnownLocation(applicationContext)
            val request = HeartbeatRequest(
                latitude = location?.latitude,
                longitude = location?.longitude
            )
            
            val response = api.heartbeat(request)

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    Log.d(TAG, "Heartbeat OK — server status=${body?.status}")

                    // Sync clock from response timestamp header or body
                    val serverTimeHeader = response.headers()["Server-Time"]
                    if (serverTimeHeader != null) {
                        try {
                            SecuredClock.update(serverTimeHeader.toLong())
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not parse server Server-Time header: $serverTimeHeader")
                        }
                    } else {
                        val serverDateHeader = response.headers()["Date"]
                        if (serverDateHeader != null) {
                            try {
                                val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                                val parsed = sdf.parse(serverDateHeader)?.time
                                if (parsed != null) SecuredClock.update(parsed)
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not parse server Date header: $serverDateHeader")
                            }
                        }
                    }

                    // Record successful contact (clears maintenance flag) and recompute gate state
                    offlineLimitEnforcer.recordServerContact()

                    // Check if range needs extension
                    if (agentConfigDataStore.rangeNeedsExtension()) {
                        requestRangeExtension(api)
                    }

                    Result.success()
                }

                response.code() == 503 || response.code() == 502 -> {
                    // Server in maintenance — suspend offline timer
                    Log.i(TAG, "Server maintenance (HTTP ${response.code()}) — suspending offline timer")
                    offlineLimitEnforcer.recordServerMaintenance()
                    Result.retry()
                }

                response.code() == 401 -> {
                    // Not authenticated — not a connectivity issue; don't penalize offline timer
                    Log.d(TAG, "Heartbeat 401 — session expired, skipping offline timer update")
                    // Still recompute to ensure gate state is current
                    offlineLimitEnforcer.recompute()
                    Result.success()
                }

                else -> {
                    Log.w(TAG, "Heartbeat failed: HTTP ${response.code()}")
                    offlineLimitEnforcer.recompute()
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat network error: ${e.message}")
            offlineLimitEnforcer.recompute()
            Result.retry()
        }
    }

    private suspend fun requestRangeExtension(api: AgentApiService) {
        try {
            val creds = agentConfigDataStore.getDeviceCredentials() ?: return
            val configResponse = api.getAgentConfig(deviceId = creds.deviceId)
            if (configResponse.isSuccessful) {
                val body = configResponse.body()
                if (body?.success == true && body.range != null) {
                    agentConfigDataStore.saveTicketNumberRange(
                        start = body.range.ticketNumberStart,
                        end = body.range.ticketNumberEnd,
                        current = body.range.ticketNumberCurrent
                    )
                    agentConfigDataStore.clearRangeExtensionFlag()
                    Log.i(TAG, "Range extended: ${body.range.ticketNumberStart}–${body.range.ticketNumberEnd}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Range extension failed: ${e.message}")
        }
    }

    private fun getLastKnownLocation(context: Context): android.location.Location? {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null
        }
        
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return null
        val providers = locationManager.getProviders(true)
        var bestLocation: android.location.Location? = null
        
        for (provider in providers) {
            try {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            } catch (e: SecurityException) {
                // Ignore security exceptions
            }
        }
        return bestLocation
    }

    companion object {
        const val WORK_NAME = "gaboom_heartbeat_v3"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(2, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}