package com.gaboom.agent.ui.screens.tirages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaboom.agent.data.model.Tirage
import com.gaboom.agent.data.repository.DrawRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TiragesUiState(
    val isLoading: Boolean = false,
    val tirages: List<Tirage> = emptyList(),
    val error: String? = null,
    val isOnline: Boolean = true,
    val lastSyncTime: String? = null,
    val serverTime: String? = null
)

@HiltViewModel
class TiragesViewModel @Inject constructor(
    private val drawRepository: DrawRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TiragesUiState())
    val uiState: StateFlow<TiragesUiState> = _uiState.asStateFlow()

    fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                loadTirages()
                delay(30_000L) // 30 secondes
            }
        }
    }

    fun loadTirages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val response = drawRepository.getTiragesActifs()
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                val allTirages = body.tirages ?: emptyList()
                val now = java.time.LocalTime.now()
                val syncTime = String.format("%02d:%02d", now.hour, now.minute)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tirages = allTirages,
                    isOnline = true,
                    lastSyncTime = syncTime,
                    serverTime = body.serverTime,
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = response.body()?.error ?: "Erreur serveur",
                    isOnline = false
                )
            }
        }
    }
}
