import logging
from core.feature_flags import is_feature_enabled

logger = logging.getLogger(__name__)

class IncidentState:
    OPEN = "OPEN"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    INVESTIGATING = "INVESTIGATING"
    RESOLVED = "RESOLVED"
    CLOSED = "CLOSED"

class AlertManager:
    """
    Gestionnaire d'alertes techniques et d'incidents (Phase 6).
    """

    def __init__(self):
        self.state = IncidentState.CLOSED

    def trigger_alert(self, title: str, details: str, severity: str = "WARNING") -> str:
        if not is_feature_enabled("ALERT_ENGINE_ENABLED"):
            return IncidentState.CLOSED
            
        self.state = IncidentState.OPEN
        logger.warning(f"[ALERT] State: {self.state} | Severity: {severity} | {title} - {details}")
        return self.state

    def update_incident_state(self, new_state: str) -> None:
        self.state = new_state
        logger.info(f"[INCIDENT UPDATE] State transitioned to: {self.state}")

    def get_current_state(self) -> str:
        return self.state
