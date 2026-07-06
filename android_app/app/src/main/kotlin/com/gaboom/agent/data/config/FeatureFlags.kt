package com.gaboom.agent.data.config

/**
 * Registre centralisé des Feature Flags pour l'application Android (Phase 0).
 * Toutes les nouvelles fonctionnalités sont désactivées (false) par défaut.
 */
object FeatureFlags {
    const val OFFLINE_V2 = true
    const val SYNC_ENGINE_V2 = true
    const val QUEUE_ENGINE = true
    const val LOTTERY_CLOCK = true
    const val SQLCIPHER = true
    const val GO_GATEWAY = false
    const val RUST_SIGNATURE = false
    const val DELTA_SYNC = true
    const val PRIORITY_QUEUE = true
    const val ANTI_REPLAY = true
    const val PHASE1_ARCHITECTURE_V2_ENABLED = true
    const val TICKET_IDENTITY_V2_ENABLED = true
    const val CLOCK_AUTHORITY_SCORE_ENABLED = true
    const val OFFLINE_ENGINE_ENABLED = true
    const val OFFLINE_TICKET_SALES_ENABLED = true
    const val SYNC_ENGINE_V2_ENABLED = true
    const val OFFLINE_SIGNATURE_ENABLED = true
    const val SYNC_MANAGER_V2_ENABLED = true
    const val DELTA_SYNC_ENABLED = true
    const val BATCH_SYNC_ENABLED = true
    const val CONFLICT_ENGINE_ENABLED = true
    const val ADAPTIVE_QUEUE_ENABLED = true
    const val RUST_SYNC_VALIDATION_ENABLED = false
    const val KEY_ROTATION_ENABLED = true
    const val RUST_SECURITY_HARDENING_ENABLED = true
    const val DEVICE_INTEGRITY_CHECK_ENABLED = true
    const val SECURITY_AUDIT_ENABLED = true
    const val DISASTER_RECOVERY_ENABLED = true
    const val ADVANCED_ACCESS_CONTROL_ENABLED = true
    const val CENTRAL_LOGGING_ENABLED = true
    const val MONITORING_DASHBOARD_ENABLED = true
    const val ALERT_ENGINE_ENABLED = true
    const val DISTRIBUTED_TRACE_ENABLED = true
    const val OFFLINE_ENGINE_V2 = true
    const val RUST_VALIDATOR = true
    const val CICD_VALIDATION_ENABLED = true
    const val AUTO_BACKUP_ENABLED = true

    /**
     * Vérifie si une fonctionnalité est activée dynamiquement par son nom de clé.
     */
    fun isEnabled(flagKey: String): Boolean {
        return when (flagKey.uppercase()) {
            "OFFLINE_ENGINE_V2" -> OFFLINE_ENGINE_V2
            "RUST_VALIDATOR" -> RUST_VALIDATOR
            "OFFLINE_V2" -> OFFLINE_V2
            "SYNC_ENGINE_V2" -> SYNC_ENGINE_V2
            "QUEUE_ENGINE" -> QUEUE_ENGINE
            "LOTTERY_CLOCK" -> LOTTERY_CLOCK
            "SQLCIPHER" -> SQLCIPHER
            "GO_GATEWAY" -> GO_GATEWAY
            "RUST_SIGNATURE" -> RUST_SIGNATURE
            "DELTA_SYNC" -> DELTA_SYNC
            "PRIORITY_QUEUE" -> PRIORITY_QUEUE
            "ANTI_REPLAY" -> ANTI_REPLAY
            "PHASE1_ARCHITECTURE_V2_ENABLED" -> PHASE1_ARCHITECTURE_V2_ENABLED
            "TICKET_IDENTITY_V2_ENABLED" -> TICKET_IDENTITY_V2_ENABLED
            "CLOCK_AUTHORITY_SCORE_ENABLED" -> CLOCK_AUTHORITY_SCORE_ENABLED
            "OFFLINE_ENGINE_ENABLED" -> OFFLINE_ENGINE_ENABLED
            "OFFLINE_TICKET_SALES_ENABLED" -> OFFLINE_TICKET_SALES_ENABLED
            "SYNC_ENGINE_V2_ENABLED" -> SYNC_ENGINE_V2_ENABLED
            "OFFLINE_SIGNATURE_ENABLED" -> OFFLINE_SIGNATURE_ENABLED
            "SYNC_MANAGER_V2_ENABLED" -> SYNC_MANAGER_V2_ENABLED
            "DELTA_SYNC_ENABLED" -> DELTA_SYNC_ENABLED
            "BATCH_SYNC_ENABLED" -> BATCH_SYNC_ENABLED
            "CONFLICT_ENGINE_ENABLED" -> CONFLICT_ENGINE_ENABLED
            "ADAPTIVE_QUEUE_ENABLED" -> ADAPTIVE_QUEUE_ENABLED
            "RUST_SYNC_VALIDATION_ENABLED" -> RUST_SYNC_VALIDATION_ENABLED
            "KEY_ROTATION_ENABLED" -> KEY_ROTATION_ENABLED
            "RUST_SECURITY_HARDENING_ENABLED" -> RUST_SECURITY_HARDENING_ENABLED
            "DEVICE_INTEGRITY_CHECK_ENABLED" -> DEVICE_INTEGRITY_CHECK_ENABLED
            "SECURITY_AUDIT_ENABLED" -> SECURITY_AUDIT_ENABLED
            "DISASTER_RECOVERY_ENABLED" -> DISASTER_RECOVERY_ENABLED
            "ADVANCED_ACCESS_CONTROL_ENABLED" -> ADVANCED_ACCESS_CONTROL_ENABLED
            "CENTRAL_LOGGING_ENABLED" -> CENTRAL_LOGGING_ENABLED
            "MONITORING_DASHBOARD_ENABLED" -> MONITORING_DASHBOARD_ENABLED
            "ALERT_ENGINE_ENABLED" -> ALERT_ENGINE_ENABLED
            "DISTRIBUTED_TRACE_ENABLED" -> DISTRIBUTED_TRACE_ENABLED
            "CICD_VALIDATION_ENABLED" -> CICD_VALIDATION_ENABLED
            "AUTO_BACKUP_ENABLED" -> AUTO_BACKUP_ENABLED
            else -> false
        }
    }
}
