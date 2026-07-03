package com.gaboom.agent.crypto

import com.gaboom.agent.policy.SecurityPolicy
import com.gaboom.agent.policy.SignatureAlgorithm
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Service that abstracts signing and verification operations.
 * Currently only HMAC‑SHA256 is fully implemented; other algorithms are stubs
 * that can be filled once the Rust cryptographic layer is integrated.
 */
object SignatureService {
    /**
     * Sign the given data using the algorithm defined in the supplied [SecurityPolicy].
     * Returns the raw signature bytes.
     */
    fun sign(data: ByteArray, policy: SecurityPolicy, epoch: Int = 0): ByteArray {
        return when (policy.algorithm) {
            SignatureAlgorithm.HMAC_SHA256 -> {
                // Use the in‑memory KeyManager for HMAC keys
                val mac: Mac = Mac.getInstance("HmacSHA256")
                mac.init(KeyManager.getKeyForEpoch(epoch, policy.hmacKeyLength))
                mac.doFinal(data)
            }
            // Stub implementations – in production these would delegate to native Rust libs
            SignatureAlgorithm.ED25519 -> {
                // Placeholder: simple SHA‑256 hash of data (not a real signature)
                MessageDigest.getInstance("SHA-256").digest(data)
            }
            SignatureAlgorithm.ECDSA_P256 -> {
                MessageDigest.getInstance("SHA-256").digest(data)
            }
            SignatureAlgorithm.RSA_PSS -> {
                MessageDigest.getInstance("SHA-256").digest(data)
            }
        }
    }

    /**
     * Verify a signature against the original data using the same [SecurityPolicy].
     * For HMAC this recomputes the MAC and does a constant‑time comparison.
     * Stub algorithms perform a simple hash equality check.
     */
    fun verify(data: ByteArray, signature: ByteArray, policy: SecurityPolicy, epoch: Int = 0): Boolean {
        return when (policy.algorithm) {
            SignatureAlgorithm.HMAC_SHA256 -> {
                val expected = sign(data, policy, epoch)
                // constant‑time comparison
                var result = 0
                for (i in expected.indices) {
                    result = result or (expected[i].toInt() xor signature[i].toInt())
                }
                result == 0 && expected.size == signature.size
            }
            else -> {
                // For stub algorithms we just compare the SHA‑256 hash placeholder
                val expected = sign(data, policy, epoch)
                expected.contentEquals(signature)
            }
        }
    }
}
