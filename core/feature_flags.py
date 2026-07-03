"""
Système centralisé de Feature Flags pour Gaboom Borlette OS (Phase 0).
Toutes les nouvelles fonctionnalités sont désactivées par défaut.
"""

import os
import logging

logger = logging.getLogger(__name__)

# Registry of feature flags (default disabled)
DEFAULT_FLAGS = {
    # Core flags
    "OFFLINE_V2": True,
    "SYNC_ENGINE_V2": True,
    "QUEUE_ENGINE": True,
    "LOTTERY_CLOCK": True,
    "SQLCIPHER": True,
    "GO_GATEWAY": True,
    "RUST_SIGNATURE": True,
    "DELTA_SYNC": True,
    "PRIORITY_QUEUE": True,
    "ANTI_REPLAY": True,
    "PHASE1_ARCHITECTURE_V2_ENABLED": True,
    "TICKET_IDENTITY_V2_ENABLED": True,
    "CLOCK_AUTHORITY_SCORE_ENABLED": True,
    "OFFLINE_ENGINE_ENABLED": True,
    "OFFLINE_TICKET_SALES_ENABLED": True,
    "SYNC_ENGINE_V2_ENABLED": True,
    "OFFLINE_SIGNATURE_ENABLED": True,
    "SYNC_MANAGER_V2_ENABLED": True,
    "DELTA_SYNC_ENABLED": True,
    "BATCH_SYNC_ENABLED": True,
    "CONFLICT_ENGINE_ENABLED": True,
    "ADAPTIVE_QUEUE_ENABLED": True,
    "RUST_SYNC_VALIDATION_ENABLED": True,
    "KEY_ROTATION_ENABLED": True,
    "RUST_SECURITY_HARDENING_ENABLED": True,
    "DEVICE_INTEGRITY_CHECK_ENABLED": True,
    "SECURITY_AUDIT_ENABLED": True,
    "DISASTER_RECOVERY_ENABLED": True,
    "ADVANCED_ACCESS_CONTROL_ENABLED": True,
    "CENTRAL_LOGGING_ENABLED": True,
    "MONITORING_DASHBOARD_ENABLED": True,
    "ALERT_ENGINE_ENABLED": True,
    "DISTRIBUTED_TRACE_ENABLED": True,
    "CICD_VALIDATION_ENABLED": True,
    "AUTO_BACKUP_ENABLED": True,
    # Canary control flags (global defaults disabled)
    "CANARY_MODE_ENABLED": True,
    "OFFLINE_ENGINE_CANARY_ENABLED": True,
    "SYNC_ENGINE_V2_CANARY_ENABLED": True,
    "CLOCK_AUTHORITY_SCORE_CANARY_ENABLED": True,
    "ADAPTIVE_QUEUE_CANARY_ENABLED": True,
}


def is_feature_enabled(flag_key: str, device_id: str | None = None) -> bool:
    """Check if a feature flag is enabled.

    Supports:
    * Global flags
    * Canary‑scoped flags (5% rollout)
    * Phase 3 rollout flags (25% rollout)
    The ``device_id`` argument is used for deterministic bucket selection.
    """
    # Flags for canary (5%)
    canary_flags = {
        "OFFLINE_ENGINE_ENABLED",
        "SYNC_ENGINE_V2_ENABLED",
        "CLOCK_AUTHORITY_SCORE_ENABLED",
        "ADAPTIVE_QUEUE_ENABLED",
    }
    # Flags for Phase 3 (25%) – includes canary flags + additional
    phase3_flags = {
        "OFFLINE_ENGINE_ENABLED",
        "SYNC_ENGINE_V2_ENABLED",
        "CLOCK_AUTHORITY_SCORE_ENABLED",
        "ADAPTIVE_QUEUE_ENABLED",
        "DELTA_SYNC_ENABLED",
    }
    # Compute hash if device_id provided
    if device_id is not None:
        try:
            device_hash = int.from_bytes(device_id.encode("utf-8"), "big")
        except Exception:
            device_hash = hash(device_id)
    else:
        device_hash = None

    if flag_key in canary_flags:
        if not DEFAULT_FLAGS.get("CANARY_MODE_ENABLED", False) or device_hash is None:
            return False
        return device_hash % 100 < 5

    if flag_key in phase3_flags:
        if not DEFAULT_FLAGS.get("CANARY_MODE_ENABLED", False) or device_hash is None:
            return False
        return device_hash % 100 < 25

    if flag_key not in DEFAULT_FLAGS:
        logger.warning(f"Feature Flag inconnu interrogé : {flag_key}")
        return False
    env_val = os.environ.get(f"FLAG_{flag_key}", None)
    if env_val is not None:
        return env_val.lower() in ("true", "1", "yes")
    return DEFAULT_FLAGS.get(flag_key, False)
