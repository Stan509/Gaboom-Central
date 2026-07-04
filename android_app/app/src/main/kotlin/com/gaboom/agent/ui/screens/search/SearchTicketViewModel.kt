package com.gaboom.agent.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaboom.agent.data.repository.TicketRepository
import com.gaboom.agent.data.model.BlueprintLine
import com.gaboom.agent.data.model.TicketSearchResult
import com.gaboom.agent.print.BluetoothPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchTicketUiState(
    val isLoading: Boolean = false,
    val tickets: List<TicketSearchResult> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val hasSearched: Boolean = false,
    // Blueprint for refaire fiche
    val blueprintLines: List<BlueprintLine>? = null,
    val blueprintReady: Boolean = false,
    val blueprintSourceTicketId: String? = null
)

@HiltViewModel
class SearchTicketViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val printer: BluetoothPrinter
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchTicketUiState())
    val uiState: StateFlow<SearchTicketUiState> = _uiState.asStateFlow()

    fun search(query: String) {
        if (query.length < 3) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                successMessage = null,
                hasSearched = true
            )

            val response = ticketRepository.searchTickets(query)
            if (response.isSuccessful && response.body()?.success == true) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tickets = response.body()?.tickets ?: emptyList()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = response.body()?.error ?: "Erreur de recherche"
                )
            }
        }
    }

    fun clearResults() {
        _uiState.value = SearchTicketUiState()
    }

    fun printTicket(ticketId: String) {
        viewModelScope.launch {
            val response = ticketRepository.getTicketPrint(ticketId)
            if (response.isSuccessful && response.body()?.success == true) {
                val printData = response.body()?.printData
                if (printData != null) {
                    printer.printTicket(printData)
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Impression envoyée"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    error = response.body()?.error ?: "Erreur impression"
                )
            }
        }
    }

    fun getPrintData(ticketId: String, onResult: (com.gaboom.agent.data.model.PrintData?) -> Unit) {
        viewModelScope.launch {
            val response = ticketRepository.getTicketPrint(ticketId)
            if (response.isSuccessful && response.body()?.success == true) {
                onResult(response.body()?.printData)
            } else {
                onResult(null)
            }
        }
    }

    fun payTicket(ticketId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            val response = ticketRepository.payTicket(ticketId)
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Paiement effectué: ${body.amountPaid?.toInt() ?: 0} HTG"
                )
                val currentTickets = _uiState.value.tickets
                if (currentTickets.isNotEmpty()) {
                    search(currentTickets.first().ticketNo)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = response.body()?.error ?: "Erreur paiement"
                )
            }
        }
    }

    fun voidTicket(ticketId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            val response = ticketRepository.voidTicket(ticketId)
            if (response.isSuccessful && response.body()?.success == true) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Ticket annulé"
                )
                val currentTickets = _uiState.value.tickets
                if (currentTickets.isNotEmpty()) {
                    search(currentTickets.first().ticketNo)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = response.body()?.error ?: "Erreur annulation"
                )
            }
        }
    }

    fun fetchBlueprint(ticketId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                blueprintReady = false,
                blueprintLines = null
            )

            val response = ticketRepository.getTicketBlueprint(ticketId)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.lines != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    blueprintLines = response.body()?.lines,
                    blueprintReady = true,
                    blueprintSourceTicketId = ticketId
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = response.body()?.error ?: "Erreur récupération blueprint"
                )
            }
        }
    }

    fun clearBlueprint() {
        _uiState.value = _uiState.value.copy(
            blueprintLines = null,
            blueprintReady = false,
            blueprintSourceTicketId = null
        )
    }
}
