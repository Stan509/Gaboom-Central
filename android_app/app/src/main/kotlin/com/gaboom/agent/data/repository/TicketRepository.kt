package com.gaboom.agent.data.repository

import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.local.PendingTicketDao
import com.gaboom.agent.data.local.PendingTicketEntity
import com.gaboom.agent.data.local.SyncStatus
import com.gaboom.agent.data.model.*
import com.gaboom.agent.data.config.AgentConfigDataStore
import com.gaboom.agent.data.sync.OfflineCoordinator
import com.gaboom.agent.util.HmacUtil
import com.google.gson.Gson
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketRepository @Inject constructor(
    private val apiService: AgentApiService,
    private val pendingTicketDao: PendingTicketDao,
    private val agentConfigDataStore: AgentConfigDataStore,
    private val offlineCoordinator: OfflineCoordinator,
    private val gson: Gson
) {
    suspend fun createTicket(
        request: TicketCreateRequest,
        tirageId: Int,
        apiLines: List<TicketLine>
    ): Response<TicketCreateResponse> {
        return offlineCoordinator.execute(
            networkCall = {
                apiService.createTicket(request)
            },
            offlineFallback = {
                val localId = UUID.randomUUID().toString()
                val localTicketNo = "HL-${localId.take(8).uppercase()}"
                
                val payloadJson = gson.toJson(request)
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
                    syncStatus = SyncStatus.PENDING,
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

                TicketCreateResponse(
                    success = true,
                    ticket = TicketInfo(
                        id = localId,
                        numero = localTicketNo,
                        groupId = null,
                        totalMise = apiLines.sumOf { it.mise },
                        totalGain = 0.0,
                        statut = "VALIDATION_PENDING",
                        createdAt = java.time.LocalDateTime.now().toString(),
                        closedAt = null,
                        tirages = listOf("Tirage"),
                        lines = ticketLines
                    ),
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
                apiService.createMultiTicket(request)
            },
            offlineFallback = {
                val groupId = UUID.randomUUID().toString()
                val ticketsList = request.tirageIds.map { tirageId ->
                    val localId = UUID.randomUUID().toString()
                    val localTicketNo = "HL-${localId.take(8).uppercase()}"
                    
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
                        syncStatus = SyncStatus.PENDING,
                        hmacSignature = hmacSignature,
                        batchId = groupId
                    )
                    pendingTicketDao.insert(pendingTicket)

                    MultiTicketInfo(
                        tirageId = tirageId,
                        tirageNom = "Tirage",
                        ticketId = localId,
                        ticketNo = localTicketNo,
                        groupId = groupId,
                        totalMise = request.entries.sumOf { it.stake },
                        lines = null
                    )
                }

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
            networkCall = { apiService.listTickets(limit = limit, offset = offset) },
            offlineFallback = {
                TicketListResponse(
                    success = true,
                    tickets = emptyList(),
                    total = 0,
                    limit = limit,
                    offset = offset,
                    error = null
                )
            }
        )
    }

    suspend fun searchTickets(query: String): Response<TicketSearchResponse> {
        return offlineCoordinator.execute(
            networkCall = { apiService.searchTickets(query) },
            offlineFallback = {
                TicketSearchResponse(
                    success = true,
                    tickets = emptyList(),
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
                TicketGroupResponse(
                    success = true,
                    groupId = groupId,
                    tickets = emptyList(),
                    total = 0,
                    error = null
                )
            }
        )
    }

    suspend fun getHistorique(): Response<HistoriqueResponse> {
        return offlineCoordinator.execute(
            networkCall = { apiService.getHistorique() },
            offlineFallback = {
                HistoriqueResponse(
                    success = true,
                    tickets = emptyList(),
                    error = null
                )
            }
        )
    }
}
