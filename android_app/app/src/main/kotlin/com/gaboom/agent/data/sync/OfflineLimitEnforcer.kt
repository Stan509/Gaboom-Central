package com.gaboom.agent.data.sync

import android.util.Log
import com.gaboom.agent.data.clock.SecuredClock
import com.gaboom.agent.data.config.AgentConfigDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OfflineLimitEnforcer"

/** 25 minutes in milliseconds */
private const val MAX_OFFLINE_MS = 25L * 60L * 1000L

/** 2 minutes max clock drift allowed */
private const val MAX_CLOCK_DRIFT_MS = 2L * 60L * 1000L

/**
 * Reason why ticket creation is blocked.
 */
enum class SaleBlockedReason {
    /** Allowed — nothing is blocking. */
    NONE,
    /** Server unreachable for more than 25 minutes (and server is NOT in maintenance). */
    OFFLINE_LIMIT_EXCEEDED,
    /** Device clock drifted more than 2 minutes from server time. */
    CLOCK_DRIFT_EXCEEDED,
    /** Server contact has never been established (first boot, no sync yet). */
    NO_SERVER_CONTACT
}

data class SaleGateState(
    val reason: SaleBlockedReason = SaleBlockedReason.NONE,
    /** Remaining minutes before offline limit hits (null when not relevant). */
    val minutesBeforeBlock: Int? = null,
    /** Clock drift in seconds (null when not relevant). */
    val clockDriftSeconds: Long? = null
) {
    val isBlocked: Boolean get() = reason != SaleBlockedReason.NONE
}

/**
 * Phase 3 — Local-First Architecture.
 *
 * Central gatekeeper for ticket creation:
 * - Blocks sales if server has been unreachable for > 25 minutes AND server is NOT in maintenance.
 * - Blocks NEW ticket creation if clock drift exceeds 120 seconds (already-created tickets unaffected).
 * - Never blocks if server is responding with HTTP 503 / maintenance mode.
 */
@Singleton
class OfflineLimitEnforcer @Inject constructor(
    private val agentConfigDataStore: AgentConfigDataStore
) {

    private val _gateState = MutableStateFlow(SaleGateState())
    val gateState: StateFlow<SaleGateState> = _gateState.asStateFlow()

    /**
     * Call this after every successful server response (including heartbeat).
     */
    suspend fun recordServerContact() {
        agentConfigDataStore.updateServerContact()
        recompute()
    }

    /**
     * Call this when server responds with maintenance indicator (HTTP 503 / maintenance flag).
     * The 25-min offline timer is paused during maintenance.
     */
    suspend fun recordServerMaintenance() {
        agentConfigDataStore.recordServerMaintenance()
        recompute()
    }

    /**
     * Recomputes the current gate state. Call after each heartbeat result.
     */
    suspend fun recompute() {
        val now = System.currentTimeMillis()
        val lastContact = agentConfigDataStore.getLastServerContactAt()
        val inMaintenance = agentConfigDataStore.isServerInMaintenance()

        // ── Clock drift check ─────────────────────────────────────────────────
        val secureClock = SecuredClock.now()
        val drift = Math.abs(now - secureClock)
        if (drift > MAX_CLOCK_DRIFT_MS && secureClock > 0L) {
            val driftSec = drift / 1000L
            Log.w(TAG, "Clock drift exceeded: ${driftSec}s")
            _gateState.value = SaleGateState(
                reason = SaleBlockedReason.CLOCK_DRIFT_EXCEEDED,
                clockDriftSeconds = driftSec
            )
            return
        }

        // ── No contact yet ────────────────────────────────────────────────────
        if (lastContact == 0L) {
            Log.w(TAG, "No server contact ever recorded")
            _gateState.value = SaleGateState(reason = SaleBlockedReason.NO_SERVER_CONTACT)
            return
        }

        // ── Maintenance mode: timer suspended ─────────────────────────────────
        if (inMaintenance) {
            Log.d(TAG, "Server in maintenance — offline timer suspended")
            _gateState.value = SaleGateState(reason = SaleBlockedReason.NONE)
            return
        }

        // ── Offline limit check ───────────────────────────────────────────────
        val offlineDuration = now - lastContact
        if (offlineDuration > MAX_OFFLINE_MS) {
            val minutesOver = (offlineDuration / 60_000L).toInt()
            Log.w(TAG, "Offline limit exceeded: ${minutesOver}min")
            _gateState.value = SaleGateState(
                reason = SaleBlockedReason.OFFLINE_LIMIT_EXCEEDED,
                minutesBeforeBlock = 0
            )
            return
        }

        // ── OK: compute remaining time ────────────────────────────────────────
        val remaining = ((MAX_OFFLINE_MS - offlineDuration) / 60_000L).toInt()
        _gateState.value = SaleGateState(
            reason = SaleBlockedReason.NONE,
            minutesBeforeBlock = remaining
        )
    }

    /**
     * Synchronous quick check — for use in UI before initiating a ticket sale.
     */
    fun isAllowedToSell(): Boolean = !_gateState.value.isBlocked

    /**
     * Returns the current block reason string for display.
     */
    fun getBlockMessage(): String = when (_gateState.value.reason) {
        SaleBlockedReason.OFFLINE_LIMIT_EXCEEDED ->
            "Terminal hors-ligne depuis plus de 25 minutes. Reconnexion requise."
        SaleBlockedReason.CLOCK_DRIFT_EXCEEDED ->
            "Horloge désynchronisée (${_gateState.value.clockDriftSeconds}s de décalage). " +
            "Reconnectez-vous pour resynchroniser."
        SaleBlockedReason.NO_SERVER_CONTACT ->
            "Aucun contact avec le serveur. Vérifiez la connexion et réessayez."
        SaleBlockedReason.NONE -> ""
    }
}
