package com.kyronix.swadhyaa.presentation.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.repository.LibraryBookInfo
import com.kyronix.swadhyaa.data.repository.LibraryBookWithStatus
import com.kyronix.swadhyaa.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LibraryUiState {
    data object Loading : LibraryUiState()
    data class Success(val books: List<LibraryBookWithStatus>) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

class LibraryViewModel(private val appContext: Context) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** bookId -> current progress message, for books actively downloading. */
    private val _downloadProgress = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, String>> = _downloadProgress.asStateFlow()

    /** One-shot-ish error surface for a failed download/delete — Activity clears it after showing. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            LibraryRepository.getCatalog(appContext)
                .onSuccess { _uiState.value = LibraryUiState.Success(it) }
                .onFailure { _uiState.value = LibraryUiState.Error(it.message ?: "লোড ব্যর্থ হয়েছে") }
        }
    }

    fun download(book: LibraryBookInfo) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (book.id to "শুরু হচ্ছে…")
            LibraryRepository.download(appContext, book) { msg ->
                _downloadProgress.value = _downloadProgress.value + (book.id to msg)
            }.onSuccess {
                _downloadProgress.value = _downloadProgress.value - book.id
                load()
            }.onFailure { e ->
                _downloadProgress.value = _downloadProgress.value - book.id
                _actionError.value = e.message ?: "ডাউনলোড ব্যর্থ হয়েছে"
            }
        }
    }

    fun delete(book: LibraryBookInfo) {
        viewModelScope.launch {
            LibraryRepository.delete(appContext, book)
            load()
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                return LibraryViewModel(appContext.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
