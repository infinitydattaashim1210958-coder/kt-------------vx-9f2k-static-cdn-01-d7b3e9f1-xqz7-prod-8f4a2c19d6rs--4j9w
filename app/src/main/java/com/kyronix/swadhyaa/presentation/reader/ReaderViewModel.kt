package com.kyronix.swadhyaa.presentation.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.entity.ScholarEntity
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import com.kyronix.swadhyaa.data.repository.BhashyaField
import com.kyronix.swadhyaa.data.repository.BhashyaRepository
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
    val mantraNoOptions: List<Int> = emptyList(),
    val jumpError: String? = null,
    // ── Bhashya ───────────────────────────────────────────────────────────
    val scholarsByLang: Map<String, List<ScholarEntity>> = emptyMap(),
    val availableLanguages: List<String> = emptyList(),
    val selectedLanguage: String = "",
    val selectedScholar: ScholarEntity? = null,
    val scholarDownloadStatus: Map<Int, Boolean> = emptyMap(),
    val bhashyaContent: List<BhashyaField> = emptyList(),
    val bhashyaLoading: Boolean = false,
    val bhashyaError: String? = null,
    val downloadProgress: String? = null,
)

class ReaderViewModel(
    private val repository: VedaRepository,
    private val bhashyaRepo: BhashyaRepository,
    private val appContext: Context,
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
                    loading = false, error = e.message ?: "Failed to load mantra"
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

    fun jumpLevel1(v: Int) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            val l2 = repository.getLevel2List(cur.vedaId, v).firstOrNull()
            val no = repository.getMantraNoList(cur.vedaId, v, l2, null).firstOrNull() ?: 1
            repository.getMantraAt(cur.vedaId, v, l2, null, no)?.let { applyMantra(it) }
        }
    }

    fun jumpLevel2(v: Int) {
        val cur = _state.value.current ?: return
        val l1 = cur.level1 ?: return
        viewModelScope.launch {
            val no = repository.getMantraNoList(cur.vedaId, l1, v, null).firstOrNull() ?: 1
            repository.getMantraAt(cur.vedaId, l1, v, null, no)?.let { applyMantra(it) }
        }
    }

    fun jumpMantraNo(no: Int) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            val result = repository.getMantraAt(cur.vedaId, cur.level1, cur.level2, cur.level3, no)
            if (result != null) applyMantra(result)
            else _state.value = _state.value.copy(jumpError = "মন্ত্র নং $no পাওয়া যায়নি")
        }
    }

    fun selectLanguage(lang: String) {
        _state.value = _state.value.copy(
            selectedLanguage = lang,
            selectedScholar = null,
            bhashyaContent = emptyList(),
            bhashyaError = null,
            downloadProgress = null
        )
    }

    fun selectScholar(scholar: ScholarEntity) {
        val cur = _state.value.current ?: return
        val isDownloaded = _state.value.scholarDownloadStatus[scholar.id] == true
        _state.value = _state.value.copy(
            selectedScholar = scholar,
            bhashyaContent = emptyList(),
            bhashyaError = null,
            downloadProgress = null
        )
        if (isDownloaded) loadBhashyaContent(scholar, cur.id)
    }

    fun downloadScholar(scholar: ScholarEntity) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(downloadProgress = "শুরু হচ্ছে…")
            bhashyaRepo.downloadIfNeeded(appContext, scholar) { dl, total ->
                val pct = if (total > 0) (dl * 100 / total).toInt() else 0
                _state.value = _state.value.copy(downloadProgress = "$pct%")
            }.onSuccess {
                val updated = _state.value.scholarDownloadStatus.toMutableMap()
                scholar.id?.let { updated[it] = true }
                _state.value = _state.value.copy(
                    scholarDownloadStatus = updated, downloadProgress = null
                )
                loadBhashyaContent(scholar, cur.id)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    downloadProgress = null,
                    bhashyaError = "ডাউনলোড ব্যর্থ: ${e.message}"
                )
            }
        }
    }

    fun deleteScholar(scholar: ScholarEntity) {
        val file = scholar.packFile ?: return
        PackDownloadManager.deleteLocalPack(appContext, file)
        val updated = _state.value.scholarDownloadStatus.toMutableMap()
        scholar.id?.let { updated[it] = false }
        _state.value = _state.value.copy(
            scholarDownloadStatus = updated,
            selectedScholar = null,
            bhashyaContent = emptyList(),
            bhashyaError = null
        )
    }

    private fun loadBhashyaContent(scholar: ScholarEntity, mantraId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(bhashyaLoading = true, bhashyaError = null)
            bhashyaRepo.getBhashya(appContext, scholar, mantraId)
                .onSuccess { fields ->
                    _state.value = _state.value.copy(
                        bhashyaContent = fields, bhashyaLoading = false
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        bhashyaContent = emptyList(),
                        bhashyaLoading = false,
                        bhashyaError = e.message ?: "ভাষ্য লোড ব্যর্থ"
                    )
                }
        }
    }

    private suspend fun applyMantra(m: MantraContent) {
        val l1 = repository.getLevel1List(m.vedaId)
        val l2 = m.level1?.let { repository.getLevel2List(m.vedaId, it) }.orEmpty()
        val l3 = if (m.level1 != null && m.level2 != null)
            repository.getLevel3List(m.vedaId, m.level1, m.level2) else emptyList()
        val nos = repository.getMantraNoList(m.vedaId, m.level1, m.level2, m.level3)

        val scholars = bhashyaRepo.getScholarsForMantra(m.id)
        val byLang = scholars
            .groupBy { it.language?.lowercase()?.trim() ?: "other" }
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value.sortedBy { s -> s.displayOrder ?: 100 } }
        val langs = byLang.keys.toList()
        val dlStatus = scholars.associate { s ->
            (s.id ?: -1) to bhashyaRepo.isDownloaded(appContext, s)
        }

        val prevLang = _state.value.selectedLanguage
        val newLang = if (prevLang.isNotEmpty() && byLang.containsKey(prevLang)) prevLang
                      else langs.firstOrNull() ?: ""

        _state.value = _state.value.copy(
            loading = false, error = null, jumpError = null,
            current = m,
            level1Options = l1, level2Options = l2, level3Options = l3, mantraNoOptions = nos,
            scholarsByLang = byLang,
            availableLanguages = langs,
            selectedLanguage = newLang,
            selectedScholar = null,
            bhashyaContent = emptyList(),
            bhashyaError = null,
            scholarDownloadStatus = dlStatus
        )
    }

    class Factory(
        private val repository: VedaRepository,
        private val bhashyaRepo: BhashyaRepository,
        private val appContext: Context,
        private val vedaId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReaderViewModel::class.java))
                return ReaderViewModel(repository, bhashyaRepo, appContext, vedaId) as T
            throw IllegalArgumentException("Unknown ViewModel")
        }
    }
}
