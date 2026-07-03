package com.gaboom.agent.policy

/**
 * Base interface for all policy objects.
 */
interface Policy {
    val version: Int
}

/**
 * Offline session policy – defines limits for an offline session.
 */
data class OfflinePolicy(
    override val version: Int = 1,
    /** Maximum offline duration in milliseconds (default 25 minutes). */
    val maxDurationMillis: Long = 25L * 60 * 1000,
    /** Maximum number of tickets that can be issued offline. */
    val maxTickets: Int = 500,
    /** Batch size for Merkle‑tree root calculation. */
    val merkleBatchSize: Int = 50
) : Policy

/**
 * Clock related policy – controls key rotation and tamper handling.
 */
data class ClockPolicy(
    override val version: Int = 1,
    /** Key rotation interval in days (default 30 days). */
    val rotationDays: Int = 30,
    /** Overlap period in days when both old and new keys are valid. */
    val overlapDays: Int = 7,
    /** Allowed clock drift in milliseconds. */
    val maxDriftMillis: Long = 5_000L
) : Policy

/**
 * Security policy – parameters for cryptographic algorithms.
 */
enum class SignatureAlgorithm {
    HMAC_SHA256,
    ED25519,
    ECDSA_P256,
    RSA_PSS
}

data class SecurityPolicy(
    override val version: Int = 1,
    val algorithm: SignatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
    /** Length of the HMAC key in bytes. */
    val hmacKeyLength: Int = 32
) : Policy

/**
 * Synchronisation policy – back‑off schedule and retry limits.
 */
data class SyncPolicy(
    override val version: Int = 1,
    /** Base back‑off delay in milliseconds. */
    val baseDelayMillis: Long = 5_000,
    /** Maximum back‑off delay. */
    val maxDelayMillis: Long = 300_000,
    /** Maximum number of retry attempts. */
    val maxRetries: Int = 10
) : Policy

/**
 * Budget policy – multidimensional limits for an offline session.
 */
data class OfflineBudgetPolicy(
    override val version: Int = 1,
    val maxPayments: Int = 100,
    val maxCancellations: Int = 50,
    val maxFinancialAmount: Long = 10_000_00L, // in cents
    val maxSyncOps: Int = 200
) : Policy
