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
    suspend fun createTicket(
        request: TicketCreateRequest,
        tirageId: Int,
        apiLines: List<TicketLine>
    ): Response<TicketCreateResponse> {
        return offlineCoordinator.execute(
            networkCall = {
                val response = apiService.createTicket(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    val created = response.body()?.ticket
                    if (created != null) {
                        localTicketCacheDao.insert(
                            LocalTicketCache(
                                ticketUuid = created.id,
                                tirageId = tirageId,
                                sessionKey = "",
                                ticketNo = created.numero,
                                totalMise = created.totalMise,
                                createdAt = System.currentTimeMillis(),
                                rawJson = gson.toJson(created)
                            )
                        )
                    }
                }
                response
            },
            offlineFallback = {
                val localId = UUID.randomUUID().toString()
                val localTicketNo = "HL-${localId.take(8).uppercase()}"
                
                // Convert to MultiTicketCreateRequest for consistent offline payload
                val multiRequest = MultiTicketCreateRequest(
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
                )
                
                val payloadJson = gson.toJson(multiRequest)
                val linesSummary = apiLines.take(5).joinToString(", ") { "${it.jeu}:${it.valeur}" } + 
                                   if (apiLines.size > 5) "..." else ""
                
                val deviceCreds = agentConfigDataStore.getDeviceCredentials()
                val hmacSignature = if (deviceCreds != null) {
                    HmacUtil.signPayload(
                        deviceSecret = deviceCreds.deviceSecret,
                        payloadJson = payloadJson,
                        sessionKey = ""
                    )
                } else null

                val pendingTicket = PendingTicketEntity(
                    id = localId,
                    payloadJson = payloadJson,
                    tirageIds = tirageId.toString(),
                    tirageId = tirageId,
                    sessionKey = "",
                    totalMise = apiLines.sumOf { it.mise },
                    linesSummary = linesSummary,
                    syncStatus = SyncStatus.LOCAL_PENDING,
                    hmacSignature = hmacSignature
                )
                pendingTicketDao.insert(pendingTicket)

                val ticketLines = apiLines.map { line ->
                    TicketLine(
                        jeu = line.jeu,
                        valeur = line.valeur,
                        mise = line.mise,
                        potentielGain = 0.0,
                        gratuit = line.gratuit,
                        option = line.option
                    )
                }

                val ticketInfo = TicketInfo(
                    id = localId,
                    numero = localTicketNo,
                    groupId = null,
                    totalMise = apiLines.sumOf { it.mise },
                    totalGain = 0.0,
                    statut = "LOCAL_PENDING",
                    createdAt = java.time.LocalDateTime.now().toString(),
                    closedAt = null,
                    tirages = listOf("Tirage"),
                    lines = ticketLines
                )

                // Store immediately to local read cache
                localTicketCacheDao.insert(
                    LocalTicketCache(
                        ticketUuid = localId,
                        tirageId = tirageId,
                        sessionKey = "",
                        ticketNo = localTicketNo,
                        totalMise = apiLines.sumOf { it.mise },
                        createdAt = System.currentTimeMillis(),
                        rawJson = gson.toJson(ticketInfo)
                    )
                )

                // Trigger synchronization in background immediately
                syncManagerProvider.get().syncPendingTickets()

                TicketCreateResponse(
                    success = true,
                    ticket = ticketInfo,
                    error = null
                )
            }
        )
    }

    suspend fun createMultiTicket(
        request: MultiTicketCreateRequest
    ): Response<MultiTicketCreateResponse> {
        return offlineCoordinator.execute(
            networkCall = {
                val response = apiService.createMultiTicket(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    val tickets = body.tickets ?: emptyList()
                    tickets.forEach { ticket ->
                        localTicketCacheDao.insert(
                            LocalTicketCache(
                                ticketUuid = ticket.ticketId,
                                tirageId = ticket.tirageId,
                                sessionKey = request.sessionKey ?: "",
                                ticketNo = ticket.ticketNo,
                                totalMise = ticket.totalMise,
                                createdAt = System.currentTimeMillis(),
                                rawJson = gson.toJson(ticket)
                            )
                        )
                    }
                }
                response
            },
            offlineFallback = {
                val groupId = UUID.randomUUID().toString()
                val ticketsList = request.tirageIds.map { tirageId ->
                    val localId = UUID.randomUUID().toString()
                    val localTicketNo = if (com.gaboom.agent.data.config.FeatureFlags.isEnabled("OFFLINE_ENGINE_V2")) {
                        kotlinx.coroutines.runBlocking { agentConfigDataStore.getAndIncrementTicketNumber() }
                        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        "CB-$year-${kotlinx.coroutines.runBlocking { agentConfigDataStore.getAndIncrementTicketNumber() - 1 }}" // Subtract 1 since runBlocking increments it
                    } else {
                        "HL-${localId.take(8).uppercase()}"
                    }
                    
                    val payloadJson = gson.toJson(request)
                    val linesSummary = request.entries.take(5).joinToString(", ") { "${it.game}:${it.number}" } + 
                                       if (request.entries.size > 5) "..." else ""
                                       
                    val deviceCreds = agentConfigDataStore.getDeviceCredentials()
                    val hmacSignature = if (deviceCreds != null) {
                        HmacUtil.signPayload(
                            deviceSecret = deviceCreds.deviceSecret,
                            payloadJson = payloadJson,
                            sessionKey = request.sessionKey ?: ""
                        )
                    } else null
                    
                    val pendingTicket = PendingTicketEntity(
                        id = localId,
                        payloadJson = payloadJson,
                        tirageIds = request.tirageIds.joinToString(","),
                        tirageId = tirageId,
                        sessionKey = request.sessionKey,
                        totalMise = request.entries.sumOf { it.stake },
                        linesSummary = linesSummary,
                        syncStatus = SyncStatus.LOCAL_PENDING,
                        hmacSignature = hmacSignature,
                        batchId = groupId,
                        localTicketNo = localTicketNo
                    )
                    pendingTicketDao.insert(pendingTicket)

                    val multiTicket = MultiTicketInfo(
                        tirageId = tirageId,
                        tirageNom = "Tirage",
                        ticketId = localId,
                        ticketNo = localTicketNo,
                        groupId = groupId,
                        totalMise = request.entries.sumOf { it.stake },
                        lines = null
                    )

                    val ticketItem = com.gaboom.agent.data.model.TicketListItem(
                        id = localId,
                        numero = localTicketNo,
                        groupId = groupId,
                        tirageId = tirageId,
                        tirageNom = "Tirage",
                        tirageOpen = true,
                        status = "pending",
                        numBets = request.entries.size,
                        totalMise = request.entries.sumOf { it.stake },
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

                    // Save to local read cache
                    localTicketCacheDao.insert(
                        LocalTicketCache(
                            ticketUuid = localId,
                            tirageId = tirageId,
                            sessionKey = request.sessionKey ?: "",
                            ticketNo = localTicketNo,
                            totalMise = request.entries.sumOf { it.stake },
                            createdAt = System.currentTimeMillis(),
                            rawJson = gson.toJson(ticketItem)
                        )
                    )

                    multiTicket
                }

                // Trigger synchronization in background immediately
                syncManagerProvider.get().syncPendingTickets()

                MultiTicketCreateResponse(
                    success = true,
                    groupId = groupId,
                    tickets = ticketsList,
                    failed = null,
                    error = null
                )
            }
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
                                isPaid = info.statut.lowercase() == "paid",
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
