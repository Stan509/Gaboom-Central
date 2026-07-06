package com.gaboom.agent.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.gaboom.agent.data.local.LocalTicketCacheDao
import com.gaboom.agent.data.local.PendingTicketDao
import com.gaboom.agent.data.local.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "TicketExpiryWorker"

/**
 * Phase 3 — Local-First Architecture.
 *
 * Runs once daily during device charging to:
 *   1. Delete [LocalTicketCache] rows whose [expiresAt] < now (95-day window).
 *   2. Delete [PendingTicketEntity] rows that are SYNCED and older than 95 days.
 *
 * Server-side records are NEVER touched — expiry is local only.
 */
class TicketExpiryWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = androidx.room.Room.databaseBuilder(
                applicationContext,
                com.gaboom.agent.data.local.AgentDatabase::class.java,
                "agent_database"
            ).build()

            val now = System.currentTimeMillis()
            val cutoff95Days = now - (95L * 24 * 60 * 60 * 1000)

            // 1. Delete expired local cache rows
            val cacheDao = db.localTicketCacheDao()
            cacheDao.deleteExpired(now)
            Log.i(TAG, "Expired local ticket cache rows deleted (cutoff=${cutoff95Days})")

            // 2. Delete old synced pending rows
            val pendingDao = db.pendingTicketDao()
            val synced = pendingDao.getSyncedBefore(cutoff95Days)
            synced.forEach { pendingDao.deleteById(it.id) }
            Log.i(TAG, "Deleted ${synced.size} old synced pending tickets")

            db.close()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Expiry worker failed: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "gaboom_ticket_expiry_v3"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(false)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<TicketExpiryWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(6, TimeUnit.HOURS) // don't run immediately on startup
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
