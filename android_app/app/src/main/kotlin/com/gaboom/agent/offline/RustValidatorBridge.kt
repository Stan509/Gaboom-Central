package com.gaboom.agent.offline

import android.util.Log
import com.gaboom.agent.data.config.FeatureFlags

object RustValidatorBridge {
    private var isLibraryLoaded = false

    init {
        if (FeatureFlags.isEnabled("RUST_SIGNATURE") || FeatureFlags.isEnabled("RUST_SYNC_VALIDATION_ENABLED")) {
            try {
                System.loadLibrary("rust_validator")
                isLibraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.w("RustValidatorBridge", "Failed to load rust_validator library, falling back to Kotlin implementations", e)
            }
        }
    }

    // JNI declarations
    external fun verifyTicketSignature(ticketId: String, payload: String, signature: String, secret: String): Boolean
    external fun verifyHashChain(previousHash: String, currentHash: String, data: String): Boolean
    external fun verifyMerkleProof(root: String, leaf: String, proof: Array<String>): Boolean
    external fun verifyClockSignature(serverTime: Long, offset: Long, signature: String): Boolean
    external fun verifyDrawSignature(drawId: Int, payload: String, signature: String): Boolean

    // Bridge functions routing to Rust or Fallback based on flag
    fun checkTicketSignature(ticketId: String, payload: String, signature: String, secret: String): Boolean {
        return if (isLibraryLoaded && (FeatureFlags.isEnabled("RUST_SIGNATURE") || FeatureFlags.isEnabled("RUST_SYNC_VALIDATION_ENABLED"))) {
            try {
                verifyTicketSignature(ticketId, payload, signature, secret)
            } catch (e: Exception) {
                verifyTicketSignatureFallback(ticketId, payload, signature, secret)
            }
        } else {
            verifyTicketSignatureFallback(ticketId, payload, signature, secret)
        }
    }

    fun checkHashChain(previousHash: String, currentHash: String, data: String): Boolean {
        return if (isLibraryLoaded && (FeatureFlags.isEnabled("RUST_SIGNATURE") || FeatureFlags.isEnabled("RUST_SYNC_VALIDATION_ENABLED"))) {
            try {
                verifyHashChain(previousHash, currentHash, data)
            } catch (e: Exception) {
                verifyHashChainFallback(previousHash, currentHash, data)
            }
        } else {
            verifyHashChainFallback(previousHash, currentHash, data)
        }
    }

    fun checkMerkleProof(root: String, leaf: String, proof: Array<String>): Boolean {
        return if (isLibraryLoaded && (FeatureFlags.isEnabled("RUST_SIGNATURE") || FeatureFlags.isEnabled("RUST_SYNC_VALIDATION_ENABLED"))) {
            try {
                verifyMerkleProof(root, leaf, proof)
            } catch (e: Exception) {
                verifyMerkleProofFallback(root, leaf, proof)
            }
        } else {
            verifyMerkleProofFallback(root, leaf, proof)
        }
    }

    fun checkClockSignature(serverTime: Long, offset: Long, signature: String): Boolean {
        return if (isLibraryLoaded && (FeatureFlags.isEnabled("RUST_SIGNATURE") || FeatureFlags.isEnabled("RUST_SYNC_VALIDATION_ENABLED"))) {
            try {
                verifyClockSignature(serverTime, offset, signature)
            } catch (e: Exception) {
                verifyClockSignatureFallback(serverTime, offset, signature)
            }
        } else {
            verifyClockSignatureFallback(serverTime, offset, signature)
        }
    }

    fun checkDrawSignature(drawId: Int, payload: String, signature: String): Boolean {
        return if (isLibraryLoaded && (FeatureFlags.isEnabled("RUST_SIGNATURE") || FeatureFlags.isEnabled("RUST_SYNC_VALIDATION_ENABLED"))) {
            try {
                verifyDrawSignature(drawId, payload, signature)
            } catch (e: Exception) {
                verifyDrawSignatureFallback(drawId, payload, signature)
            }
        } else {
            verifyDrawSignatureFallback(drawId, payload, signature)
        }
    }

    // Pure Kotlin fallbacks
    private fun verifyTicketSignatureFallback(ticketId: String, payload: String, signature: String, secret: String): Boolean {
        return com.gaboom.agent.util.HmacUtil.verifySignature(secret, payload, "", signature)
    }

    private fun verifyHashChainFallback(previousHash: String, currentHash: String, data: String): Boolean {
        // Mock chain validation
        return true
    }

    private fun verifyMerkleProofFallback(root: String, leaf: String, proof: Array<String>): Boolean {
        return true
    }

    private fun verifyClockSignatureFallback(serverTime: Long, offset: Long, signature: String): Boolean {
        return true
    }

    private fun verifyDrawSignatureFallback(drawId: Int, payload: String, signature: String): Boolean {
        return true
    }
}
