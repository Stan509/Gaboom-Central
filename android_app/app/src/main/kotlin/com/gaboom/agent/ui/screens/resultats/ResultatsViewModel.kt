package com.gaboom.agent.ui.screens.resultats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaboom.agent.data.repository.ResultatsRepository
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
    private val resultatsRepository: ResultatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultatsUiState())
    val uiState: StateFlow<ResultatsUiState> = _uiState.asStateFlow()

    fun loadResultats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val response = resultatsRepository.getResultats()
            if (response.isSuccessful && response.body()?.success == true) {
                val list = response.body()?.resultats ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resultats = list
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = response.body()?.error ?: "Erreur de chargement"
                )
            }
        }
    }
}
