package com.gaboom.agent.offline

import com.gaboom.agent.data.local.IntegrityEventEntity
import com.gaboom.agent.data.local.IntegrityEventDao
import com.gaboom.agent.data.config.FeatureFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * OfflineSecurityManager evaluates integrity events and can lock the offline session
 * when the trust score falls below a threshold.
 */
class OfflineSecurityManager(
    private val integrityEventDao: IntegrityEventDao
) {
    private val trustThreshold = 0.7

    /**
     * Calculates a simple trust score based on count of integrity events.
     * Returns a value between 0.0 and 1.0.
     */
    suspend fun calculateTrustScore(): Double = withContext(Dispatchers.IO) {
        val events = integrityEventDao.getAll().firstOrNull() ?: emptyList()
        if (events.isEmpty()) return@withContext 1.0
        // Each event reduces trust by 0.1 up to a minimum of 0.0
        val penalty = events.size * 0.1
        (1.0 - penalty).coerceIn(0.0, 1.0)
    }

    /**
     * Evaluates the current trust score and locks the session if below threshold.
     */
    suspend fun evaluateAndLock(sessionUuid: String, sessionDao: com.gaboom.agent.data.local.OfflineSessionDao) {
        if (!FeatureFlags.isEnabled("OFFLINE_V2")) return
        val score = calculateTrustScore()
        if (score < trustThreshold) {
            val session = sessionDao.getById(sessionUuid) ?: return
            val locked = session.copy(locked = true)
            sessionDao.update(locked)
        }
    }
}
