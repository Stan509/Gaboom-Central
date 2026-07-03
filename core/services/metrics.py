import logging
from core.feature_flags import is_feature_enabled

logger = logging.getLogger(__name__)

class MetricsCollector:
    """
    Collecteur de métriques métiers et techniques (Phase 4).
    """

    @classmethod
    def record_business_metric(cls, name: str, value: float) -> None:
        if not is_feature_enabled("METRICS_SYSTEM_ENABLED"):
            return
        # Recording business metric (e.g. tickets/minute, sync success)
        logger.debug(f"Business metric recorded: {name}={value}")

    @classmethod
    def record_technical_metric(cls, name: str, latency_ms: float) -> None:
        if not is_feature_enabled("METRICS_SYSTEM_ENABLED"):
            return
        # Recording latency/timing metric
        logger.debug(f"Technical metric recorded: {name}={latency_ms}ms")
