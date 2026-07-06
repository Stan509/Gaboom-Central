package com.gaboom.agent

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject

/**
 * Application principale Gaboom Agent
 */
@HiltAndroidApp
class GaboomAgentApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        com.gaboom.agent.data.clock.SecuredClock.init(this)
        
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
                
                android.util.Log.e("GaboomAgentApp", "Global crash captured: $stackTrace")
            } catch (e: Throwable) {
                // Ensure handler itself does not crash
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Phase 3: Start background workers
        com.gaboom.agent.data.sync.HeartbeatWorker.schedule(this)
        com.gaboom.agent.data.sync.TicketExpiryWorker.schedule(this)
    }
}
