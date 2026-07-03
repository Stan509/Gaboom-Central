package com.gaboom.agent.crypto

import android.util.Base64
import java.security.Key
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Manages cryptographic keys for the offline engine.
 *
 * For now we store keys in memory (a ConcurrentHashMap) keyed by rotation epoch.
 * In production this should be backed by Android Keystore.
 */
object KeyManager {
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private val keys = ConcurrentHashMap<Int, SecretKey>()

    /**
     * Returns the current key according to the provided rotation epoch.
     * If the key does not exist it is generated lazily.
     */
    fun getKeyForEpoch(epoch: Int, keyLengthBytes: Int = 32): SecretKey {
        return keys.computeIfAbsent(epoch) {
            generateKey(keyLengthBytes)
        }
    }

    /**
     * Generates a new random HMAC‑SHA256 key.
     */
    private fun generateKey(length: Int): SecretKey {
        val randomBytes = ByteArray(length)
        java.security.SecureRandom().nextBytes(randomBytes)
        return SecretKeySpec(randomBytes, HMAC_ALGORITHM)
    }

    /**
     * Convenience method to compute an HMAC over the supplied data using the current epoch key.
     */
    fun computeHmac(data: ByteArray, epoch: Int, keyLengthBytes: Int = 32): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(getKeyForEpoch(epoch, keyLengthBytes))
        return mac.doFinal(data)
    }

    /**
     * Returns the current key as a Base64 string – useful for debugging.
     */
    fun getCurrentKeyBase64(epoch: Int, keyLengthBytes: Int = 32): String {
        return Base64.encodeToString(getKeyForEpoch(epoch, keyLengthBytes).encoded, Base64.NO_WRAP)
    }
}
