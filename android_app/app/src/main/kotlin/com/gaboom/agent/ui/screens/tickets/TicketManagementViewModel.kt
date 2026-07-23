package com.gaboom.agent.ui.screens.tickets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaboom.agent.data.model.TicketGroupItem
import com.gaboom.agent.data.model.TicketListItem
import com.gaboom.agent.data.model.Tirage
import com.gaboom.agent.data.repository.TicketRepository
import com.gaboom.agent.data.repository.DrawRepository
import com.gaboom.agent.print.BluetoothPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class TicketStatusFilter(val value: String, val display: String) {
    ALL("", "Tous"),
    PENDING("pending", "En cours"),
    WON("won", "Gagné"),
    LOST("lost", "Perdu"),
    PAID("paid", "Déjà payé"),
    CANCELLED("cancelled", "Annulé")
}

data class TicketManagementUiState(
    val isLoading: Boolean = false,
    val tickets: List<TicketListItem> = emptyList(),
    val totalTickets: Int = 0,
    val error: String? = null,
    val successMessage: String? = null,
    
    // Filters
    val selectedDate: LocalDate? = LocalDate.now(),
    val selectedTirageId: Int? = null,
    val statusFilter: TicketStatusFilter = TicketStatusFilter.ALL,
    val searchQuery: String = "",
    
    // Available tirages for filter dropdown
    val availableTirages: List<Tirage> = emptyList(),
    
    // Action states
    val payingTicketId: String? = null,
    val voidingTicketId: String? = null,
    val reprintingTicketId: String? = null,
    
    // Group search (QR code)
    val groupTickets: List<TicketGroupItem> = emptyList(),
    val isSearchingGroup: Boolean = false,
    
    // Pagination
    val currentPage: Int = 0,
    val pageSize: Int = 50,
    val hasMore: Boolean = false
)

