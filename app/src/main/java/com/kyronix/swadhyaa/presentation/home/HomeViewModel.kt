package com.kyronix.swadhyaa.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.repository.VedaRepository
import com.kyronix.swadhyaa.domain.model.VedaSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the Home screen.
 */
sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val vedas: List<VedaSummary>,
        val totalMantras: Int
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

/**
 * HomeViewModel — loads real data from VedaRepository.
 * Never touches Room directly.
 */
class HomeViewModel(
    private val repository: VedaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val vedas = repository.getVedaSummaries()
                val total = repository.getTotalMantraCount()
                _uiState.value = HomeUiState.Success(vedas = vedas, totalMantras = total)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(
                    e.message ?: "Failed to load Vedas from database"
                )
            }
        }
    }

    class Factory(
        private val repository: VedaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
