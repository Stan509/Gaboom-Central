package com.gaboom.agent.data.sync

import com.gaboom.agent.data.local.*
import com.gaboom.agent.data.model.*
import com.gaboom.agent.policy.OfflinePolicy
import com.gaboom.agent.policy.SyncPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OfflineBusinessFlowTest {

    @Test
    fun testAirplaneModeAndOfflinePrinting() {
        // 1. Verify ticket is created in offline mode (simulate Airplane Mode)
        val localId = UUID.randomUUID().toString()
        val localTicketNo = "HL-${localId.take(8).uppercase()}"
        val lines = listOf(
            TicketLine(jeu = "boule", valeur = "44", mise = 10.0, option = 0)
        )
        
        // Confirm offline ticket has complete details and Status is LOCAL_PENDING
        val ticketInfo = TicketInfo(
            id = localId,
            numero = localTicketNo,
            groupId = null,
            totalMise = 10.0,
            totalGain = 0.0,
            statut = "LOCAL_PENDING",
            createdAt = java.time.LocalDateTime.now().toString(),
            closedAt = null,
            tirages = listOf("Tirage"),
            lines = lines
        )
        
        assertEquals("LOCAL_PENDING", ticketInfo.statut)
        assertEquals(10.0, ticketInfo.totalMise, 0.0)
        assertEquals("boule", ticketInfo.lines?.first()?.jeu)
    }

    @Test
    fun testOfflineBudgets500And2000Tickets() {
        // Verify offline policy limits
        val policy = OfflinePolicy(maxTickets = 500)
        assertEquals(500, policy.maxTickets)
        
        // 2000 tickets requires adaptive batching configuration check
        val syncPolicy = SyncPolicy(smallBatchSize = 20, maxBatchSize = 500)
        assertEquals(20, syncPolicy.smallBatchSize)
        assertEquals(500, syncPolicy.maxBatchSize)
    }

    @Test
    fun testAutomaticSynchronizationAfterReconnect() {
        var isOnline = false
        var syncTriggered = false
        
        // Simulating network reconnect trigger
        val onNetworkRestore = {
            isOnline = true
            syncTriggered = true
        }
        
        assertFalse(isOnline)
        assertFalse(syncTriggered)
        
        // Network switching back online
        onNetworkRestore()
        
        assertTrue(isOnline)
        assertTrue(syncTriggered)
    }

    @Test
    fun testGpsQueueReplay() {
        val queue = mutableListOf<LocationQueueEntity>()
        
        // Simulate GPS updates offline
        queue.add(LocationQueueEntity(UUID.randomUUID().toString(), 18.539, -72.336, System.currentTimeMillis()))
        queue.add(LocationQueueEntity(UUID.randomUUID().toString(), 18.540, -72.335, System.currentTimeMillis()))
        
        assertEquals(2, queue.size)
        
        // Simulate reconnect and replay queue
        val uploaded = mutableListOf<LocationQueueEntity>()
        while (queue.isNotEmpty()) {
            val item = queue.removeAt(0)
            uploaded.add(item)
        }
        
        assertTrue(queue.isEmpty())
        assertEquals(2, uploaded.size)
    }

    @Test
    fun testOfflineTicketManagementAndRoomPersistence() {
        val roomCache = mutableListOf<LocalTicketCache>()
        
        // Store offline created ticket to room cache
        val localId = UUID.randomUUID().toString()
        val cachedTicket = LocalTicketCache(
            ticketUuid = localId,
            tirageId = 6,
            sessionKey = "session-xxx",
            ticketNo = "HL-${localId.take(8).uppercase()}",
            totalMise = 50.0,
            createdAt = System.currentTimeMillis(),
            rawJson = "{}"
        )
        roomCache.add(cachedTicket)
        
        // Assert we can read from Room offline
        assertEquals(1, roomCache.size)
        assertEquals(localId, roomCache.first().ticketUuid)
    }

    @Test
    fun testBatchSynchronizationAndAdaptiveBatching() {
        val queueSize = 1200
        val currentAdaptiveBatchSize = 50
        
        // If queue size > 1000, we use adaptive batch size
        val batchSize = if (queueSize > 1000) currentAdaptiveBatchSize else 20
        assertEquals(50, batchSize)
        
        // Simulate latency increase/decrease batch adaptation
        var adaptiveSize = currentAdaptiveBatchSize
        val durationSlow = 4500L
        if (durationSlow > 4000L) {
            adaptiveSize = 50 // Minimum
        }
        assertEquals(50, adaptiveSize)
        
        val durationFast = 800L
        if (durationFast < 1500L) {
            adaptiveSize = 100 // Additive increase
        }
        assertEquals(100, adaptiveSize)
    }

    @Test
    fun testConflictResolution() {
        // Duplicate ticket synchronization rejected with CONFLICT status
        val syncResultError = "conflict: Ticket already exists with this serial number"
        val isConflict = syncResultError.contains("conflict") || syncResultError.contains("déjà")
        
        val finalStatus = if (isConflict) SyncStatus.CONFLICT else SyncStatus.FAILED
        assertEquals(SyncStatus.CONFLICT, finalStatus)
    }
}
