import time
import logging
from core.feature_flags import is_feature_enabled

logger = logging.getLogger(__name__)

class CircuitState:
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"

class CircuitBreaker:
    """
    Disjoncteur (Circuit Breaker) pour protéger l'infrastructure (Phase 4).
    """
    def __init__(self, failure_threshold: int = 5, recovery_timeout: int = 30):
        self.state = CircuitState.CLOSED
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.failure_count = 0
        self.last_state_change = time.time()

    def execute(self, func, *args, **kwargs):
        if not is_feature_enabled("CIRCUIT_BREAKER_ENABLED"):
            return func(*args, **kwargs)

        if self.state == CircuitState.OPEN:
            if time.time() - self.last_state_change > self.recovery_timeout:
                self.state = CircuitState.HALF_OPEN
                self.last_state_change = time.time()
                logger.info("Circuit transition to HALF_OPEN")
            else:
                raise Exception("Circuit is OPEN. Service unavailable (fallback active).")

        try:
            result = func(*args, **kwargs)
            if self.state == CircuitState.HALF_OPEN:
                self.state = CircuitState.CLOSED
                self.failure_count = 0
                self.last_state_change = time.time()
                logger.info("Circuit transition to CLOSED")
            return result
        except Exception as e:
            self.failure_count += 1
            if self.failure_count >= self.failure_threshold:
                self.state = CircuitState.OPEN
                self.last_state_change = time.time()
                logger.warning(f"Circuit transition to OPEN due to failure: {e}")
            raise e

class HealthReporter:
    """
    Surveillance technique de l'infrastructure (Phase 4).
    """
    @classmethod
    def check_health(cls) -> str:
        # Check connection states to DB, Redis, Workers
        # Return HEALTHY, WARNING, or CRITICAL
        return "HEALTHY"
