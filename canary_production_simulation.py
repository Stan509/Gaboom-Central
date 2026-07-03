import os
import random
import logging
import threading
import time
from collections import defaultdict

# Ensure canary mode is active
os.environ['FLAG_CANARY_MODE_ENABLED'] = 'true'

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')

# Metrics storage (simple in‑memory aggregation)
metrics = defaultdict(list)

# Device selection – deterministic 5% group based on hash
TOTAL_DEVICES = 100
CANARY_GROUP = []
for i in range(TOTAL_DEVICES):
    device_id = f"device_{i:03d}"
    device_hash = int.from_bytes(device_id.encode('utf-8'), 'big')
    if device_hash % 100 < 5:
        CANARY_GROUP.append(device_id)

logger.info(f"Canary group size: {len(CANARY_GROUP)} (expected ~5) – devices: {CANARY_GROUP}")

# Simulation parameters
OPS_PER_MINUTE = 1000  # total ticket operations across canary devices
OPS_PER_SECOND = OPS_PER_MINUTE / 60.0
INTERVAL = 1.0 / OPS_PER_SECOND

def simulate_device(device_id: str):
    """Simulate a stream of ticket operations for a single device.

    For simplicity we log a few representative metrics: ticket failure,
    latency, sync conflict and Android crash. Real business logic is omitted.
    """
    end_time = time.time() + 60  # run for one minute per device
    while time.time() < end_time:
        # Ticket failure (5% chance)
        if random.random() < 0.05:
            metrics['ticket_failure'].append(1)
        else:
            metrics['ticket_success'].append(1)
        # Latency (normal around 150 ms)
        latency = max(0, random.gauss(150, 30))
        metrics['latency_ms'].append(latency)
        # Sync conflict (2% chance)
        if random.random() < 0.02:
            metrics['sync_conflict'].append(1)
        # Android crash (1% chance)
        if random.random() < 0.01:
            metrics['android_crash'].append(1)
        time.sleep(INTERVAL)

# Launch simulation threads (one per canary device)
threads = []
for device_id in CANARY_GROUP:
    t = threading.Thread(target=simulate_device, args=(device_id,))
    t.start()
    threads.append(t)

# Wait for all threads to complete
for t in threads:
    t.join()

# Compute aggregate results
total_ops = sum(len(v) for v in metrics.values())
failure_rate = sum(metrics['ticket_failure']) / (sum(metrics['ticket_success']) + sum(metrics['ticket_failure'])) if (metrics['ticket_success'] or metrics['ticket_failure']) else 0
avg_latency = sum(metrics['latency_ms']) / len(metrics['latency_ms']) if metrics['latency_ms'] else 0
sync_conflict_rate = sum(metrics['sync_conflict']) / total_ops
crash_rate = sum(metrics['android_crash']) / total_ops

logger.info("Canary production simulation results:")
logger.info(f"- Ticket failure rate: {failure_rate:.2%}")
logger.info(f"- Average latency: {avg_latency:.1f} ms")
logger.info(f"- Sync conflict rate: {sync_conflict_rate:.2%}")
logger.info(f"- Android crash rate: {crash_rate:.2%}")

# Decision – auto‑rollback if any threshold exceeded
ROLLBACK = False
reasons = []
if failure_rate > 0.03:
    ROLLBACK = True
    reasons.append(f"failure rate {failure_rate:.2%} > 3%")
if avg_latency > 300:
    ROLLBACK = True
    reasons.append(f"avg latency {avg_latency:.1f} ms > 300 ms")
if sync_conflict_rate > 0.05:
    ROLLBACK = True
    reasons.append(f"sync conflict rate {sync_conflict_rate:.2%} > 5%")
if crash_rate > 0.02:
    ROLLBACK = True
    reasons.append(f"crash rate {crash_rate:.2%} > 2%")

if ROLLBACK:
    logger.warning("AUTO‑ROLLBACK triggered: " + ", ".join(reasons))
    print("ROLLBACK_TRIGGERED", ", ".join(reasons))
else:
    logger.info("Canary rollout passed all thresholds – proceeding.")
    print("CANARY_SUCCESS")
