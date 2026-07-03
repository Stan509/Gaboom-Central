package com.gaboom.agent.ui.screens.vente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.model.Tirage
import com.gaboom.agent.data.config.AgentConfigDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TirageSelectionUiState(
    val tirages: List<Tirage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TirageSelectionViewModel @Inject constructor(
    private val apiService: AgentApiService,
    private val agentConfigDataStore: AgentConfigDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TirageSelectionUiState())
    val uiState: StateFlow<TirageSelectionUiState> = _uiState.asStateFlow()

    fun loadOpenTirages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getTiragesActifs()
                if (response.isSuccessful) {
                    val allTirages = response.body()?.tirages ?: emptyList()
                    agentConfigDataStore.saveCachedTirages(allTirages)
                    val tirages = allTirages.filter { it.etat == "OUVERT" }
                    _uiState.value = _uiState.value.copy(
                        tirages = tirages,
                        isLoading = false
                    )
                } else {
                    val cached = agentConfigDataStore.getCachedTirages()
                    _uiState.value = _uiState.value.copy(
                        tirages = cached.filter { it.etat == "OUVERT" },
                        isLoading = false,
                        error = if (cached.isEmpty()) "Erreur chargement tirages" else null
                    )
                }
            } catch (e: Exception) {
                val cached = agentConfigDataStore.getCachedTirages()
                _uiState.value = _uiState.value.copy(
                    tirages = cached.filter { it.etat == "OUVERT" },
                    isLoading = false,
                    error = if (cached.isEmpty()) "Erreur: ${e.message}" else null
                )
            }
        }
    }
}
