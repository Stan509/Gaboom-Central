import logging
from core.feature_flags import is_feature_enabled

logger = logging.getLogger(__name__)

class SecuritySeverity:
    INFO = "INFO"
    WARNING = "WARNING"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"

class SecurityAuditService:
    """
    Service d'audit de sécurité et de détection de fraudes (Phase 5).
    """

    @classmethod
    def log_security_event(cls, action: str, severity: str, details: str) -> None:
        if not is_feature_enabled("SECURITY_AUDIT_ENABLED"):
            return

        # Write to SyncAuditLog database or trigger administrator alert email/Slack
        log_message = f"[SECURITY AUDIT] [{severity}] {action} - {details}"
        if severity in (SecuritySeverity.HIGH, SecuritySeverity.CRITICAL):
            logger.critical(log_message)
        else:
            logger.warning(log_message)
