package com.kyronix.swadhyaa.presentation.mahabharata

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.repository.Adhyay
import com.kyronix.swadhyaa.data.repository.MahabharataManifest
import com.kyronix.swadhyaa.data.repository.MahabharataRepository
import com.kyronix.swadhyaa.data.repository.ParbaInfo
import com.kyronix.swadhyaa.data.repository.Upakhyan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MahabharataUiState(
    val parbas: List<ParbaInfo> = MahabharataManifest.PARBAS,
    val selectedParba: ParbaInfo = MahabharataManifest.PARBAS.first(),
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    val downloadError: String? = null,
    val adhyayas: List<Adhyay> = emptyList(),
    val selectedAdhyay: Adhyay? = null,
    val upakhyanas: List<Upakhyan> = emptyList(),
    val loadingContent: Boolean = false,
    val error: String? = null
)

class MahabharataViewModel(private val appContext: Context) : ViewModel() {

    private val _state = MutableStateFlow(MahabharataUiState())
    val state: StateFlow<MahabharataUiState> = _state.asStateFlow()

    init {
        selectParba(_state.value.selectedParba)
    }

    fun selectParba(parba: ParbaInfo) {
        _state.value = _state.value.copy(
            selectedParba = parba,
            downloaded = MahabharataRepository.isDownloaded(appContext, parba),
            adhyayas = emptyList(),
            selectedAdhyay = null,
            upakhyanas = emptyList(),
            downloadError = null,
            error = null
        )
        if (_state.value.downloaded) loadAdhyayas(parba)
    }

    fun download() {
        val parba = _state.value.selectedParba
        _state.value = _state.value.copy(downloading = true, downloadError = null)
        viewModelScope.launch {
            val result = MahabharataRepository.downloadIfNeeded(appContext, parba)
            if (result.isSuccess) {
                _state.value = _state.value.copy(downloading = false, downloaded = true)
                loadAdhyayas(parba)
            } else {
                _state.value = _state.value.copy(
                    downloading = false,
                    downloadError = result.exceptionOrNull()?.message ?: "ডাউনলোড ব্যর্থ হয়েছে"
                )
            }
        }
    }

    private fun loadAdhyayas(parba: ParbaInfo) {
        viewModelScope.launch {
            val result = MahabharataRepository.getAdhyayas(appContext, parba)
            result.onSuccess { list ->
                _state.value = _state.value.copy(adhyayas = list)
                list.firstOrNull()?.let { selectAdhyay(it) }
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "অধ্যায় পড়া যায়নি")
            }
        }
    }

    fun selectAdhyay(adhyay: Adhyay) {
        _state.value = _state.value.copy(selectedAdhyay = adhyay, loadingContent = true, upakhyanas = emptyList())
        viewModelScope.launch {
            val result = MahabharataRepository.getUpakhyanas(appContext, _state.value.selectedParba, adhyay.id)
            result.onSuccess { list ->
                _state.value = _state.value.copy(loadingContent = false, upakhyanas = list)
            }.onFailure {
                _state.value = _state.value.copy(
                    loadingContent = false,
                    error = it.message ?: "বিষয়বস্তু পড়া যায়নি"
                )
            }
        }
    }

    fun nextAdhyay() {
        val list = _state.value.adhyayas
        val idx = list.indexOf(_state.value.selectedAdhyay)
        if (idx in 0 until list.size - 1) selectAdhyay(list[idx + 1])
    }

    fun prevAdhyay() {
        val list = _state.value.adhyayas
        val idx = list.indexOf(_state.value.selectedAdhyay)
        if (idx > 0) selectAdhyay(list[idx - 1])
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MahabharataViewModel(appContext) as T
    }
}
