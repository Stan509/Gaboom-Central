package com.gaboom.agent.data.config

/**
 * Ticket state machine for offline tickets. Defines allowed states and simple transition validation.
 * All operations should be guarded by [OfflineFeatureGuard] before mutating state.
 */
enum class TicketState {
    CREATED,
    VALIDATING,
    VALIDATED,
    SYNC_PENDING,
    SYNCING,
    SYNCED,
    FAILED;

    /**
     * Returns true if a transition from [this] to [newState] is allowed.
     */
    fun canTransitionTo(newState: TicketState): Boolean {
        return when (this) {
            CREATED -> newState == VALIDATING || newState == FAILED
            VALIDATING -> newState == VALIDATED || newState == FAILED
            VALIDATED -> newState == SYNC_PENDING || newState == FAILED
            SYNC_PENDING -> newState == SYNCING || newState == FAILED
            SYNCING -> newState == SYNCED || newState == FAILED
            SYNCED -> false // terminal state
            FAILED -> false // terminal state
        }
    }
}

object TicketStateMachine {
    /**
     * Attempts to transition a ticket state, returning the new state if allowed, otherwise the old state.
     */
    fun transition(current: TicketState, target: TicketState): TicketState {
        return if (current.canTransitionTo(target)) target else current
    }
}
