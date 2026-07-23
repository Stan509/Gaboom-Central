package com.gaboom.agent.data.repository

import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.local.PendingTicketDao
import com.gaboom.agent.data.local.PendingTicketEntity
import com.gaboom.agent.data.local.SyncStatus
import com.gaboom.agent.data.local.LocalTicketCacheDao
import com.gaboom.agent.data.local.LocalTicketCache
import com.gaboom.agent.data.model.*
import com.gaboom.agent.data.config.AgentConfigDataStore
import com.gaboom.agent.data.sync.OfflineCoordinator
import com.gaboom.agent.data.sync.SyncManager
import com.gaboom.agent.util.HmacUtil
import com.google.gson.Gson
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TicketRepository @Inject constructor(
    private val apiService: AgentApiService,
    private val pendingTicketDao: PendingTicketDao,
    private val localTicketCacheDao: LocalTicketCacheDao,
    private val agentConfigDataStore: AgentConfigDataStore,
    private val offlineCoordinator: OfflineCoordinator,
    private val syncManagerProvider: Provider<SyncManager>,
    private val gson: Gson
) {
    /**
     * Phase 3 — Local-First.
     *
     * Always creates the ticket locally first. The ticket number is allocated from the
     * device range (CB-YYYY-N). The ticket is immediately available for printing.
     * Background upload to the server happens asynchronously via [SyncManager].
     */
    suspend fun createTicket(
        request: TicketCreateRequest,
        tirageId: Int,
        apiLines: List<TicketLine>
    ): Response<TicketCreateResponse> {
        return createTicketLocalFirst(
            tirageIds = listOf(tirageId),
            entries = apiLines.map { line ->
                MultiTicketEntry(
                    game = line.jeu,
                    number = line.valeur,
                    stake = line.mise,
                    gratuit = line.gratuit,
                    option = line.option
                )
            },
            sessionKey = ""
        ).let { multiResponse ->
            val firstTicket = multiResponse.tickets?.firstOrNull()
            if (firstTicket != null) {
                retrofit2.Response.success(
                    TicketCreateResponse(
                        success = true,
                        ticket = TicketInfo(
                            id = firstTicket.ticketId,
                            numero = firstTicket.ticketNo,
                            groupId = firstTicket.groupId,
                            totalMise = firstTicket.totalMise,
                            totalGain = 0.0,
                            statut = "pending",
                            createdAt = java.time.LocalDateTime.now().toString(),
                            closedAt = null,
                            tirages = listOf("Tirage"),
                            lines = apiLines.map { line ->
                                TicketLine(
                                    jeu = line.jeu, valeur = line.valeur,
                                    mise = line.mise, potentielGain = 0.0,
                                    gratuit = line.gratuit, option = line.option
                                )
                            },
                            signature = firstTicket.signature,
                            hash = firstTicket.hash
                        ),
                        error = null
                    )
                )
            } else {
                retrofit2.Response.success(
                    TicketCreateResponse(success = false, ticket = null, error = "Création locale échouée")
                )
            }
        }
    }

    /**
     * Phase 3 — Local-First unified ticket creation.
     *
     * - Allocates an official CB-YYYY-N number from the device range.
     * - Stores to local DB immediately (ticket is usable without server response).
     * - Enqueues for background upload via [SyncManager].
     * - Legacy HL- tickets in DB are preserved and will be uploaded if server accepts them.
     */
    suspend fun createMultiTicket(
        request: MultiTicketCreateRequest
    ): Response<MultiTicketCreateResponse> {
        val result = createTicketLocalFirst(
            tirageIds = request.tirageIds,
            entries = request.entries,
            sessionKey = request.sessionKey
        )
        return retrofit2.Response.success(result)
    }

    /**
     * Core local-first ticket factory. Called by both [createTicket] and [createMultiTicket].
     */
    internal suspend fun createTicketLocalFirst(
        tirageIds: List<Int>,
        entries: List<MultiTicketEntry>,
        sessionKey: String?
    ): MultiTicketCreateResponse {
        val groupId = UUID.randomUUID().toString()
        val now = com.gaboom.agent.data.clock.SecuredClock.now()
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

        val cachedTirages = agentConfigDataStore.getCachedTirages()

        val ticketsList = tirageIds.map { tirageId ->
            val localId = UUID.randomUUID().toString()

            // Allocate official number from server-allocated device range
            val seqNumber = agentConfigDataStore.getAndIncrementTicketNumber()
            val localTicketNo = seqNumber.toString()

            // Try to find the correct session key for this specific draw
            val correctSessionKey = cachedTirages.find { it.id == tirageId }?.sessionKey ?: sessionKey ?: ""

            val multiRequest = MultiTicketCreateRequest(
                tirageIds = listOf(tirageId),
                entries = entries,
                sessionKey = correctSessionKey,
                createdAt = now,
                clientTime = now
            )
            val payloadJson = gson.toJson(multiRequest)
            val linesSummary = entries.take(5).joinToString(", ") { "${it.game}:${it.number}" } +
                if (entries.size > 5) "..." else ""

            val deviceCreds = agentConfigDataStore.getDeviceCredentials()
            val hmacSignature = if (deviceCreds != null) {
                HmacUtil.signPayload(
                    deviceSecret = deviceCreds.deviceSecret,
                    payloadJson = payloadJson,
                    sessionKey = correctSessionKey
                )
            } else null

            // Find draw name and result time for display
            val tirageInfo = cachedTirages.find { it.id == tirageId }
            val tirageNom = if (tirageInfo != null && tirageInfo.heureTirage.isNotBlank()) {
                "${tirageInfo.nom} (${tirageInfo.heureTirage})"
            } else tirageInfo?.nom ?: "Tirage"

            val batchLabel = cachedTirages.filter { it.id in tirageIds }.map { t ->
                if (t.heureTirage.isNotBlank()) "${t.nom} (${t.heureTirage})" else t.nom
            }.joinToString(", ").ifEmpty { tirageNom }

            // Persist to pending queue (for background upload)
            val pendingTicket = PendingTicketEntity(
                id = localId,
                payloadJson = payloadJson,
                tirageIds = tirageIds.joinToString(","),
                tirageId = tirageId,
                sessionKey = correctSessionKey,
                totalMise = entries.sumOf { it.stake },
                linesSummary = linesSummary,
                syncStatus = SyncStatus.LOCAL_PENDING,
                hmacSignature = hmacSignature,
                batchId = groupId,
                batchLabel = batchLabel,
                localTicketNo = localTicketNo
            )
            pendingTicketDao.insert(pendingTicket)

            // Build display item
            val ticketItem = TicketListItem(
                id = localId,
                numero = localTicketNo,
                groupId = groupId,
                tirageId = tirageId,
                tirageNom = tirageNom,
                tirageOpen = true,
                status = "pending",
                numBets = entries.size,
                totalMise = entries.sumOf { it.stake },
                totalGainDu = 0.0,
                totalGainPaye = 0.0,
                isWinner = false,
                isPaid = false,
                canPay = false,
                canVoid = true,
                canReprint = true,
                createdAt = java.time.LocalDateTime.now().toString(),
                ageMinutes = 0.0
            )

            // Persist to local read cache
            localTicketCacheDao.insert(
                LocalTicketCache(
                    ticketUuid = localId,
                    tirageId = tirageId,
                    sessionKey = correctSessionKey,
                    ticketNo = localTicketNo,
                    totalMise = entries.sumOf { it.stake },
                    createdAt = now,
                    rawJson = gson.toJson(ticketItem),
                    uploadStatus = "PENDING"
                )
            )

            MultiTicketInfo(
                tirageId = tirageId,
                tirageNom = tirageNom,
                ticketId = localId,
                ticketNo = localTicketNo,
                groupId = groupId,
                totalMise = entries.sumOf { it.stake },
                lines = null,
                signature = hmacSignature,
                hash = hmacSignature
            )
        }

        // Trigger background upload (non-blocking)
        syncManagerProvider.get().syncPendingTickets()

        return MultiTicketCreateResponse(
            success = true,
            groupId = groupId,
            tickets = ticketsList,
            failed = null,
            error = null
        )
    }

    suspend fun listTickets(limit: Int, offset: Int): Response<TicketListResponse> {
        return offlineCoordinator.execute(
            networkCall = {
                val response = apiService.listTickets(limit = limit, offset = offset)
                if (response.isSuccessful && response.body()?.success == true) {
                    val tickets = response.body()?.tickets ?: emptyList()
                    val caches = tickets.map { ticket ->
                        LocalTicketCache(
                            ticketUuid = ticket.id,
                            tirageId = ticket.tirageId ?: 0,
                            sessionKey = "",
                            ticketNo = ticket.numero,
                            totalMise = ticket.totalMise,
                            createdAt = try {
                                java.time.OffsetDateTime.parse(ticket.createdAt).toInstant().toEpochMilli()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            },
                            rawJson = gson.toJson(ticket)
                        )
                    }
                    localTicketCacheDao.insertAll(caches)
                }
                response
            },
            offlineFallback = {
                val cached = localTicketCacheDao.getAllTickets()
                val listItems = cached.mapNotNull { cache ->
                    if (cache.rawJson != null) {
                        try {
                            gson.fromJson(cache.rawJson, TicketListItem::class.java)
                        } catch (e: Exception) {
                            val info = gson.fromJson(cache.rawJson, TicketInfo::class.java)
                            TicketListItem(
                                id = info.id,
                                numero = info.numero,
                                groupId = info.groupId,
                                tirageId = cache.tirageId,
                                tirageNom = "Tirage",
                                tirageOpen = true,
                                status = info.statut,
                                numBets = info.lines?.size ?: 0,
                                totalMise = info.totalMise,
                                totalGainDu = info.totalGain ?: 0.0,
                                totalGainPaye = 0.0,
                                isWinner = (info.totalGain ?: 0.0) > 0.0,
                                isPaid = info.statut.lowercase() == "paid" || info.statut.lowercase() == "paye",
                                canPay = false,
                                canVoid = false,
                                canReprint = true,
                                createdAt = info.createdAt,
                                ageMinutes = 0.0
                            )
                        }
                    } else null
                }
                TicketListResponse(
                    success = true,
                    tickets = listItems,
                    total = listItems.size,
                    limit = limit,
                    offset = offset,
                    error = null
                )
            }
        )
    }

    suspend fun searchTickets(query: String): Response<TicketSearchResponse> {
        return offlineCoordinator.execute(
            networkCall = {
                val response = apiService.searchTickets(query)
                if (response.isSuccessful && response.body()?.success == true) {
                    val tickets = response.body()?.tickets ?: emptyList()
                    val caches = tickets.map { ticket ->
                        LocalTicketCache(
                            ticketUuid = ticket.id,
                            tirageId = 0,
                            sessionKey = "",
                            ticketNo = ticket.ticketNo,
                            totalMise = ticket.totalMise,
                            createdAt = System.currentTimeMillis(),
                            rawJson = gson.toJson(ticket)
                        )
                    }
                    localTicketCacheDao.insertAll(caches)
                }
                response
            },
            offlineFallback = {
                val cached = localTicketCacheDao.getAllTickets()
                val queryLower = query.lowercase()
                val listItems = cached.mapNotNull { cache ->
                    if (cache.rawJson != null) {
                        try {
                            gson.fromJson(cache.rawJson, TicketListItem::class.java)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }.filter {
                    it.numero.lowercase().contains(queryLower) || it.id.lowercase().contains(queryLower)
                }
                
                val results = listItems.map { item ->
                    TicketSearchResult(
                        id = item.id,
                        ticketNo = item.numero,
                        tirageNom = item.tirageNom,
                        tirageId = item.tirageId ?: 0,
                        totalMise = item.totalMise,
                        totalGainDu = item.totalGainDu,
                        isWinner = item.isWinner,
                        isPaid = item.isPaid,
                        statut = item.status ?: "pending",
                        createdAt = item.createdAt,
                        canVoid = item.canVoid,
                        voidDeadline = null,
                        lines = emptyList()
                    )
                }
                
                TicketSearchResponse(
                    success = true,
                    tickets = results,
                    error = null
                )
            }
        )
    }

    suspend fun payTicket(ticketId: String): Response<TicketPayResponse> {
        return offlineCoordinator.execute(
            networkCall = { apiService.payTicket(ticketId) },
            offlineFallback = {
                TicketPayResponse(
                    success = false,
                    message = "Paiement indisponible hors-ligne",
                    amountPaid = null,
                    error = "Offline"
                )
            }
        )
    }

    suspend fun voidTicket(ticketId: String): Response<TicketVoidResponse> {
        return offlineCoordinator.execute(
            networkCall = { apiService.voidTicket(ticketId) },
            offlineFallback = {
                TicketVoidResponse(
                    success = false,
                    message = "Annulation indisponible hors-ligne",
                    error = "Offline"
                )
            }
        )
    }

    suspend fun getTicketPrint(ticketId: String): Response<TicketPrintResponse> {
        return offlineCoordinator.execute(
            networkCall = { apiService.getTicketPrint(ticketId) },
            offlineFallback = {
                TicketPrintResponse(
                    success = false,
                    printData = null,
                    error = "Offline"
                )
            }
        )
    }

    suspend fun getTicketBlueprint(ticketId: String): Response<TicketBlueprintResponse> {
        return offlineCoordinator.execute(
            networkCall = { apiService.getTicketBlueprint(ticketId) },
            offlineFallback = {
                TicketBlueprintResponse(
                    success = false,
                    ticketId = null,
                    tirageId = null,
                    tirageNom = null,
                    sessionKey = null,
                    lines = null,
                    totalMise = null,
                    error = "Offline"
                )
            }
        )
    }

    suspend fun searchTicketsByGroup(groupId: String): Response<TicketGroupResponse> {
        return offlineCoordinator.execute(
            networkCall = { apiService.searchTicketsByGroup(groupId) },
            offlineFallback = {
                val cached = localTicketCacheDao.getAllTickets()
                val listItems = cached.mapNotNull { cache ->
                    if (cache.rawJson != null) {
                        try {
                            gson.fromJson(cache.rawJson, TicketListItem::class.java)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }.filter {
                    it.groupId == groupId
                }
                
                val groupItems = listItems.map { item ->
                    TicketGroupItem(
                        id = item.id,
                        numero = item.numero,
                        tirageId = item.tirageId,
                        tirageNom = item.tirageNom,
                        status = item.status,
                        totalMise = item.totalMise,
                        totalGainDu = item.totalGainDu,
                        totalGainPaye = item.totalGainPaye,
                        isWinner = item.isWinner,
                        isPaid = item.isPaid,
                        canPay = item.canPay,
                        canVoid = item.canVoid,
                        createdAt = item.createdAt,
                        lines = emptyList()
                    )
                }
                
                TicketGroupResponse(
                    success = true,
                    groupId = groupId,
                    tickets = groupItems,
                    total = groupItems.size,
                    error = null
                )
            }
        )
    }

    suspend fun getHistorique(): Response<HistoriqueResponse> {
        return offlineCoordinator.execute(
            networkCall = {
                val response = apiService.getHistorique()
                if (response.isSuccessful && response.body()?.success == true) {
                    val tickets = response.body()?.tickets ?: emptyList()
                    val caches = tickets.map { ticket ->
                        LocalTicketCache(
                            ticketUuid = ticket.id,
                            tirageId = 0,
                            sessionKey = "",
                            ticketNo = ticket.numero,
                            totalMise = ticket.totalMise,
                            createdAt = System.currentTimeMillis(),
                            rawJson = gson.toJson(ticket)
                        )
                    }
                    localTicketCacheDao.insertAll(caches)
                }
                response
            },
            offlineFallback = {
                val cached = localTicketCacheDao.getAllTickets()
                val listItems = cached.mapNotNull { cache ->
                    if (cache.rawJson != null) {
                        try {
                            gson.fromJson(cache.rawJson, TicketInfo::class.java)
                        } catch (e: Exception) {
                            val item = gson.fromJson(cache.rawJson, TicketListItem::class.java)
                            TicketInfo(
                                id = item.id,
                                numero = item.numero,
                                groupId = item.groupId,
                                totalMise = item.totalMise,
                                totalGain = item.totalGainDu,
                                statut = item.status ?: "pending",
                                createdAt = item.createdAt,
                                closedAt = null,
                                tirages = listOf(item.tirageNom),
                                lines = emptyList()
                            )
                        }
                    } else null
                }
                HistoriqueResponse(
                    success = true,
                    tickets = listItems,
                    error = null
                )
            }
        )
    }
}
