package com.gaboom.agent

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application principale Gaboom Agent
 */
@HiltAndroidApp
class GaboomAgentApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    
    @Inject lateinit var offlineLimitEnforcer: com.gaboom.agent.data.sync.OfflineLimitEnforcer
    
    @Inject lateinit var syncManager: com.gaboom.agent.data.sync.SyncManager
    
    @Inject lateinit var agentConfigDataStore: com.gaboom.agent.data.config.AgentConfigDataStore
    
    @Inject lateinit var pendingTicketDao: com.gaboom.agent.data.local.PendingTicketDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Initialize secured clock
        com.gaboom.agent.data.clock.SecuredClock.init(this)
        
        // Record app start time for grace period
        appScope.launch {
            agentConfigDataStore.setAppStartTime(System.currentTimeMillis())
        }
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                val stackTrace = sw.toString()
                
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val timestamp = sdf.format(java.util.Date())
                
                val logText = "=== CRASH REPORT ===\nTimestamp: $timestamp\nThread: ${thread.name}\n$stackTrace\n\n"
                
                val logFile = java.io.File(filesDir, "crash_log.txt")
                logFile.appendText(logText)
                
                Log.e("GaboomAgentApp", "Global crash captured: $stackTrace")
            } catch (e: Throwable) {
                // Ensure handler itself does not crash
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Phase 3: Start background workers
        com.gaboom.agent.data.sync.HeartbeatWorker.schedule(this)
        com.gaboom.agent.data.sync.TicketExpiryWorker.schedule(this)
        
        // Phase 3: Initialize gatekeeper and trigger initial sync
        appScope.launch {
            // Wait for DataStore to be ready
            delay(1000)
            
            // Initialize the gate state
            offlineLimitEnforcer.recompute()
            Log.i("GaboomAgentApp", "OfflineLimitEnforcer initialized: ${offlineLimitEnforcer.gateState.value}")
            
            // Check for pending tickets and trigger initial sync
            val pendingCount = pendingTicketDao.getPendingCount()
            Log.i("GaboomAgentApp", "Pending tickets at startup: $pendingCount")
            if (pendingCount > 0) {
                Log.i("GaboomAgentApp", "Triggering initial sync for $pendingCount pending tickets")
                syncManager.syncPendingTickets()
            }
            
            // Periodic recompute every 2 minutes
            while (true) {
                delay(2 * 60 * 1000L) // 2 minutes
                offlineLimitEnforcer.recompute()
                Log.d("GaboomAgentApp", "Periodic recompute: ${offlineLimitEnforcer.gateState.value}")
            }
        }
    }
}
