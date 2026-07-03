package com.gaboom.agent.data.clock

import com.gaboom.agent.data.config.FeatureFlags

/**
 * Interface pour la hiérarchie de temps de confiance (ClockAuthority - Phase 1).
 * Garantit que l'heure locale d'Android n'est jamais l'autorité principale.
 */
enum class ClockConfidence(val display: String) {
    CONFIDENCE_100("100%"),
    CONFIDENCE_95("95%"),
    CONFIDENCE_80("80%"),
    CONFIDENCE_60("60%"),
    INVALID("INVALID")
}

interface ClockAuthority {
    fun getCurrentTime(): Long
    fun verifyDrift(serverTime: Long): Long
    fun isClockTampered(): Boolean
    fun getConfidenceScore(): ClockConfidence
}

class TrustedClockAuthority : ClockAuthority {
    private var lastServerTime: Long = 0L
    private var lastSyncLocalTime: Long = 0L
    private var isTampered = false

    override fun getCurrentTime(): Long {
        return SecuredClock.now()
    }

    override fun verifyDrift(serverTime: Long): Long {
        lastServerTime = serverTime
        lastSyncLocalTime = System.currentTimeMillis()
        SecuredClock.update(serverTime)
        
        val localTime = System.currentTimeMillis()
        val drift = Math.abs(localTime - serverTime)
        
        // Flag tamper detection if clock is shifted by more than 5 minutes (300 000ms)
        if (drift > 300_000) {
            isTampered = true
        }
        return drift
    }

    override fun isClockTampered(): Boolean {
        return if (FeatureFlags.isEnabled("LOTTERY_CLOCK")) {
            isTampered
        } else {
            false
        }
    }

    override fun getConfidenceScore(): ClockConfidence {
        if (!FeatureFlags.isEnabled("CLOCK_AUTHORITY_SCORE_ENABLED")) {
            return ClockConfidence.CONFIDENCE_100
        }

        if (isTampered) {
            return ClockConfidence.INVALID
        }

        val now = System.currentTimeMillis()
        val syncAge = now - lastSyncLocalTime
        val drift = if (lastSyncLocalTime > 0L) Math.abs(now - (lastServerTime + (now - lastSyncLocalTime))) else 0L

        if (drift > 1_500_000) { // > 25 minutes
            return ClockConfidence.INVALID
        }

        return when {
            lastSyncLocalTime == 0L -> ClockConfidence.CONFIDENCE_60
            syncAge < 60_000 && drift < 5_000 -> ClockConfidence.CONFIDENCE_100
            syncAge < 3_600_000 && drift < 30_000 -> ClockConfidence.CONFIDENCE_95
            syncAge < 86_400_000 && drift < 120_000 -> ClockConfidence.CONFIDENCE_80
            else -> ClockConfidence.CONFIDENCE_60
        }
    }
}
