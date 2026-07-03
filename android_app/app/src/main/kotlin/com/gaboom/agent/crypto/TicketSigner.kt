package com.gaboom.agent.crypto

import com.gaboom.agent.policy.SecurityPolicy
import com.gaboom.agent.policy.SignatureAlgorithm

/**
 * Utility to sign ticket payloads.
 * In a full implementation the policy would be injected; for now we use the default policy.
 */
object TicketSigner {
    private val defaultPolicy = SecurityPolicy()

    /**
     * Sign a ticket's raw byte representation.
     * Returns the raw signature bytes.
     */
    fun signTicket(ticketBytes: ByteArray, epoch: Int = 0): ByteArray {
        return SignatureService.sign(ticketBytes, defaultPolicy, epoch)
    }

    /**
     * Verify a ticket signature.
     */
    fun verifyTicket(ticketBytes: ByteArray, signature: ByteArray, epoch: Int = 0): Boolean {
        return SignatureService.verify(ticketBytes, signature, defaultPolicy, epoch)
    }
}
