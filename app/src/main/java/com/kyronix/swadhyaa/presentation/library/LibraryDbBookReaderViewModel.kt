package com.kyronix.swadhyaa.presentation.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.local.MasterDatabase
import com.kyronix.swadhyaa.data.local.entity.LibraryBookSelectionEntity
import com.kyronix.swadhyaa.data.repository.LibraryChapter
import com.kyronix.swadhyaa.data.repository.LibraryDbBookRepository
import com.kyronix.swadhyaa.data.repository.LibraryParagraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DbBookUiState {
    data object Loading : DbBookUiState()
    data class Success(
        val chapters: List<LibraryChapter>,
        val selectedChapterId: String,
        val paragraphs: List<LibraryParagraph>,
        /** selections keyed by para_seq for O(1) lookup at render time */
        val selectionsByParaSeq: Map<Int, List<LibraryBookSelectionEntity>> = emptyMap()
    ) : DbBookUiState()
    data class Error(val message: String) : DbBookUiState()
}

class LibraryDbBookReaderViewModel(
    private val appContext: Context,
    private val bookId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<DbBookUiState>(DbBookUiState.Loading)
    val uiState: StateFlow<DbBookUiState> = _uiState.asStateFlow()

    private val dao by lazy { MasterDatabase.getInstance(appContext).masterDao() }

    init { loadChapters() }

    private fun loadChapters() {
        viewModelScope.launch {
            _uiState.value = DbBookUiState.Loading
            try {
                val chapters = LibraryDbBookRepository.getChapters(appContext, bookId)
                if (chapters.isEmpty()) {
                    _uiState.value = DbBookUiState.Error("এই বইয়ের কোনো অধ্যায় পাওয়া যায়নি।")
                    return@launch
                }
                loadParagraphsFor(chapters, chapters.first().chapterId)
            } catch (e: Exception) {
                _uiState.value = DbBookUiState.Error(e.message ?: "লোড ব্যর্থ হয়েছে")
            }
        }
    }

    fun selectChapter(chapterId: String) {
        val current = _uiState.value
        if (current is DbBookUiState.Success) {
            loadParagraphsFor(current.chapters, chapterId)
        }
    }

    private fun loadParagraphsFor(chapters: List<LibraryChapter>, chapterId: String) {
        viewModelScope.launch {
            try {
                val paragraphs = LibraryDbBookRepository.getParagraphs(appContext, bookId, chapterId)
                val selections = dao.getSelectionsForChapter(bookId, chapterId)
                val selMap = selections.groupBy { it.paraSeq }
                _uiState.value = DbBookUiState.Success(chapters, chapterId, paragraphs, selMap)
            } catch (e: Exception) {
                _uiState.value = DbBookUiState.Error(e.message ?: "লোড ব্যর্থ হয়েছে")
            }
        }
    }

    /** Save a highlight or bookmark; then reload selections so the UI refreshes. */
    fun saveSelection(
        chapterId: String,
        paraSeq: Int,
        selStart: Int,
        selEnd: Int,
        selectedText: String,
        kind: String  // "highlight" or "bookmark"
    ) {
        viewModelScope.launch {
            try {
                dao.insertSelection(LibraryBookSelectionEntity(
                    bookId = bookId, chapterId = chapterId, paraSeq = paraSeq,
                    selStart = selStart, selEnd = selEnd,
                    selectedText = selectedText, kind = kind
                ))
                // Refresh selections without reloading full paragraph list
                refreshSelections(chapterId)
            } catch (_: Exception) {}
        }
    }

    /** Delete a specific selection by its DB id. */
    fun deleteSelection(id: Int, chapterId: String) {
        viewModelScope.launch {
            try {
                dao.deleteSelection(id)
                refreshSelections(chapterId)
            } catch (_: Exception) {}
        }
    }

    private suspend fun refreshSelections(chapterId: String) {
        val current = _uiState.value as? DbBookUiState.Success ?: return
        val selections = dao.getSelectionsForChapter(bookId, chapterId)
        _uiState.value = current.copy(selectionsByParaSeq = selections.groupBy { it.paraSeq })
    }

    class Factory(private val appContext: Context, private val bookId: String) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryDbBookReaderViewModel::class.java))
                return LibraryDbBookReaderViewModel(appContext.applicationContext, bookId) as T
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
