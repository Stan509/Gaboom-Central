package com.gaboom.agent.util

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.google.gson.Gson
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC utility for offline ticket signing (Phase I-A Anti-Tamper)
 * 
 * Signature: HMAC_SHA256(deviceSecret, payloadJson + sessionKey)
 */
object HmacUtil {
    
    private const val HMAC_ALGORITHM = "HmacSHA256"
    
    /**
     * Generate HMAC-SHA256 signature for offline ticket payload.
     * 
     * @param deviceSecret The device secret from server registration
     * @param payloadJson The JSON payload (must be consistently serialized)
     * @param sessionKey The tirage session key
     * @return Hex-encoded HMAC signature
     */
    fun signPayload(deviceSecret: String, payloadJson: String, sessionKey: String): String {
        val canonical = canonicalizeJson(payloadJson)
        val message = "$canonical$sessionKey"
        val keySpec = SecretKeySpec(deviceSecret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(keySpec)
        val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return bytes.toHex()
    }
    
    /**
     * Verify HMAC signature (for testing/validation).
     */
    fun verifySignature(deviceSecret: String, payloadJson: String, sessionKey: String, signature: String): Boolean {
        val expected = signPayload(deviceSecret, payloadJson, sessionKey)
        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            signature.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Canonicalize JSON string by sorting all keys alphabetically recursively.
     */
    fun canonicalizeJson(json: String): String {
        return try {
            val element = JsonParser.parseString(json)
            canonicalize(element)
        } catch (e: Exception) {
            json
        }
    }

    private fun canonicalize(element: JsonElement): String {
        return when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val sortedKeys = obj.keySet().sorted()
                val sb = StringBuilder()
                sb.append("{")
                sortedKeys.forEachIndexed { index, key ->
                    if (index > 0) sb.append(",")
                    sb.append("\"").append(key).append("\":")
                    sb.append(canonicalize(obj.get(key)))
                }
                sb.append("}")
                sb.toString()
            }
            element.isJsonArray -> {
                val arr = element.asJsonArray
                val sb = StringBuilder()
                sb.append("[")
                for (i in 0 until arr.size()) {
                    if (i > 0) sb.append(",")
                    sb.append(canonicalize(arr.get(i)))
                }
                sb.append("]")
                sb.toString()
            }
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                when {
                    prim.isString -> {
                        Gson().toJson(prim.asString)
                    }
                    prim.isNumber -> {
                        val num = prim.asNumber
                        val str = num.toString()
                        if (str.endsWith(".0")) {
                            str
                        } else if (str.contains(".") || str.contains("e") || str.contains("E")) {
                            str
                        } else {
                            str
                        }
                    }
                    prim.isBoolean -> prim.asBoolean.toString()
                    else -> prim.toString()
                }
            }
            element.isJsonNull -> "null"
            else -> "null"
        }
    }
    
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
