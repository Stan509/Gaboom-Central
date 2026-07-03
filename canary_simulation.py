import os
import random
import logging
class MetricsCollector:
    @classmethod
    def record_business_metric(cls, name: str, value: float) -> None:
        logger.debug(f"Business metric recorded: {name}={value}")

    @classmethod
    def record_technical_metric(cls, name: str, latency_ms: float) -> None:
        logger.debug(f"Technical metric recorded: {name}={latency_ms}ms")


logger = logging.getLogger(__name__)

# Enable metrics system via env var (simulating canary rollout activation)
os.environ['FLAG_METRICS_SYSTEM_ENABLED'] = 'true'

# Simulation parameters
TOTAL_POS_DEVICES = 100
CANARY_PERCENT = 5  # 5% rollout
CANARY_DEVICES = int(TOTAL_POS_DEVICES * CANARY_PERCENT / 100)

# Thresholds
ERROR_RATE_THRESHOLD = 0.01  # 1%
LATENCY_THRESHOLD_MS = 300
SYNC_CONFLICT_MULTIPLIER = 2  # >2x baseline
CRASH_RATE_THRESHOLD = 0.01  # 1%

# Baseline metrics (from historical data, placeholders)
BASELINE_FAILURE_RATE = 0.005  # 0.5%
BASELINE_LATENCY_MS = 150  # avg
BASELINE_SYNC_CONFLICT_RATE = 0.01  # 1%
BASELINE_CRASH_RATE = 0.005  # 0.5%

# Simulate metrics for canary devices
failures = 0
latencies = []
conflicts = 0
crashes = 0

for _ in range(CANARY_DEVICES):
    # Simulate ticket failure (10% chance)
    if random.random() < 0.10:
        failures += 1
        MetricsCollector.record_business_metric('ticket_failure', 1)
    # Simulate latency (normal distribution around BASELINE_LATENCY_MS)
    latency = max(0, random.gauss(BASELINE_LATENCY_MS, 50))
    latencies.append(latency)
    MetricsCollector.record_technical_metric('endpoint_latency', latency)
    # Simulate sync conflict (5% chance)
    if random.random() < 0.05:
        conflicts += 1
        MetricsCollector.record_business_metric('sync_conflict', 1)
    # Simulate Android crash (2% chance)
    if random.random() < 0.02:
        crashes += 1
        MetricsCollector.record_business_metric('android_crash', 1)

# Compute rates
error_rate = failures / CANARY_DEVICES
avg_latency = sum(latencies) / len(latencies) if latencies else 0
sync_conflict_rate = conflicts / CANARY_DEVICES
crash_rate = crashes / CANARY_DEVICES

logger.info(f"Canary rollout metrics:\n" 
            f"- Ticket failure rate: {error_rate:.2%}\n" 
            f"- Average latency: {avg_latency:.1f}ms\n" 
            f"- Sync conflict rate: {sync_conflict_rate:.2%}\n" 
            f"- Android crash rate: {crash_rate:.2%}")

# Evaluate rollback conditions
rollback = False
reasons = []
if error_rate > ERROR_RATE_THRESHOLD:
    rollback = True
    reasons.append(f"Error rate {error_rate:.2%} exceeds {ERROR_RATE_THRESHOLD:.2%}")
if avg_latency > LATENCY_THRESHOLD_MS:
    rollback = True
    reasons.append(f"Avg latency {avg_latency:.1f}ms exceeds {LATENCY_THRESHOLD_MS}ms")
if sync_conflict_rate > (BASELINE_SYNC_CONFLICT_RATE * SYNC_CONFLICT_MULTIPLIER):
    rollback = True
    reasons.append(f"Sync conflict rate {sync_conflict_rate:.2%} exceeds {BASELINE_SYNC_CONFLICT_RATE * SYNC_CONFLICT_MULTIPLIER:.2%} (baseline x{SYNC_CONFLICT_MULTIPLIER})")
if crash_rate > CRASH_RATE_THRESHOLD:
    rollback = True
    reasons.append(f"Crash rate {crash_rate:.2%} exceeds {CRASH_RATE_THRESHOLD:.2%}")

if rollback:
    logger.warning("Rollback triggered due to: " + ", ".join(reasons))
    print("ROLLBACK_TRIGGERED", ", ".join(reasons))
else:
    logger.info("Canary rollout passed all thresholds – proceeding.")
    print("CANARY_SUCCESS")
