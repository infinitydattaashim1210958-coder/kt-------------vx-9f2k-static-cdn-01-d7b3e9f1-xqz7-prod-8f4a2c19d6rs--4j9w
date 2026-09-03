package com.kyronix.swadhyaa.presentation.ramayana

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.local.entity.KandaEntity
import com.kyronix.swadhyaa.data.repository.RamayanaRepository
import com.kyronix.swadhyaa.data.repository.ShlokaContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RamayanaUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val kandas: List<KandaEntity> = emptyList(),
    val current: ShlokaContent? = null,
    val sargaOptions: List<Int> = emptyList(),
    val shlokaNoOptions: List<Int> = emptyList(),
    val jumpError: String? = null
)

class RamayanaViewModel(private val repository: RamayanaRepository) : ViewModel() {

    private val _state = MutableStateFlow(RamayanaUiState())
    val state: StateFlow<RamayanaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val kandas = repository.getKandas()
            val first = repository.openFirst()
            if (first == null) {
                _state.value = _state.value.copy(loading = false, error = "রামায়ণ ডেটা পাওয়া যায়নি")
                return@launch
            }
            applyShloka(kandas, first)
        }
    }

    fun openKanda(kandaId: Int) {
        viewModelScope.launch {
            repository.openKanda(kandaId)?.let { applyShloka(_state.value.kandas, it) }
        }
    }

    fun jumpSarga(chapter: Int) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            val sargaId = repository.findSargaId(cur.kandaId, chapter) ?: return@launch
            repository.jumpSarga(cur.kandaId, sargaId)?.let { applyShloka(_state.value.kandas, it) }
        }
    }

    fun jumpShlokaNo(no: Int) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(jumpError = null)
            val result = repository.jumpShlokaNo(cur.sargaId, no)
            if (result != null) applyShloka(_state.value.kandas, result)
            else _state.value = _state.value.copy(jumpError = "শ্লোক নং $no পাওয়া যায়নি")
        }
    }

    fun next() {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            repository.next(cur.kandaId, cur.id)?.let { applyShloka(_state.value.kandas, it) }
        }
    }

    fun prev() {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            repository.prev(cur.kandaId, cur.id)?.let { applyShloka(_state.value.kandas, it) }
        }
    }

    private suspend fun applyShloka(kandas: List<KandaEntity>, s: ShlokaContent) {
        val sargaOptions = repository.getSargaOptions(s.kandaId)
        val shlokaNoOptions = repository.getShlokaNoOptions(s.sargaId)
        _state.value = _state.value.copy(
            loading = false,
            error = null,
            jumpError = null,
            kandas = kandas.ifEmpty { _state.value.kandas },
            current = s,
            sargaOptions = sargaOptions,
            shlokaNoOptions = shlokaNoOptions
        )
    }

    class Factory(private val repository: RamayanaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RamayanaViewModel(repository) as T
    }
}
