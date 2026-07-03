package com.gaboom.agent.data.sync

import com.gaboom.agent.data.model.TicketLine
import com.gaboom.agent.data.model.CreatedTicketInfo

/**
 * Interfaces de base pour le moteur hors-ligne (Phase 1).
 * Tous les composants restent inactifs derrière le Feature Flag OFFLINE_V2.
 */

interface OfflineRepository {
    suspend fun saveTicketOffline(tirageId: Int, lines: List<TicketLine>): CreatedTicketInfo
    suspend fun getPendingTicketsCount(): Int
}

interface TransactionJournal {
    fun logAction(action: String, metadata: String)
    fun getHistory(): List<String>
}

interface PendingQueue {
    fun enqueue(ticketId: String)
    fun dequeue(): String?
    fun peek(): String?
}

interface ConflictResolver {
    fun resolve(ticketId: String, reason: String): ConflictResolutionPolicy
}

interface RecoveryManager {
    fun handleRecovery()
    fun isRecovering(): Boolean
}

enum class ConflictResolutionPolicy {
    REFUND,
    REJECT,
    MANUAL_REVIEW
}
