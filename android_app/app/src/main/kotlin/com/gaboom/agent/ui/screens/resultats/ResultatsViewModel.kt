package com.gaboom.agent.ui.screens.resultats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaboom.agent.data.api.AgentApiService
import com.gaboom.agent.data.config.AgentConfigDataStore
import com.gaboom.agent.data.model.ResultatTirage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultatsUiState(
    val isLoading: Boolean = false,
    val resultats: List<ResultatTirage> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ResultatsViewModel @Inject constructor(
    private val apiService: AgentApiService,
    private val agentConfigDataStore: AgentConfigDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultatsUiState())
    val uiState: StateFlow<ResultatsUiState> = _uiState.asStateFlow()

    fun loadResultats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getResultats()
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()?.resultats ?: emptyList()
                    agentConfigDataStore.saveCachedResultats(list)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resultats = list
                    )
                } else {
                    val cached = agentConfigDataStore.getCachedResultats()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resultats = cached,
                        error = if (cached.isEmpty()) "Erreur serveur" else null
                    )
                }
            } catch (e: Exception) {
                val cached = agentConfigDataStore.getCachedResultats()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resultats = cached,
                    error = if (cached.isEmpty()) e.message else null
                )
            }
        }
    }
}
