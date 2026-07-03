package com.gaboom.agent.data.clock

/**
 * Interfaces de base du LotteryClock pour l'application Android (Phase 1).
 * Désactivé par défaut.
 */

interface ILotteryClockManager {
    fun initializeClock()
    fun syncWithServer(serverTime: Long)
    fun isClockReady(): Boolean
}

interface DriftDetector {
    fun calculateDrift(localTime: Long, serverTime: Long): Long
    fun shouldSync(): Boolean
    fun maxAllowedDrift(): Long
}

interface TamperMonitor {
    fun checkTampering(): Boolean
    fun flagViolation()
}

interface ClockRecovery {
    fun attemptRecovery()
}

interface ClockDiagnostics {
    fun recordInitialization(serverTime: Long)
    fun recordSync(serverTime: Long)
    fun recordDrift(driftMs: Long)
    fun getDriftHistory(): List<Long>
}

interface SignedTimeVerifier {
    fun verifySignature(signedTime: Long, signature: ByteArray): Boolean
}

interface ClockVersionManager {
    fun currentVersion(): String
    fun bumpVersion()
}

