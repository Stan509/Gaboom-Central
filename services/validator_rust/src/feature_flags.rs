use std::env;

/// Structure de base pour les Feature Flags de la Phase 0.
/// Toutes les fonctionnalités sont désactivées par défaut.
pub struct FeatureFlags;

impl FeatureFlags {
    /// Vérifie si une fonctionnalité est activée
    pub fn is_enabled(flag_key: &str) -> bool {
        let upper_key = flag_key.to_uppercase();
        
        // Liste des flags valides
        let valid_flags = [
            "OFFLINE_V2",
            "SYNC_ENGINE_V2",
            "QUEUE_ENGINE",
            "LOTTERY_CLOCK",
            "SQLCIPHER",
            "GO_GATEWAY",
            "RUST_SIGNATURE",
            "DELTA_SYNC",
            "PRIORITY_QUEUE",
            "ANTI_REPLAY",
            "PHASE1_ARCHITECTURE_V2_ENABLED",
            "TICKET_IDENTITY_V2_ENABLED",
            "CLOCK_AUTHORITY_SCORE_ENABLED",
            "OFFLINE_ENGINE_ENABLED",
            "OFFLINE_TICKET_SALES_ENABLED",
            "SYNC_ENGINE_V2_ENABLED",
            "OFFLINE_SIGNATURE_ENABLED",
            "SYNC_MANAGER_V2_ENABLED",
            "DELTA_SYNC_ENABLED",
            "BATCH_SYNC_ENABLED",
            "CONFLICT_ENGINE_ENABLED",
            "ADAPTIVE_QUEUE_ENABLED",
            "RUST_SYNC_VALIDATION_ENABLED",
            "KEY_ROTATION_ENABLED",
            "RUST_SECURITY_HARDENING_ENABLED",
            "DEVICE_INTEGRITY_CHECK_ENABLED",
            "SECURITY_AUDIT_ENABLED",
            "DISASTER_RECOVERY_ENABLED",
            "ADVANCED_ACCESS_CONTROL_ENABLED",
            "CENTRAL_LOGGING_ENABLED",
            "MONITORING_DASHBOARD_ENABLED",
            "ALERT_ENGINE_ENABLED",
            "DISTRIBUTED_TRACE_ENABLED",
            "CICD_VALIDATION_ENABLED",
            "AUTO_BACKUP_ENABLED",
        ];

        if !valid_flags.contains(&upper_key.as_str()) {
            return false;
        }

        // Lecture depuis l'environnement
        let env_var_name = format!("FLAG_{}", upper_key);
        match env::var(&env_var_name) {
            Ok(val) => {
                let lower = val.to_lowercase();
                lower == "true" || lower == "1" || lower == "yes"
            }
            Err(_) => false,
        }
    }
}
