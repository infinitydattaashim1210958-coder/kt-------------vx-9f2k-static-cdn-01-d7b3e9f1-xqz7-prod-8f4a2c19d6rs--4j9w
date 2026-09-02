package com.kyronix.swadhyaa.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.repository.VedaRepository
import com.kyronix.swadhyaa.domain.model.MantraContent
import com.kyronix.swadhyaa.domain.model.VedaSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val vedas: List<VedaSummary> = emptyList(),
    val current: MantraContent? = null,
    val level1Options: List<Int> = emptyList(),
    val level2Options: List<Int> = emptyList(),
    val level3Options: List<Int> = emptyList(),
    val mantraNoOptions: List<Int> = emptyList()
)

class ReaderViewModel(
    private val repository: VedaRepository,
    private val initialVedaId: Int
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val vedas = repository.getVedaSummaries()
            _state.value = _state.value.copy(vedas = vedas)
            openVeda(initialVedaId)
        }
    }

    fun openVeda(vedaId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val first = repository.getFirstMantra(vedaId)
                    ?: throw IllegalStateException("No mantras for veda $vedaId")
                applyMantra(first)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load mantra"
                )
            }
        }
    }

    fun next() {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            repository.getNext(cur.vedaId, cur.id)?.let { applyMantra(it) }
        }
    }

    fun prev() {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            repository.getPrev(cur.vedaId, cur.id)?.let { applyMantra(it) }
        }
    }

    fun jumpLevel1(level1: Int) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            val l2List = repository.getLevel2List(cur.vedaId, level1)
            val l2 = l2List.firstOrNull()
            val nos = repository.getMantraNoList(cur.vedaId, level1, l2, null)
            val no = nos.firstOrNull() ?: 1
            repository.getMantraAt(cur.vedaId, level1, l2, null, no)?.let { applyMantra(it) }
        }
    }

    fun jumpLevel2(level2: Int) {
        val cur = _state.value.current ?: return
        val l1 = cur.level1 ?: return
        viewModelScope.launch {
            val nos = repository.getMantraNoList(cur.vedaId, l1, level2, null)
            val no = nos.firstOrNull() ?: 1
            repository.getMantraAt(cur.vedaId, l1, level2, null, no)?.let { applyMantra(it) }
        }
    }

    fun jumpMantraNo(mantraNo: Int) {
        val cur = _state.value.current ?: return
        val l1 = cur.level1 ?: return
        viewModelScope.launch {
            repository.getMantraAt(
                cur.vedaId, l1, cur.level2, cur.level3, mantraNo
            )?.let { applyMantra(it) }
        }
    }

    private suspend fun applyMantra(m: MantraContent) {
        val l1List = repository.getLevel1List(m.vedaId)
        val l2List = m.level1?.let { repository.getLevel2List(m.vedaId, it) }.orEmpty()
        val l3List = if (m.level1 != null && m.level2 != null) {
            repository.getLevel3List(m.vedaId, m.level1, m.level2)
        } else emptyList()
        val noList = if (m.level1 != null) {
            repository.getMantraNoList(m.vedaId, m.level1, m.level2, m.level3)
        } else emptyList()
        _state.value = _state.value.copy(
            loading = false,
            error = null,
            current = m,
            level1Options = l1List,
            level2Options = l2List,
            level3Options = l3List,
            mantraNoOptions = noList
        )
    }

    class Factory(
        private val repository: VedaRepository,
        private val vedaId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
                return ReaderViewModel(repository, vedaId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel")
        }
    }
}
