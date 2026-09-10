package com.kyronix.swadhyaa.presentation.gita

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import com.kyronix.swadhyaa.data.repository.BhashyaField
import com.kyronix.swadhyaa.data.repository.GitaBhashyaRepository
import com.kyronix.swadhyaa.data.repository.GitaCoreTextRepository
import com.kyronix.swadhyaa.data.repository.GitaManifest
import com.kyronix.swadhyaa.data.repository.GitaScholarInfo
import com.kyronix.swadhyaa.data.repository.GitaShloka
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GitaUiState(
    // ── Base text download gate ─────────────────────────────────────────
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: String? = null,
    val downloadError: String? = null,
    // ── Verse + navigation ───────────────────────────────────────────────
    val loading: Boolean = true,
    val error: String? = null,
    val current: GitaShloka? = null,
    val adhyayaOptions: List<Int> = GitaCoreTextRepository.ADHYAYA_OPTIONS,
    val shlokaOptions: List<Int> = emptyList(),
    // ── Bhashya ──────────────────────────────────────────────────────────
    val scholarsByLang: Map<String, List<GitaScholarInfo>> = emptyMap(),
    val availableLanguages: List<String> = emptyList(),
    val selectedLanguage: String = "",
    val selectedScholar: GitaScholarInfo? = null,
    val scholarDownloadStatus: Map<String, Boolean> = emptyMap(),
    val bhashyaContent: List<BhashyaField> = emptyList(),
    val bhashyaLoading: Boolean = false,
    val bhashyaError: String? = null,
    val bhashyaDownloadProgress: String? = null
)

/**
 * NOTE on scholar availability: unlike Veda ([com.kyronix.swadhyaa.presentation.reader.ReaderViewModel]
 * calls `getScholarsForMantra`, filtered by a `bhashya_presence` table), Gita
 * has no per-verse presence data — [GitaManifest.SCHOLARS] is a fixed list
 * shown for every verse regardless of whether that scholar actually wrote on
 * it. A scholar with nothing for the current verse just shows an empty panel
 * (see [GitaBhashyaRepository.getBhashya]'s empty-list-not-failure contract)
 * rather than being hidden from the list. Revisit if/when presence data
 * becomes available for these packs.
 */
class GitaViewModel(private val appContext: Context) : ViewModel() {

    private val _state = MutableStateFlow(GitaUiState())
    val state: StateFlow<GitaUiState> = _state.asStateFlow()

    init {
        val downloaded = GitaCoreTextRepository.isDownloaded(appContext)
        _state.value = _state.value.copy(downloaded = downloaded, loading = downloaded)
        if (downloaded) openFirstVerse()
    }

    // ── Base text download gate ──────────────────────────────────────────

    fun downloadCoreText() {
        _state.value = _state.value.copy(downloading = true, downloadError = null)
        viewModelScope.launch {
            GitaCoreTextRepository.downloadIfNeeded(appContext) { dl, total ->
                val pct = if (total > 0) (dl * 100 / total).toInt() else 0
                _state.value = _state.value.copy(downloadProgress = "$pct%")
            }.onSuccess {
                _state.value = _state.value.copy(
                    downloading = false, downloaded = true, downloadProgress = null, loading = true
                )
                openFirstVerse()
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    downloading = false, downloadProgress = null,
                    downloadError = e.message ?: "ডাউনলোড ব্যর্থ হয়েছে"
                )
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────

    private fun openFirstVerse() {
        viewModelScope.launch {
            GitaCoreTextRepository.getFirstVerse(appContext)
                .onSuccess { verse ->
                    if (verse != null) applyVerse(verse)
                    else _state.value = _state.value.copy(loading = false, error = "গীতার কোনো শ্লোক পাওয়া যায়নি")
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "লোড ব্যর্থ") }
        }
    }

    fun next() {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            GitaCoreTextRepository.getNext(appContext, cur.adhyaya, cur.shloka)
                .onSuccess { verse -> verse?.let { applyVerse(it) } }
        }
    }

