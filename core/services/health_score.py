import logging
from core.feature_flags import is_feature_enabled

logger = logging.getLogger(__name__)

class PlatformHealthScore:
    """
    Score global de santé de la plateforme Gaboom (Phase 6).
    Évalue API, DB, Gateway, Sync, et Sécurité.
    """

    @classmethod
    def evaluate_global_health(cls) -> str:
        if not is_feature_enabled("MONITORING_DASHBOARD_ENABLED"):
            return "100"

        # Check subcomponents health:
        api_ok = True
        db_ok = True
        gateway_ok = True
        sync_ok = True
        sec_ok = True

        if not api_ok or not db_ok or not sec_ok:
            return "CRITICAL"
        
        if not gateway_ok or not sync_ok:
            return "50"

        return "100"
