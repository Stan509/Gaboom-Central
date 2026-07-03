import time
import uuid
import logging
from core.feature_flags import is_feature_enabled

logger = logging.getLogger(__name__)

class CentralLogService:
    """
    Service de collecte centralisée des logs (Phase 6).
    """

    @classmethod
    def log_event(cls, event_name: str, device_id: str, data: dict, level: str = "INFO") -> None:
        if not is_feature_enabled("CENTRAL_LOGGING_ENABLED"):
            return

        correlation_id = str(uuid.uuid4())
        log_payload = {
            "event_name": event_name,
            "correlation_id": correlation_id,
            "request_id": str(uuid.uuid4())[:8],
            "device_id": device_id,
            "timestamp": int(time.time() * 1000),
            "level": level,
            "data": data
        }
        
        log_msg = f"[CENTRAL LOG] {log_payload}"
        if level == "ERROR":
            logger.error(log_msg)
        else:
            logger.info(log_msg)
