package com.kyronix.swadhyaa.presentation.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.local.MasterDatabase
import com.kyronix.swadhyaa.data.local.entity.LibraryBookSelectionEntity
import com.kyronix.swadhyaa.data.prefs.UserPrefs
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
        val selectionsByParaSeq: Map<Int, List<LibraryBookSelectionEntity>> = emptyMap()
    ) : DbBookUiState()
    data class Error(val message: String) : DbBookUiState()
}

/**
 * ViewModel for the db-book reader.
 *
 * Bug fixes vs. previous version:
 *
 *  Bug 3 — "bookmark saved to Room but not visible in Bookmarks tab":
 *    The Bookmarks tab reads from UserPrefs.bookmarksFlow (DataStore JSON),
 *    not from Room. saveSelection() now also calls userPrefs.addBookmark()
 *    with kind="library" whenever kind=="bookmark", so it appears in the
 *    global Bookmarks tab immediately.
 *
 *    Bookmark fields:
 *      kind       = "library"
 *      corpusId   = 0  (library books don't have a numeric corpus id)
 *      itemId     = paraSeq  (used for dedup: same para = same bookmark)
 *      label      = chapterId  (shown as the bookmark's heading)
 *      snippet    = first 120 chars of selected text
 */
class LibraryDbBookReaderViewModel(
    private val appContext: Context,
    private val bookId: String,
    private val bookTitle: String = bookId
) : ViewModel() {

    private val _uiState = MutableStateFlow<DbBookUiState>(DbBookUiState.Loading)
    val uiState: StateFlow<DbBookUiState> = _uiState.asStateFlow()

    private val dao      by lazy { MasterDatabase.getInstance(appContext).masterDao() }
    private val userPrefs by lazy { UserPrefs(appContext) }

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
        if (current is DbBookUiState.Success) loadParagraphsFor(current.chapters, chapterId)
    }

    private fun loadParagraphsFor(chapters: List<LibraryChapter>, chapterId: String) {
        viewModelScope.launch {
            try {
                val paragraphs = LibraryDbBookRepository.getParagraphs(appContext, bookId, chapterId)
                val selections = dao.getSelectionsForChapter(bookId, chapterId)
                _uiState.value = DbBookUiState.Success(
                    chapters, chapterId, paragraphs,
                    selections.groupBy { it.paraSeq }
                )
            } catch (e: Exception) {
                _uiState.value = DbBookUiState.Error(e.message ?: "লোড ব্যর্থ হয়েছে")
            }
        }
    }

    /**
     * Saves a highlight or bookmark.
     *
     * For kind = "bookmark":
     *  1. Writes to Room (library_book_selections) — survives chapter nav
     *  2. Also writes to UserPrefs.bookmarksFlow — appears in Bookmarks tab
     */
    fun saveSelection(
        chapterId: String,
        paraSeq: Int,
        selStart: Int,
        selEnd: Int,
        selectedText: String,
        kind: String        // "highlight" or "bookmark"
    ) {
        viewModelScope.launch {
            try {
                // 1. Room — persistent, chapter-scoped highlight/bookmark
                dao.insertSelection(LibraryBookSelectionEntity(
                    bookId       = bookId,
                    chapterId    = chapterId,
                    paraSeq      = paraSeq,
                    selStart     = selStart,
                    selEnd       = selEnd,
                    selectedText = selectedText,
                    kind         = kind
                ))

                // 2. UserPrefs — only for bookmarks; makes them visible in
                //    the global Bookmarks tab (which reads bookmarksFlow)
                if (kind == "bookmark") {
                    val chapterLabel = (_uiState.value as? DbBookUiState.Success)
                        ?.chapters?.firstOrNull { it.chapterId == chapterId }
                        ?.heading?.takeIf { it.isNotBlank() }
                        ?: chapterId

                    userPrefs.addBookmark(UserPrefs.Bookmark(
                        kind      = "library",
                        corpusId  = 0,
                        itemId    = paraSeq,
                        label     = "$bookTitle — $chapterLabel",
                        snippet   = selectedText.take(120)
                    ))
                }

                refreshSelections(chapterId)
            } catch (_: Exception) {}
        }
    }

    fun deleteSelection(id: Int, chapterId: String) {
        viewModelScope.launch {
            try { dao.deleteSelection(id); refreshSelections(chapterId) }
            catch (_: Exception) {}
        }
    }

    private suspend fun refreshSelections(chapterId: String) {
        val current = _uiState.value as? DbBookUiState.Success ?: return
        val selections = dao.getSelectionsForChapter(bookId, chapterId)
        _uiState.value = current.copy(selectionsByParaSeq = selections.groupBy { it.paraSeq })
    }

    class Factory(
        private val appContext: Context,
        private val bookId: String,
        private val bookTitle: String = bookId
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryDbBookReaderViewModel::class.java))
                return LibraryDbBookReaderViewModel(appContext.applicationContext, bookId, bookTitle) as T
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
