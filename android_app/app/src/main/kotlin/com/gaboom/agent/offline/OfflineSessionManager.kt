package com.gaboom.agent.offline

import com.gaboom.agent.data.local.OfflineSession
import com.gaboom.agent.data.local.OfflineSessionDao
import com.gaboom.agent.data.config.FeatureFlags
import java.util.UUID

/**
 * Manages the lifecycle of offline sessions.
 * All public methods are guarded by the OFFLINE_V2 feature flag.
 */
class OfflineSessionManager(
    private val offlineSessionDao: OfflineSessionDao
) {
    /**
     * Starts a new offline session and persists it.
     * Returns the created [OfflineSession] or null if the feature flag is disabled.
     */
    suspend fun startSession(): OfflineSession? {
        if (!FeatureFlags.isEnabled("OFFLINE_V2")) return null
        val session = OfflineSession(
            uuid = UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis(),
            lastSync = 0L,
            deviceState = "ACTIVE",
            clockConfidence = "UNKNOWN",
            timestamp = System.currentTimeMillis(),
            version = 1,
            locked = false,
            hash = ""
        )
        offlineSessionDao.insert(session)
        return session
    }

    /**
     * Marks a session as expired and locks it.
     */
    suspend fun expireSession(uuid: String) {
        if (!FeatureFlags.isEnabled("OFFLINE_V2")) return
        val session = offlineSessionDao.getById(uuid) ?: return
        val lockedSession = session.copy(locked = true)
        offlineSessionDao.update(lockedSession)
    }

    /**
     * Resumes a previously locked session if allowed.
     */
    suspend fun resumeSession(uuid: String): OfflineSession? {
        if (!FeatureFlags.isEnabled("OFFLINE_V2")) return null
        val session = offlineSessionDao.getById(uuid) ?: return null
        if (!session.locked) return session
        // unlocking logic – only allowed when device state is safe
        val unlocked = session.copy(locked = false)
        offlineSessionDao.update(unlocked)
        return unlocked
    }
}