@HiltViewModel
class TicketManagementViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val drawRepository: DrawRepository,
    private val bluetoothPrinter: BluetoothPrinter,
    private val pendingTicketDao: com.gaboom.agent.data.local.PendingTicketDao,
    private val agentConfigDataStore: com.gaboom.agent.data.config.AgentConfigDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TicketManagementUiState())
    val uiState: StateFlow<TicketManagementUiState> = _uiState.asStateFlow()

    init {
        loadTirages()
        loadTickets(refresh = true)
    }

    private fun loadTirages() {
        viewModelScope.launch {
            val response = drawRepository.getTiragesActifs()
            if (response.isSuccessful && response.body()?.success == true) {
                val tirages = response.body()?.tirages ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    availableTirages = tirages
                )
            } else {
                val cached = agentConfigDataStore.getCachedTirages()
                _uiState.value = _uiState.value.copy(availableTirages = cached)
            }
        }
    }

    fun loadTickets(refresh: Boolean = false) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val offset = if (refresh) 0 else currentState.currentPage * currentState.pageSize

            _uiState.value = currentState.copy(
                isLoading = true,
                error = null,
                currentPage = if (refresh) 0 else currentState.currentPage
            )

            // Local pending tickets mapping
            val pendingEntities = pendingTicketDao.getAll().filter { it.syncStatus != com.gaboom.agent.data.local.SyncStatus.SYNCED }
            val pendingListItems = pendingEntities.map { mapPendingToListItem(it) }

            val response = ticketRepository.listTickets(currentState.pageSize, offset)
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                val newTickets = body.tickets ?: emptyList()

                if (refresh) {
                    agentConfigDataStore.saveCachedTicketList(newTickets)
                } else {
                    val deduplicated = (agentConfigDataStore.getCachedTicketList() + newTickets).distinctBy { it.id }
                    agentConfigDataStore.saveCachedTicketList(deduplicated)
                }

                val rawList = if (refresh) newTickets else currentState.tickets + newTickets
                val mergedList = pendingListItems + rawList.filter { raw -> pendingListItems.none { p -> p.id == raw.id } }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tickets = mergedList,
                    totalTickets = body.total ?: mergedList.size,
                    hasMore = newTickets.size >= currentState.pageSize
                )
            } else {
                val cachedTickets = agentConfigDataStore.getCachedTicketList()
                val filteredCached = filterList(cachedTickets, currentState)
                val mergedList = pendingListItems + filteredCached.filter { raw -> pendingListItems.none { p -> p.id == raw.id } }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tickets = mergedList,
                    totalTickets = mergedList.size,
                    hasMore = false,
                    error = if (cachedTickets.isEmpty()) (response.body()?.error ?: "Erreur serveur") else null
                )
            }
        }
    }

    private fun mapPendingToListItem(entity: com.gaboom.agent.data.local.PendingTicketEntity): TicketListItem {
        val count = entity.linesSummary.split(", ").filter { it.isNotBlank() }.size
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX")
        val createdStr = try {
            java.time.OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(entity.createdAt),
                java.time.ZoneId.systemDefault()
            ).format(formatter)
        } catch (e: Exception) {
            java.time.Instant.ofEpochMilli(entity.createdAt).toString()
        }

        val tirageNom = _uiState.value.availableTirages.find { it.id == entity.tirageId }?.nom ?: "Tirage"

        return TicketListItem(
            id = entity.id,
            numero = entity.localTicketNo ?: "HL-${entity.id.take(8).uppercase()}",
            groupId = entity.batchId,
            tirageId = entity.tirageId,
            tirageNom = tirageNom,
            tirageOpen = true,
            status = when (entity.syncStatus) {
                com.gaboom.agent.data.local.SyncStatus.PENDING,
                com.gaboom.agent.data.local.SyncStatus.LOCAL_PENDING,
                com.gaboom.agent.data.local.SyncStatus.PRINTED -> "pending"
                com.gaboom.agent.data.local.SyncStatus.SYNCING -> "pending"
                com.gaboom.agent.data.local.SyncStatus.FAILED,
                com.gaboom.agent.data.local.SyncStatus.UPLOAD_FAILED,
                com.gaboom.agent.data.local.SyncStatus.CONFLICT -> "pending"
                com.gaboom.agent.data.local.SyncStatus.SYNCED -> "pending"
                com.gaboom.agent.data.local.SyncStatus.VALIDATION_PENDING -> "pending"
            },
            numBets = count,
            totalMise = entity.totalMise,
            totalGainDu = 0.0,
            totalGainPaye = 0.0,
            isWinner = false,
            isPaid = false,
            canPay = false,
            canVoid = (entity.syncStatus == com.gaboom.agent.data.local.SyncStatus.PENDING ||
                    entity.syncStatus == com.gaboom.agent.data.local.SyncStatus.LOCAL_PENDING ||
                    entity.syncStatus == com.gaboom.agent.data.local.SyncStatus.PRINTED) &&
                    ((System.currentTimeMillis() - entity.createdAt) / 60000.0 < 2.0),
            canReprint = true,
            createdAt = createdStr,
            ageMinutes = (System.currentTimeMillis() - entity.createdAt) / 60000.0
        )
    }

    private fun filterList(list: List<TicketListItem>, state: TicketManagementUiState): List<TicketListItem> {
        var filtered = list
        if (state.selectedDate != null) {
            val dateStr = state.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            filtered = filtered.filter { it.createdAt.startsWith(dateStr) }
        }
        if (state.selectedTirageId != null) {
            filtered = filtered.filter { it.tirageId == state.selectedTirageId }
        }
        if (state.statusFilter != TicketStatusFilter.ALL) {
            filtered = filtered.filter { it.status == state.statusFilter.value }
        }
        val query = state.searchQuery.trim().lowercase()
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.numero.lowercase().contains(query) ||
                it.id.lowercase().contains(query)
            }
        }
        return filtered
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return
        _uiState.value = _uiState.value.copy(currentPage = _uiState.value.currentPage + 1)
        loadTickets(refresh = false)
    }

    fun setDateFilter(date: LocalDate?) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadTickets(refresh = true)
    }

    fun setTirageFilter(tirageId: Int?) {
        _uiState.value = _uiState.value.copy(selectedTirageId = tirageId)
        loadTickets(refresh = true)
    }

    fun setStatusFilter(filter: TicketStatusFilter) {
        _uiState.value = _uiState.value.copy(statusFilter = filter)
        loadTickets(refresh = true)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun searchByQuery() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            loadTickets(refresh = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val response = ticketRepository.searchTickets(query)
            if (response.isSuccessful && response.body()?.success == true) {
                val searchResults = response.body()?.tickets ?: emptyList()
                val items = searchResults.map { result ->
                    TicketListItem(
                        id = result.id,
                        numero = result.ticketNo,
                        tirageId = result.tirageId,
                        tirageNom = result.tirageNom,
                        tirageOpen = false,
                        status = when {
                            result.statut == "ANNULE" -> "cancelled"
                            result.isPaid -> "paid"
                            result.isWinner -> "won"
                            else -> "pending"
                        },
                        numBets = result.lines?.size ?: 0,
                        totalMise = result.totalMise,
                        totalGainDu = result.totalGainDu,
                        totalGainPaye = 0.0,
                        isWinner = result.isWinner,
                        isPaid = result.isPaid,
                        canPay = result.isWinner && !result.isPaid,
                        canVoid = result.canVoid,
                        canReprint = result.statut != "ANNULE",
                        createdAt = result.createdAt,
                        ageMinutes = 0.0
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tickets = items,
                    totalTickets = items.size,
                    hasMore = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = response.body()?.error ?: "Ticket non trouvé"
                )
            }
        }
    }

    fun payTicket(ticketId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(payingTicketId = ticketId, error = null)
            val response = ticketRepository.payTicket(ticketId)
            if (response.isSuccessful && response.body()?.success == true) {
                val amount = response.body()?.amountPaid ?: 0.0
                _uiState.value = _uiState.value.copy(
                    payingTicketId = null,
                    successMessage = "Ticket payé: ${amount.toInt()} G"
                )
                loadTickets(refresh = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    payingTicketId = null,
                    error = response.body()?.error ?: "Erreur lors du paiement"
                )
            }
        }
    }

    fun voidTicket(ticketId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(voidingTicketId = ticketId, error = null)
            val response = ticketRepository.voidTicket(ticketId)
            if (response.isSuccessful && response.body()?.success == true) {
                _uiState.value = _uiState.value.copy(
                    voidingTicketId = null,
                    successMessage = "Ticket annulé"
                )
                loadTickets(refresh = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    voidingTicketId = null,
                    error = response.body()?.error ?: "Erreur lors de l'annulation"
                )
            }
        }
    }

    suspend fun voidTicketSync(ticketId: String): Boolean {
        val response = ticketRepository.voidTicket(ticketId)
        return response.isSuccessful && response.body()?.success == true
    }

    fun reprintTicket(ticketId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(reprintingTicketId = ticketId, error = null)
            val response = ticketRepository.getTicketPrint(ticketId)
            if (response.isSuccessful && response.body()?.success == true) {
                val printData = response.body()?.printData
                if (printData != null) {
                    val printResult = bluetoothPrinter.printTicket(printData)
                    if (printResult.isSuccess) {
                        _uiState.value = _uiState.value.copy(
                            reprintingTicketId = null,
                            successMessage = "Ticket réimprimé"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            reprintingTicketId = null,
                            error = "Erreur impression: ${printResult.exceptionOrNull()?.message}"
                        )
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    reprintingTicketId = null,
                    error = response.body()?.error ?: "Erreur récupération données"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun onQrCodeScanned(content: String) {
        _uiState.value = _uiState.value.copy(searchQuery = content)
        searchByQuery()
    }

    fun searchByGroupId(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingGroup = true, error = null)
            val response = ticketRepository.searchTicketsByGroup(groupId)
            if (response.isSuccessful && response.body()?.success == true) {
                _uiState.value = _uiState.value.copy(
                    isSearchingGroup = false,
                    groupTickets = response.body()?.tickets ?: emptyList()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSearchingGroup = false,
                    error = response.body()?.error ?: "Aucun ticket trouvé pour ce QR code"
                )
            }
        }
    }

    fun clearGroupResults() {
        _uiState.value = _uiState.value.copy(groupTickets = emptyList())
    }

    suspend fun getTicketPrintData(ticketId: String): com.gaboom.agent.data.model.PrintData? {
        val response = ticketRepository.getTicketPrint(ticketId)
        return if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.printData
        } else {
            null
        }
    }
}
