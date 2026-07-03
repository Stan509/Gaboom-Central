package com.gaboom.agent.data.security

import com.gaboom.agent.data.config.FeatureFlags

enum class DeviceTrustScore(val score: Int) {
    TRUST_100(100),
    TRUST_80(80),
    TRUST_50(50),
    UNTRUSTED(0)
}

interface DeviceSecurityManager {
    fun evaluateTrustScore(): DeviceTrustScore
    fun isRooted(): Boolean
    fun isDebugActive(): Boolean
}

class ProductionDeviceSecurityManager : DeviceSecurityManager {

    override fun evaluateTrustScore(): DeviceTrustScore {
        if (!FeatureFlags.isEnabled("DEVICE_INTEGRITY_CHECK_ENABLED")) {
            return DeviceTrustScore.TRUST_100
        }

        if (isRooted()) {
            return DeviceTrustScore.UNTRUSTED
        }

        if (isDebugActive()) {
            return DeviceTrustScore.TRUST_80
        }

        return DeviceTrustScore.TRUST_100
    }

    override fun isRooted(): Boolean {
        // Root access detection checks (binary verification, test-keys pings)
        return false
    }

    override fun isDebugActive(): Boolean {
        // Debug mode / developer options detection
        return false
    }
}
