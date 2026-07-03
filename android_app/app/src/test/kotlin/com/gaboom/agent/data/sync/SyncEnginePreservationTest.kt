package com.gaboom.agent.data.sync

import org.junit.Assert.assertFalse
import org.junit.Test
import com.gaboom.agent.data.config.FeatureFlags

/**
 * Suite de tests de préservation de l'architecture Android (Phase 0).
 * Garantit que la structure des classes de configuration et les APIs locales restent rétrocompatibles.
 */
class SyncEnginePreservationTest {

    @Test
    fun testFeatureFlagsAreDisabledByDefault() {
        // Vérifie que les flags critiques sont désactivés
        assertFalse(FeatureFlags.isEnabled("OFFLINE_V2"))
        assertFalse(FeatureFlags.isEnabled("SYNC_ENGINE_V2"))
        assertFalse(FeatureFlags.isEnabled("QUEUE_ENGINE"))
        assertFalse(FeatureFlags.isEnabled("SQLCIPHER"))
    }
}
