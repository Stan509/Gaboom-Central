package com.gaboom.agent.data.config

/**
 * Registre centralisé des Feature Flags pour l'application Android (Phase 0).
 * Toutes les nouvelles fonctionnalités sont désactivées (false) par défaut.
 */
object FeatureFlags {
    const val OFFLINE_V2 = false
    const val SYNC_ENGINE_V2 = false
    const val QUEUE_ENGINE = false
    const val LOTTERY_CLOCK = false
    const val SQLCIPHER = false
    const val GO_GATEWAY = false
    const val RUST_SIGNATURE = false
    const val DELTA_SYNC = false
    const val PRIORITY_QUEUE = false
    const val ANTI_REPLAY = false
    const val PHASE1_ARCHITECTURE_V2_ENABLED = false
    const val TICKET_IDENTITY_V2_ENABLED = false
    const val CLOCK_AUTHORITY_SCORE_ENABLED = false
    const val OFFLINE_ENGINE_ENABLED = false
    const val OFFLINE_TICKET_SALES_ENABLED = false
    const val SYNC_ENGINE_V2_ENABLED = false
    const val OFFLINE_SIGNATURE_ENABLED = false
    const val SYNC_MANAGER_V2_ENABLED = false
    const val DELTA_SYNC_ENABLED = false
    const val BATCH_SYNC_ENABLED = false
    const val CONFLICT_ENGINE_ENABLED = false
    const val ADAPTIVE_QUEUE_ENABLED = false
    const val RUST_SYNC_VALIDATION_ENABLED = false
    const val KEY_ROTATION_ENABLED = false
    const val RUST_SECURITY_HARDENING_ENABLED = false
    const val DEVICE_INTEGRITY_CHECK_ENABLED = false
    const val SECURITY_AUDIT_ENABLED = false
    const val DISASTER_RECOVERY_ENABLED = false
    const val ADVANCED_ACCESS_CONTROL_ENABLED = false
    const val CENTRAL_LOGGING_ENABLED = false
    const val MONITORING_DASHBOARD_ENABLED = false
    const val ALERT_ENGINE_ENABLED = false
    const val DISTRIBUTED_TRACE_ENABLED = false
    const val CICD_VALIDATION_ENABLED = false
    const val AUTO_BACKUP_ENABLED = false

    /**
     * Vérifie si une fonctionnalité est activée dynamiquement par son nom de clé.
     */
    fun isEnabled(flagKey: String): Boolean {
        return when (flagKey.uppercase()) {
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
