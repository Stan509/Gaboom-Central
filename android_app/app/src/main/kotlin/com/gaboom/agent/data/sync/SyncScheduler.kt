package com.gaboom.agent.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gaboom.agent.data.config.FeatureFlags
import java.util.concurrent.TimeUnit

/**
 * Interface de planification des synchronisations périodiques (SyncScheduler).
 * Conforme aux exigences Enterprise de la Phase 1.
 * Par défaut, l'activation WorkManager reste inactive si le flag SYNC_ENGINE_V2 est désactivé.
 */
interface SyncScheduler {
    fun schedulePeriodicSync()
    fun cancelSync()
    fun isSyncActive(): Boolean
}

class AndroidSyncScheduler(private val context: Context) : SyncScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun schedulePeriodicSync() {
        if (!FeatureFlags.isEnabled("SYNC_ENGINE_V2")) {
            // Disabled behind Feature Flag for Phase 1 preservation
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "gaboom_ticket_sync",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    override fun cancelSync() {
        workManager.cancelUniqueWork("gaboom_ticket_sync")
    }

    override fun isSyncActive(): Boolean {
        return FeatureFlags.isEnabled("SYNC_ENGINE_V2")
    }
}

// Stub worker to allow clean compilation
class SyncWorker(context: Context, workerParams: androidx.work.WorkerParameters) : 
    androidx.work.CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}
