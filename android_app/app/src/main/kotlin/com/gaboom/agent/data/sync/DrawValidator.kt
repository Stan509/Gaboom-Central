package com.gaboom.agent.data.sync

import com.gaboom.agent.data.local.DrawCacheEntity
import com.gaboom.agent.offline.RustValidatorBridge
import java.security.MessageDigest

object DrawValidator {
    
    fun validateDraw(draw: DrawCacheEntity): Boolean {
        // 1. Expiration Check
        if (draw.expiration > 0 && draw.expiration < System.currentTimeMillis()) {
            return false // Reject expired draws
        }

        // 2. Checksum/Hash Validation
        val content = "${draw.id}|${draw.nom}|${draw.type}|${draw.heureOuverture}|${draw.heureFermeture}|${draw.heureTirage}|${draw.etat}|${draw.version}"
        val computedHash = sha256(content)
        if (draw.checksum.isNotEmpty() && draw.checksum != computedHash) {
            return false // Reject corrupted/tampered draw
        }

        // 3. Signature Verification via JNI/Rust bridge
        if (draw.signature.isNotEmpty()) {
            val isSigValid = RustValidatorBridge.checkDrawSignature(draw.id, content, draw.signature)
            if (!isSigValid) {
                return false // Reject invalid signatures
            }
        }

        return true
    }

    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
