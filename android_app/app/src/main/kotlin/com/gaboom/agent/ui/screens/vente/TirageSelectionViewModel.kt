package com.gaboom.agent.ui.screens.vente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaboom.agent.data.model.Tirage
import com.gaboom.agent.data.repository.DrawRepository
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
    private val drawRepository: DrawRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TirageSelectionUiState())
    val uiState: StateFlow<TirageSelectionUiState> = _uiState.asStateFlow()

    fun loadOpenTirages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val response = drawRepository.getTiragesActifs()
            if (response.isSuccessful && response.body()?.success == true) {
                val allTirages = response.body()?.tirages ?: emptyList()
                val tirages = allTirages.filter { it.etat == "OUVERT" }
                _uiState.value = _uiState.value.copy(
                    tirages = tirages,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = response.body()?.error ?: "Erreur chargement tirages"
                )
            }
        }
    }
}
