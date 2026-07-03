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
    "OFFLINE_V2": False,
    "SYNC_ENGINE_V2": False,
    "QUEUE_ENGINE": False,
    "LOTTERY_CLOCK": False,
    "SQLCIPHER": False,
    "GO_GATEWAY": False,
    "RUST_SIGNATURE": False,
    "DELTA_SYNC": False,
    "PRIORITY_QUEUE": False,
    "ANTI_REPLAY": False,
    "PHASE1_ARCHITECTURE_V2_ENABLED": False,
    "TICKET_IDENTITY_V2_ENABLED": False,
    "CLOCK_AUTHORITY_SCORE_ENABLED": False,
    "OFFLINE_ENGINE_ENABLED": False,
    "OFFLINE_TICKET_SALES_ENABLED": False,
    "SYNC_ENGINE_V2_ENABLED": False,
    "OFFLINE_SIGNATURE_ENABLED": False,
    "SYNC_MANAGER_V2_ENABLED": False,
    "DELTA_SYNC_ENABLED": False,
    "BATCH_SYNC_ENABLED": False,
    "CONFLICT_ENGINE_ENABLED": False,
    "ADAPTIVE_QUEUE_ENABLED": False,
    "RUST_SYNC_VALIDATION_ENABLED": False,
    "KEY_ROTATION_ENABLED": False,
    "RUST_SECURITY_HARDENING_ENABLED": False,
    "DEVICE_INTEGRITY_CHECK_ENABLED": False,
    "SECURITY_AUDIT_ENABLED": False,
    "DISASTER_RECOVERY_ENABLED": False,
    "ADVANCED_ACCESS_CONTROL_ENABLED": False,
    "CENTRAL_LOGGING_ENABLED": False,
    "MONITORING_DASHBOARD_ENABLED": False,
    "ALERT_ENGINE_ENABLED": False,
    "DISTRIBUTED_TRACE_ENABLED": False,
    "CICD_VALIDATION_ENABLED": False,
    "AUTO_BACKUP_ENABLED": False,
    # Canary control flags (global defaults disabled)
    "CANARY_MODE_ENABLED": False,
    "OFFLINE_ENGINE_CANARY_ENABLED": False,
    "SYNC_ENGINE_V2_CANARY_ENABLED": False,
    "CLOCK_AUTHORITY_SCORE_CANARY_ENABLED": False,
    "ADAPTIVE_QUEUE_CANARY_ENABLED": False,
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
