package com.gaboom.agent.replication

/**
 * Replication state for an entity.
 * Used for future distributed sync implementations.
 */
enum class ReplicationState {
    LOCAL, // Entity exists only locally
    PENDING, // Pending replication
    SYNCING, // Currently being synced
    SYNCED, // Successfully replicated
    FAILED, // Replication failed
    CONFLICT // Conflict detected during replication
}