    fun prev() {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            GitaCoreTextRepository.getPrev(appContext, cur.adhyaya, cur.shloka)
                .onSuccess { verse -> verse?.let { applyVerse(it) } }
        }
    }

    /** Jumps to a chosen অধ্যায় — lands on its first available শ্লোক. */
    fun jumpAdhyaya(adhyaya: Int) {
        viewModelScope.launch {
            val firstShloka = GitaCoreTextRepository.getShlokaOptions(appContext, adhyaya)
                .getOrDefault(emptyList()).firstOrNull() ?: 1
            GitaCoreTextRepository.getVerse(appContext, adhyaya, firstShloka)
                .onSuccess { verse -> verse?.let { applyVerse(it) } }
        }
    }

    fun jumpShloka(shloka: Int) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            GitaCoreTextRepository.getVerse(appContext, cur.adhyaya, shloka)
                .onSuccess { verse ->
                    if (verse != null) applyVerse(verse)
                    else _state.value = _state.value.copy(error = "শ্লোক নং $shloka পাওয়া যায়নি")
                }
        }
    }

    // ── Bhashya selection ─────────────────────────────────────────────────

    fun selectLanguage(lang: String) {
        _state.value = _state.value.copy(
            selectedLanguage = lang,
            selectedScholar = null,
            bhashyaContent = emptyList(),
            bhashyaError = null,
            bhashyaDownloadProgress = null
        )
    }

    fun selectScholar(scholar: GitaScholarInfo) {
        val cur = _state.value.current ?: return
        val isDownloaded = _state.value.scholarDownloadStatus[scholar.id] == true
        _state.value = _state.value.copy(
            selectedScholar = scholar,
            bhashyaContent = emptyList(),
            bhashyaError = null,
            bhashyaDownloadProgress = null
        )
        if (isDownloaded) loadBhashyaContent(scholar, cur.adhyaya, cur.shloka)
    }

    fun downloadScholar(scholar: GitaScholarInfo) {
        val cur = _state.value.current ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(bhashyaDownloadProgress = "শুরু হচ্ছে…")
            GitaBhashyaRepository.downloadIfNeeded(appContext, scholar) { dl, total ->
                val pct = if (total > 0) (dl * 100 / total).toInt() else 0
                _state.value = _state.value.copy(bhashyaDownloadProgress = "$pct%")
            }.onSuccess {
                // A pack can back more than one row (e.g. sankar_en/hi/sa share one
                // file) — mark every row sharing it downloaded, matching what's
                // actually true on disk after this single download.
                val sharedIds = GitaManifest.SCHOLARS.filter { it.packFile == scholar.packFile }.map { it.id }
                val updated = _state.value.scholarDownloadStatus.toMutableMap()
                sharedIds.forEach { updated[it] = true }
                _state.value = _state.value.copy(scholarDownloadStatus = updated, bhashyaDownloadProgress = null)
                loadBhashyaContent(scholar, cur.adhyaya, cur.shloka)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    bhashyaDownloadProgress = null,
                    bhashyaError = "ডাউনলোড ব্যর্থ: ${e.message}"
                )
            }
        }
    }

    fun deleteScholar(scholar: GitaScholarInfo) {
        // Same one-file-backs-several-rows caveat as downloadScholar above,
        // applied in reverse: deleting the file un-downloads every row sharing it.
        PackDownloadManager.deleteLocalPack(appContext, scholar.packFile)
        val sharedIds = GitaManifest.SCHOLARS.filter { it.packFile == scholar.packFile }.map { it.id }
        val updated = _state.value.scholarDownloadStatus.toMutableMap()
        sharedIds.forEach { updated[it] = false }
        _state.value = _state.value.copy(
            scholarDownloadStatus = updated,
            selectedScholar = null,
            bhashyaContent = emptyList(),
            bhashyaError = null
        )
    }

    private fun loadBhashyaContent(scholar: GitaScholarInfo, adhyaya: Int, shloka: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(bhashyaLoading = true, bhashyaError = null)
            GitaBhashyaRepository.getBhashya(appContext, scholar, adhyaya, shloka)
                .onSuccess { fields ->
                    _state.value = _state.value.copy(bhashyaContent = fields, bhashyaLoading = false)
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

    /**
     * Applies a newly-loaded verse AND carries the open bhashya selection
     * forward: moving to the next/prev verse (or jumping) keeps showing
     * whichever scholar was open, instead of resetting to the bare list —
     * this was a reported UX bug in the Veda reader
     * ([com.kyronix.swadhyaa.presentation.reader.ReaderViewModel.applyMantra])
     * fixed there at the same time this was written; Gita is built with the
     * fix from the start rather than reproducing the bug.
     *
     * Gita's scholar list is static (see class doc comment), so "still
     * exists under the new verse" is always true once found by id — unlike
     * Veda, where a scholar can legitimately disappear because they have no
     * `bhashya_presence` row for the new mantra.
     */
    private suspend fun applyVerse(verse: GitaShloka) {
        val shlokaOpts = GitaCoreTextRepository.getShlokaOptions(appContext, verse.adhyaya)
            .getOrDefault(emptyList())

        val byLang = GitaManifest.SCHOLARS.groupBy { it.language }.toSortedMap()
        val langs = byLang.keys.toList()
        val dlStatus = GitaManifest.SCHOLARS.associate { it.id to GitaBhashyaRepository.isDownloaded(appContext, it) }

        val prevLang = _state.value.selectedLanguage
        val newLang = if (prevLang.isNotEmpty() && byLang.containsKey(prevLang)) prevLang
                      else langs.firstOrNull() ?: ""

        val prevScholarId = _state.value.selectedScholar?.id
        val carriedScholar = prevScholarId?.let { GitaManifest.byId(it) }

        _state.value = _state.value.copy(
            loading = false, error = null,
            current = verse,
            shlokaOptions = shlokaOpts,
            scholarsByLang = byLang,
            availableLanguages = langs,
            selectedLanguage = newLang,
            selectedScholar = carriedScholar,
            bhashyaContent = emptyList(),
            bhashyaError = null,
            scholarDownloadStatus = dlStatus
        )

        // "Still open" means still showing content for the NEW verse, not just
        // still highlighted in the list — re-fetch rather than leave it blank.
        if (carriedScholar != null && dlStatus[carriedScholar.id] == true) {
            loadBhashyaContent(carriedScholar, verse.adhyaya, verse.shloka)
        }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GitaViewModel(appContext) as T
    }
}
