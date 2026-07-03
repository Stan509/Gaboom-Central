package com.gaboom.agent.data.config

/**
 * Helper wrapper to check if the Offline‑First engine is enabled.
 * All offline‑first components should call this before performing any work.
 */
object OfflineFeatureGuard {
    /**
     * Returns true only when the feature flag `OFFLINE_ENGINE_ENABLED` is set to true.
     * The flag is defined in `FeatureFlags.kt` and defaults to false.
     */
    fun isEnabled(): Boolean = FeatureFlags.isEnabled("OFFLINE_ENGINE_ENABLED")
}
