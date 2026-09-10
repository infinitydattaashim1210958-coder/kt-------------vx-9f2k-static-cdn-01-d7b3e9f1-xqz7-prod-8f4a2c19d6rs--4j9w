package com.kyronix.swadhyaa.presentation.shell

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.RamayanaCoreDatabase
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.data.prefs.UserPrefs
import com.kyronix.swadhyaa.data.repository.SearchRepository
import com.kyronix.swadhyaa.data.repository.VedaRepository
import com.kyronix.swadhyaa.data.repository.LibraryRepository
import com.kyronix.swadhyaa.data.repository.LibraryBookInfo
import com.kyronix.swadhyaa.data.repository.LibraryBookStatus
import com.kyronix.swadhyaa.data.repository.LibraryBookWithStatus
import com.kyronix.swadhyaa.presentation.library.LibraryDbBookReaderActivity
import com.kyronix.swadhyaa.domain.model.VedaSummary
import com.kyronix.swadhyaa.presentation.mahabharata.MahabharataActivity
import com.kyronix.swadhyaa.presentation.ramayana.RamayanaActivity
import com.kyronix.swadhyaa.presentation.reader.ReaderActivity
import com.kyronix.swadhyaa.ui.theme.AppColors
import com.kyronix.swadhyaa.ui.theme.FontManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App shell: Home · Library · Bookmarks · Search · Settings
 * Implements M8 A–D foundation on one activity (phone-friendly).
 */
class ShellActivity : AppCompatActivity() {

    private enum class Tab { HOME, LIBRARY, BOOKMARKS, SEARCH, SETTINGS }

    private lateinit var content: LinearLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var prefs: UserPrefs
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var vedaRepo: VedaRepository
    private lateinit var searchRepo: SearchRepository

    private var current = Tab.HOME
    private var searchJob: Job? = null
    private var renderJob: Job? = null
    private var settingsScreen: String? = null
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = UserPrefs(this)
        settingsRepo = SettingsRepository(this)
        val core = CoreDatabase.getInstance(this)
        val ram = RamayanaCoreDatabase.getInstance(this)
        vedaRepo = VedaRepository(core)
        searchRepo = SearchRepository(core, ram)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bg)
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(content)
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(AppColors.surface)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(scroll)
        root.addView(tabBar)
        setContentView(root)

        buildTabs()
        show(Tab.HOME)
        justCreated = true
    }

    private var justCreated = false

    override fun onResume() {
        super.onResume()
        if (justCreated) {
            // onCreate already rendered the current tab; skip the
            // redundant re-render Android triggers on first resume.
            justCreated = false
            return
        }
        if (current == Tab.HOME || current == Tab.BOOKMARKS) show(current)
    }

    private fun buildTabs() {
        tabBar.removeAllViews()
        listOf(
            Tab.HOME to "হোম",
            Tab.LIBRARY to "লাইব্রেরি",
            Tab.BOOKMARKS to "বুকমার্ক",
            Tab.SEARCH to "খুঁজুন",
            Tab.SETTINGS to "সেটিংস"
        ).forEach { (tab, label) ->
            val t = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setTextColor(if (tab == current) AppColors.saffron else AppColors.muted)
                setOnClickListener { show(tab) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            tabBar.addView(t)
        }
    }

    private fun show(tab: Tab) {
        current = tab
        buildTabs()
        renderJob?.cancel()
        content.removeAllViews()
        when (tab) {
            Tab.HOME -> renderJob = lifecycleScope.launch { renderHome() }
            Tab.LIBRARY -> renderLibrary()
            Tab.BOOKMARKS -> renderBookmarks()
            Tab.SEARCH -> renderSearch()
            Tab.SETTINGS -> { settingsScreen = null; lifecycleScope.launch { renderSettings() } }
        }
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(AppColors.ivory)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(8))
    }

    private fun subtitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(AppColors.gold)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, 0, 0, dp(16))
    }

    private fun card(block: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.surface)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            layoutParams = lp
            block()
        }
    }

    private data class HomeSection(
        val icon: String,
        val label: String,
        val action: HomeAction
    )

    private sealed class HomeAction {
        data object Vedas : HomeAction()
        data object Ramayana : HomeAction()
        data object Mahabharata : HomeAction()
        data object Library : HomeAction()
        data object Soon : HomeAction()
    }

    // 1:1 port of HOME_SECTIONS from the old app's www/js/app.js
    private val homeSections = listOf(
        HomeSection("🕉", "বেদ", HomeAction.Vedas),
        HomeSection("🏹", "রামায়ণ", HomeAction.Ramayana),
        HomeSection("⚔️", "মহাভারত", HomeAction.Mahabharata),
        HomeSection("📖", "পুরাণ", HomeAction.Soon),
        HomeSection("🔥", "ব্রাহ্মণ", HomeAction.Soon),
        HomeSection("🪔", "উপনিষদ", HomeAction.Soon),
        HomeSection("🌳", "আরণ্যক", HomeAction.Soon),
        HomeSection("🔤", "নিরুক্তশাস্ত্র", HomeAction.Soon),
        HomeSection("🎵", "ছন্দশাস্ত্র", HomeAction.Soon),
        HomeSection("🎓", "শিক্ষাশাস্ত্র", HomeAction.Soon),
        HomeSection("🕯", "তন্ত্র", HomeAction.Soon),
        HomeSection("📜", "স্মৃতি", HomeAction.Soon),
        HomeSection("🏠", "গৃহ্যসূত্র", HomeAction.Soon),
        HomeSection("⚖️", "ধর্মসূত্র", HomeAction.Soon),
        HomeSection("📚", "ডিজিটাল লাইব্রেরি", HomeAction.Library)
    )

    private var homeVedaSummaries: List<VedaSummary> = emptyList()

    private suspend fun renderHome() {
        content.addView(title("স্বাধ্যায়"))
        content.addView(subtitle("ও৩ম্ কৃণ্বন্তো বিশ্বমার্যম্"))

        // Continue reading — kept from the shell's existing behavior
        val cont = prefs.continueFlow.first()
        if (cont != null) {
            content.addView(card {
                addView(TextView(this@ShellActivity).apply {
                    text = "আপনার পড়া চালিয়ে যান"
                    setTextColor(AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
                addView(TextView(this@ShellActivity).apply {
                    text = cont.label
                    setTextColor(AppColors.ivory)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setPadding(0, dp(4), 0, 0)
                })
                setOnClickListener {
                    if (cont.kind == "veda") {
                        startActivity(
                            Intent(this@ShellActivity, ReaderActivity::class.java)
                                .putExtra(ReaderActivity.EXTRA_VEDA_ID, cont.corpusId)
                        )
                    }
                }
            })
        }

        try {
            homeVedaSummaries = vedaRepo.getVedaSummaries()
        } catch (e: Exception) {
            homeVedaSummaries = emptyList()
        }

        val grid = GridLayout(this).apply {
            columnCount = 3
            useDefaultMargins = false
        }
        homeSections.forEachIndexed { index, section ->
            val enabled = section.action != HomeAction.Soon
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(AppColors.surface)
                setPadding(dp(8), dp(16), dp(8), dp(16))
                alpha = if (enabled) 1f else 0.4f
                setOnClickListener { onHomeSectionTap(section) }
            }
            tile.addView(TextView(this).apply {
                text = section.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                gravity = Gravity.CENTER
            })
            tile.addView(TextView(this).apply {
                text = section.label
                setTextColor(if (enabled) AppColors.ivory else AppColors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            if (!enabled) {
                tile.addView(TextView(this).apply {
                    text = "শীঘ্রই"
                    setTextColor(AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                    gravity = Gravity.CENTER
                })
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(index % 3, 1f)
                rowSpec = GridLayout.spec(index / 3)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(tile, params)
        }
        content.addView(grid)
    }

    private fun onHomeSectionTap(section: HomeSection) {
        when (section.action) {
            HomeAction.Vedas -> {
                // Go directly to the reader — Rigveda by default, or last
                // loaded summary if available. The reader's own veda chips
                // (Rigveda / Yajurveda / Samaveda / Atharvaveda) let the
                // user switch vedas once inside, so no home-screen picker needed.
                val vedaId = homeVedaSummaries.firstOrNull()?.id ?: 1
                startActivity(
                    Intent(this, ReaderActivity::class.java)
                        .putExtra(ReaderActivity.EXTRA_VEDA_ID, vedaId)
                )
            }
            HomeAction.Ramayana -> startActivity(Intent(this, RamayanaActivity::class.java))
            HomeAction.Mahabharata -> startActivity(Intent(this, MahabharataActivity::class.java))
            HomeAction.Library -> show(Tab.LIBRARY)
            HomeAction.Soon ->
                Toast.makeText(this, "${section.label} শীঘ্রই আসছে", Toast.LENGTH_SHORT).show()
        }
    }


    // Tracks the in-flight library catalog load so it can be cancelled
    // if the user switches tabs before the network response arrives.
    private var libraryJob: Job? = null

    private fun renderLibrary() {
        libraryJob?.cancel()

        content.addView(title("লাইব্রেরি"))
        content.addView(subtitle("ডাউনলোডযোগ্য বই ও গ্রন্থ"))

        val loadingText = TextView(this).apply {
            text = "লোড হচ্ছে…"
            setTextColor(AppColors.muted)
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(loadingText)

        libraryJob = lifecycleScope.launch {
            val result = LibraryRepository.getCatalog(this@ShellActivity)

            content.removeView(loadingText)

            result.onFailure { err ->
                content.addView(TextView(this@ShellActivity).apply {
                    text = err.message ?: "বইয়ের তালিকা পাওয়া যায়নি"
                    setTextColor(AppColors.vermilion)
                    setPadding(0, dp(12), 0, 0)
                })
                return@launch
            }

            val books = result.getOrThrow()
                .sortedWith(compareBy { it.info.title })

            content.addView(TextView(this@ShellActivity).apply {
                text = "${books.size}টা বই"
                setTextColor(AppColors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, 0, 0, dp(8))
            })

            books.forEach { bws -> renderLibraryBookCard(bws) }
        }
    }

    private fun renderLibraryBookCard(bws: LibraryBookWithStatus) {
        val book = bws.info
        val downloaded = bws.status == LibraryBookStatus.DOWNLOADED

        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = book.title
                setTextColor(AppColors.ivory)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, 0, 0, dp(10))
            })

            val btnRow = LinearLayout(this@ShellActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val progressText = TextView(this@ShellActivity).apply {
                text = ""
                setTextColor(AppColors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(12), dp(8), 0, 0)
            }

            val actionBtn = TextView(this@ShellActivity).apply {
                text = if (downloaded) "পড়ুন" else "ডাউনলোড"
                setTextColor(AppColors.bg)
                setBackgroundColor(AppColors.gold)
                setPadding(dp(18), dp(8), dp(18), dp(8))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }

            actionBtn.setOnClickListener {
                if (downloaded) {
                    openLibraryBook(book)
                } else {
                    actionBtn.isEnabled = false
                    actionBtn.text = "শুরু হচ্ছে…"
                    lifecycleScope.launch {
                        LibraryRepository.download(this@ShellActivity, book) { msg ->
                            runOnUiThread { progressText.text = msg }
                        }.onSuccess {
                            show(Tab.LIBRARY)
                        }.onFailure { err ->
                            runOnUiThread {
                                actionBtn.isEnabled = true
                                actionBtn.text = "ডাউনলোড"
                                progressText.text = "ব্যর্থ: ${err.message}"
                            }
                        }
                    }
                }
            }

            btnRow.addView(actionBtn)
            btnRow.addView(progressText)
            addView(btnRow)
        })
    }

    private fun openLibraryBook(book: LibraryBookInfo) {
        when (book.type) {
            "db" -> startActivity(
                Intent(this, LibraryDbBookReaderActivity::class.java)
                    .putExtra(LibraryDbBookReaderActivity.EXTRA_BOOK_ID, book.id)
                    .putExtra(LibraryDbBookReaderActivity.EXTRA_BOOK_TITLE, book.title)
            )
            else -> lifecycleScope.launch {
                val uri = LibraryRepository.getHtmlShareableUri(this@ShellActivity, book)
                if (uri != null) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } else {
                    Toast.makeText(this@ShellActivity, "ফাইল খোলা যাচ্ছে না", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderBookmarks() {
        content.addView(title("বুকমার্ক"))
        content.addView(subtitle("Saved wisdom"))
        lifecycleScope.launch {
            val list = prefs.bookmarksFlow.first()
            if (list.isEmpty()) {
                content.addView(TextView(this@ShellActivity).apply {
                    text = "এখনো কিছু নেই।\nরিডারে বুকমার্ক চাপুন।"
                    setTextColor(AppColors.muted)
                    setPadding(0, dp(24), 0, 0)
                })
            } else {
                list.forEach { b ->
                    content.addView(card {
                        addView(TextView(this@ShellActivity).apply {
                            text = b.label
                            setTextColor(AppColors.saffron)
                        })
                        addView(TextView(this@ShellActivity).apply {
                            text = b.snippet
                            setTextColor(AppColors.ivory)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        })
                        setOnClickListener {
                            if (b.kind == "veda") {
                                startActivity(
                                    Intent(this@ShellActivity, ReaderActivity::class.java)
                                        .putExtra(ReaderActivity.EXTRA_VEDA_ID, b.corpusId)
                                )
                            }
                        }
                        setOnLongClickListener {
                            lifecycleScope.launch {
                                prefs.removeBookmark(b.kind, b.itemId)
                                show(Tab.BOOKMARKS)
                            }
                            true
                        }
                    })
                }
            }
        }
    }

    private fun renderSearch() {
        content.addView(title("খুঁজুন"))
        content.addView(subtitle("Offline · Veda + Rāmāyaṇa"))
        val input = EditText(this).apply {
            hint = "সন্ধান… (অগ্নি / तपः …)"
            setHintTextColor(AppColors.muted)
            setTextColor(AppColors.ivory)
            setBackgroundColor(AppColors.elevated)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(input)
        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(results)

        fun runSearch(q: String) {
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                results.removeAllViews()
                if (q.trim().length < 2) return@launch
                results.addView(TextView(this@ShellActivity).apply {
                    text = "Searching…"
                    setTextColor(AppColors.muted)
                })
                try {
                    val hits = searchRepo.search(q.trim())
                    results.removeAllViews()
                    if (hits.isEmpty()) {
                        results.addView(TextView(this@ShellActivity).apply {
                            text = "কিছু পাওয়া যায়নি"
                            setTextColor(AppColors.muted)
                        })
                    } else {
                        hits.forEach { h ->
                            results.addView(card {
                                addView(TextView(this@ShellActivity).apply {
                                    text = h.label
                                    setTextColor(AppColors.saffron)
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                })
                                addView(TextView(this@ShellActivity).apply {
                                    text = h.snippet
                                    setTextColor(AppColors.ivory)
                                })
                                setOnClickListener {
                                    if (h.kind == "veda") {
                                        startActivity(
                                            Intent(this@ShellActivity, ReaderActivity::class.java)
                                                .putExtra(ReaderActivity.EXTRA_VEDA_ID, h.corpusId)
                                        )
                                    }
                                }
                            })
                        }
                    }
                } catch (e: Exception) {
                    results.removeAllViews()
                    results.addView(TextView(this@ShellActivity).apply {
                        text = "Error: ${e.message}"
                        setTextColor(AppColors.vermilion)
                    })
                }
            }
        }

        input.setOnEditorActionListener { _, _, _ ->
            runSearch(input.text.toString())
            true
        }
    }


    // ── Settings navigation ────────────────────────────────────────────────

    private fun settingsSection(label: String) = TextView(this).apply {
        text = label
        setTextColor(AppColors.gold)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(16), 0, dp(4))
    }

    private fun settingsRow(icon: String, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(AppColors.surface)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
            layoutParams = lp
            addView(TextView(this@ShellActivity).apply {
                text = "$icon  $label"
                setTextColor(AppColors.ivory)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@ShellActivity).apply {
                text = "›"
                setTextColor(AppColors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun settingsSubHeader(label: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        row.addView(TextView(this).apply {
            text = "← "
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setOnClickListener {
                settingsScreen = null
                content.removeAllViews()
                lifecycleScope.launch { renderSettings() }
            }
        })
        row.addView(TextView(this).apply {
            text = label
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(row)
    }

    private fun showSettingsScreen(screen: String) {
        settingsScreen = screen
        content.removeAllViews()
        lifecycleScope.launch {
            when (screen) {
                "reader_prefs"      -> renderReaderPrefs()
                "library_storage"   -> renderLibraryStorage()
                "language"          -> renderLanguageSettings()
                "notifications"     -> renderNotificationSettings()
                "search_settings"   -> renderSearchSettings()
                "privacy"           -> renderStaticPage("গোপনীয়তা নীতি", "স্বাধ্যায় আপনার কোনো ব্যক্তিগত ডেটা সংগ্রহ বা সংরক্ষণ করে না। সমস্ত পড়ার ইতিহাস এবং সেটিংস শুধুমাত্র আপনার ডিভাইসে সংরক্ষিত হয়।\n\nএই অ্যাপটি সম্পূর্ণ অফলাইনে কাজ করে।")
                "terms"             -> renderStaticPage("শর্তাবলি", "এই অ্যাপটি শুধুমাত্র শিক্ষামূলক, আধ্যাত্মিক এবং গবেষণার উদ্দেশ্যে ব্যবহারের জন্য। বিষয়বস্তু Kyronix Innovation Group (KIG) দ্বারা সংকলিত এবং সংরক্ষিত।")
                "disclaimer"        -> renderStaticPage("দায়মুক্তি", "এই অ্যাপের বৈদিক ভাষ্য ও অনুবাদ বিভিন্ন পণ্ডিতদের কাজ থেকে নেওয়া। মূল গ্রন্থের নির্ভুলতার জন্য সর্বদা মূল সংস্কৃত পাঠ যাচাই করুন।")
                "licenses"          -> renderStaticPage("ওপেন সোর্স লাইসেন্স", "এই অ্যাপ নিম্নলিখিত ওপেন সোর্স লাইব্রেরি ব্যবহার করে:\n\n• Room Database (Apache 2.0)\n• OkHttp (Apache 2.0)\n• Noto Fonts (SIL Open Font License)\n• Tiro Devanagari (SIL Open Font License)\n• Rozha One (SIL Open Font License)\n• Eczar (SIL Open Font License)")
                "contact"           -> renderStaticPage("যোগাযোগ করুন", "ওয়েবসাইট: arsa-siddanto.blogspot.com\n\nডেভেলপার: Ashim Datta\nFounder & CEO, Kyronix Innovation Group (KIG)\n\nবাগ রিপোর্ট ও ফিডব্যাকের জন্য নিচের বিকল্পগুলো ব্যবহার করুন।")
                "feedback"          -> renderFeedbackForm()
                "bug_report"        -> renderBugReportForm()
                "faq"               -> renderStaticPage("সাহায্য ও প্রশ্নোত্তর", "প্র: ভাষ্য কোথায় পাবো?\nউ: রিডারে মন্ত্র দেখার সময় নিচে ভাষা ট্যাব থেকে ভাষ্য ডাউনলোড করুন।\n\nপ্র: অ্যাপ কি অফলাইনে কাজ করে?\nউ: হ্যাঁ। মূল ডেটাবেজ অ্যাপের সাথেই থাকে। শুধু ভাষ্য ও লাইব্রেরি ডাউনলোডের জন্য ইন্টারনেট দরকার।\n\nপ্র: বুকমার্ক কীভাবে করবো?\nউ: রিডারে "বুকমার্ক" বাটন চাপুন।")
                "about"             -> renderAbout()
            }
        }
    }

    private suspend fun renderSettings() {
        val settings = settingsRepo.settingsFlow.first()
        content.addView(title("সেটিংস"))

        // ── READER ────────────────────────────────────────────────────────
        content.addView(settingsSection("📖  READER"))
        content.addView(settingsRow("📖", "Reader Preferences") { showSettingsScreen("reader_prefs") })

        // ── LIBRARY ───────────────────────────────────────────────────────
        content.addView(settingsSection("📚  LIBRARY"))
        content.addView(settingsRow("📚", "Library & Storage") { showSettingsScreen("library_storage") })

        // ── LANGUAGE ──────────────────────────────────────────────────────
        content.addView(settingsSection("🌐  LANGUAGE"))
        content.addView(settingsRow("🌐", "Language Settings") { showSettingsScreen("language") })

        // ── NOTIFICATIONS ─────────────────────────────────────────────────
        content.addView(settingsSection("🔔  NOTIFICATIONS"))
        content.addView(settingsRow("🔔", "Notification Settings") { showSettingsScreen("notifications") })

        // ── SEARCH ────────────────────────────────────────────────────────
        content.addView(settingsSection("🔍  SEARCH"))
        content.addView(settingsRow("🔍", "Search Settings") { showSettingsScreen("search_settings") })

        // ── PRIVACY & LEGAL ───────────────────────────────────────────────
        content.addView(settingsSection("🛡  PRIVACY & LEGAL"))
        content.addView(settingsRow("📜", "Privacy Policy")       { showSettingsScreen("privacy") })
        content.addView(settingsRow("📋", "Terms & Conditions")   { showSettingsScreen("terms") })
        content.addView(settingsRow("⚖️", "Disclaimer")            { showSettingsScreen("disclaimer") })
        content.addView(settingsRow("🔓", "Open Source Licenses") { showSettingsScreen("licenses") })

        // ── SUPPORT ───────────────────────────────────────────────────────
        content.addView(settingsSection("🆘  SUPPORT"))
        content.addView(settingsRow("📧", "Contact Us")     { showSettingsScreen("contact") })
        content.addView(settingsRow("💡", "Send Feedback")  { showSettingsScreen("feedback") })
        content.addView(settingsRow("🐛", "Report a Bug")   { showSettingsScreen("bug_report") })
        content.addView(settingsRow("❓", "Help & FAQ")     { showSettingsScreen("faq") })

        // ── ABOUT ─────────────────────────────────────────────────────────
        content.addView(settingsSection("⭐  ABOUT"))
        content.addView(settingsRow("🕉", "About স্বাধ্যায়") { showSettingsScreen("about") })

        // Footer
        content.addView(TextView(this).apply {
            text = "স্বাধ্যায় v1.0.0 · Build 1\nDeveloped by Ashim Datta\n© Copyright & Preservation\nAll rights reserved. This digital work is protected\nand preserved for educational, spiritual and\nresearch purposes.\n\nKyronix Innovation Group (KIG)\nAshim Datta\nFounder & CEO"
            setTextColor(AppColors.mutedDim)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(8))
        })
    }

    // ── Settings sub-screens ───────────────────────────────────────────────

    private suspend fun renderReaderPrefs() {
        val settings = settingsRepo.settingsFlow.first()
        settingsSubHeader("Reader Preferences")

        // APPEARANCE
        content.addView(settingsSection("🎨  APPEARANCE"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = "Theme"
                setTextColor(AppColors.ivory)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, 0, dp(8))
            })
            val themeRow = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("auto" to "System Default", "dark" to "Dark", "light" to "Light").forEach { (k, v) ->
                themeRow.addView(TextView(this@ShellActivity).apply {
                    text = if (k == settings.theme) "● $v" else "○ $v"
                    setTextColor(if (k == settings.theme) AppColors.goldBright else AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, 0, dp(12), 0)
                    setOnClickListener { lifecycleScope.launch { settingsRepo.setTheme(k); showSettingsScreen("reader_prefs") } }
                })
            }
            addView(themeRow)
        })
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = "Accent Color"
                setTextColor(AppColors.ivory)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, 0, dp(8))
            })
            val row = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("gold" to "🌕 স্বর্ণালী (Gold)", "emerald" to "💚 এমারেল্ড", "indigo" to "💙 নীল", "violet" to "💜 বেগুনি").forEach { (k, v) ->
                row.addView(TextView(this@ShellActivity).apply {
                    text = if (k == settings.accentTheme) "● $v" else "○ $v"
                    setTextColor(if (k == settings.accentTheme) AppColors.goldBright else AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(4), dp(8), dp(4))
                    setOnClickListener {
                        lifecycleScope.launch {
                            settingsRepo.setAccentTheme(k)
                            AppColors.applyAccent(k)
                            recreate()
                        }
                    }
                })
            }
            addView(row)
        })

        // FONT SIZE
        content.addView(settingsSection("🔤  FONT SIZE"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = "Size"
                setTextColor(AppColors.ivory)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
            val cur = settingsRepo.settingsFlow.first().fontSize
            val sizeLabel = TextView(this@ShellActivity).apply {
                text = "${cur}px"
                setTextColor(AppColors.gold)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
            }
            val preview = TextView(this@ShellActivity).apply {
                text = "ॐ अग्निमीळे पुरोहितं यज्ञस्य देवमृत्विजम्।"
                setTextColor(AppColors.ivory)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, cur.toFloat())
                setPadding(0, dp(8), 0, 0)
            }
            val sizeRow = LinearLayout(this@ShellActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            sizeRow.addView(TextView(this@ShellActivity).apply {
                text = "Small (14)"; setTextColor(AppColors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            sizeRow.addView(sizeLabel)
            sizeRow.addView(TextView(this@ShellActivity).apply {
                text = "Large (30)"; setTextColor(AppColors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.END }
            })
            val btnRow = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            btnRow.addView(TextView(this@ShellActivity).apply {
                text = " − "; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setOnClickListener { lifecycleScope.launch { val n = (settingsRepo.settingsFlow.first().fontSize - 1).coerceIn(14, 30); settingsRepo.setFontSize(n); sizeLabel.text = "${n}px"; preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, n.toFloat()) } }
            })
            btnRow.addView(TextView(this@ShellActivity).apply {
                text = " + "; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setOnClickListener { lifecycleScope.launch { val n = (settingsRepo.settingsFlow.first().fontSize + 1).coerceIn(14, 30); settingsRepo.setFontSize(n); sizeLabel.text = "${n}px"; preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, n.toFloat()) } }
            })
            addView(sizeRow)
            addView(btnRow)
            addView(preview)
        })

        // FONT FAMILY (Devanagari)
        content.addView(settingsSection("✏️  FONT FAMILY"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = "সংস্কৃত ফন্ট (মন্ত্র)"
                setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(4))
            })
            addView(TextView(this@ShellActivity).apply {
                text = "উদাত্ত-অনুদাত্ত চিহ্নের জন্য Noto Serif/Sans বা Tiro Sanskrit বেছে নিন"
                setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setPadding(0, 0, 0, dp(10))
            })
            val devaCol = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.VERTICAL }
            FontManager.DEVANAGARI_FONTS.forEach { entry ->
                val isSelected = entry.id == settings.devanagariFont
                // ── FIX: create label and preview separately, add to row, add row to col ──
                val label = TextView(this@ShellActivity).apply {
                    text = (if (isSelected) "● " else "○ ") + entry.displayName
                    setTextColor(if (isSelected) AppColors.goldBright else AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(dp(4), dp(6), dp(8), dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val preview = TextView(this@ShellActivity).apply {
                    text = "अ॒ग्निमी॑ळे"
                    setTextColor(AppColors.saffron)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    if (entry.id != "system") typeface = entry.typeface(this@ShellActivity)
                    setPadding(0, dp(6), dp(4), dp(6))
                }
                val row = LinearLayout(this@ShellActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setOnClickListener {
                        lifecycleScope.launch { settingsRepo.setDevanagariFont(entry.id); showSettingsScreen("reader_prefs") }
                    }
                }
                row.addView(label)
                row.addView(preview)
                devaCol.addView(row)  // add row (not label) to col — fixes double-addView crash
            }
            addView(devaCol)
        })
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = "বাংলা ফন্ট"
                setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(10))
            })
            val banglaCol = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.VERTICAL }
            FontManager.BANGLA_FONTS.forEach { entry ->
                val isSelected = entry.id == settings.banglaFont
                val tv = TextView(this@ShellActivity).apply {
                    text = (if (isSelected) "● " else "○ ") + entry.displayName
                    setTextColor(if (isSelected) AppColors.goldBright else AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    if (entry.id != "system") typeface = entry.typeface(this@ShellActivity)
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    setOnClickListener {
                        lifecycleScope.launch { settingsRepo.setBanglaFont(entry.id); showSettingsScreen("reader_prefs") }
                    }
                }
                banglaCol.addView(tv)
            }
            addView(banglaCol)
        })

        // LAYOUT
        content.addView(settingsSection("📐  LAYOUT"))
        content.addView(card {
            addView(toggleRow("Justify Text", settings.justifyText) { checked ->
                lifecycleScope.launch { settingsRepo.setJustifyText(checked) }
            })
        })

        // PERFORMANCE
        content.addView(settingsSection("⚡  PERFORMANCE"))
        content.addView(card {
            addView(toggleRow("Keep Screen Awake", settings.keepAwake) { checked ->
                lifecycleScope.launch { settingsRepo.setKeepAwake(checked) }
            })
            addView(TextView(this@ShellActivity).apply {
                text = "Prevents screen from dimming while reading"
                setTextColor(AppColors.mutedDim); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(2), 0, 0)
            })
        })
    }

    private suspend fun renderLibraryStorage() {
        settingsSubHeader("Library & Storage")

        // Storage usage
        content.addView(settingsSection("💾  STORAGE USAGE"))
        content.addView(card {
            val tv = TextView(this@ShellActivity).apply {
                text = "মোট ব্যবহৃত স্টোরেজ"
                setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            val val2 = TextView(this@ShellActivity).apply {
                text = "গণনা হচ্ছে…"; setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            val row = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(val2)
            addView(row)
            lifecycleScope.launch {
                val libDir = filesDir.resolve("library_books")
                val packDir = filesDir.resolve("bhashya_packs")
                val bytes = (libDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L }) +
                            (packDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L })
                val kb = bytes / 1024
                val2.text = if (kb > 1024) "${"%.1f".format(kb / 1024.0)} MB" else "$kb KB"
            }
        })

        // Veda Bhashya packs — list scholars with delete option
        content.addView(settingsSection("📜  বেদ ভাষ্য (VEDA BHĀṢYA PACKS)"))
        content.addView(card {
            val loadTV = TextView(this@ShellActivity).apply {
                text = "লোড হচ্ছে…"; setTextColor(AppColors.muted)
            }
            addView(loadTV)
            lifecycleScope.launch {
                val packDir = filesDir.resolve("bhashya_packs")
                val files = packDir.listFiles()?.filter { it.isFile } ?: emptyList()
                loadTV.visibility = View.GONE
                if (files.isEmpty()) {
                    addView(TextView(this@ShellActivity).apply {
                        text = "কোনো ভাষ্য ডাউনলোড করা হয়নি"
                        setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    })
                } else {
                    files.sortedBy { it.name }.forEach { file ->
                        val row = LinearLayout(this@ShellActivity).apply {
                            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, dp(4), 0, dp(4))
                        }
                        row.addView(TextView(this@ShellActivity).apply {
                            text = file.nameWithoutExtension
                            setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        row.addView(TextView(this@ShellActivity).apply {
                            text = "${"%.0f".format(file.length() / 1024.0)} KB"
                            setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setPadding(dp(8), 0, dp(8), 0)
                        })
                        row.addView(TextView(this@ShellActivity).apply {
                            text = "মুছুন"; setTextColor(AppColors.vermilion); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            setOnClickListener { file.delete(); showSettingsScreen("library_storage") }
                        })
                        addView(row)
                    }
                }
            }
        })

        // Digital library books
        content.addView(settingsSection("📗  ডিজিটাল লাইব্রেরির বই"))
        content.addView(card {
            val loadTV2 = TextView(this@ShellActivity).apply {
                text = "লোড হচ্ছে…"; setTextColor(AppColors.muted)
            }
            addView(loadTV2)
            lifecycleScope.launch {
                val libDir = filesDir.resolve("library_books")
                val files = libDir.listFiles()?.filter { it.isFile } ?: emptyList()
                loadTV2.visibility = View.GONE
                if (files.isEmpty()) {
                    addView(TextView(this@ShellActivity).apply {
                        text = "কোনো বই ডাউনলোড করা হয়নি"; setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    })
                } else {
                    files.sortedBy { it.name }.forEach { file ->
                        val row = LinearLayout(this@ShellActivity).apply {
                            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4))
                        }
                        row.addView(TextView(this@ShellActivity).apply {
                            text = file.nameWithoutExtension; setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        row.addView(TextView(this@ShellActivity).apply {
                            text = "মুছুন"; setTextColor(AppColors.vermilion); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            setOnClickListener { file.delete(); showSettingsScreen("library_storage") }
                        })
                        addView(row)
                    }
                }
            }
        })

        // Cache management
        content.addView(settingsSection("🗑  CACHE MANAGEMENT"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = "শুধু ডিজিটাল লাইব্রেরির বই এখানে একসঙ্গে মোছা যায়। ভাষ্য/পর্ব প্যাক ওপরের তালিকা থেকে আলাদাভাবে মুছুন।"
                setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setPadding(0, 0, 0, dp(12))
            })
            addView(TextView(this@ShellActivity).apply {
                text = "🗑  Clear All Downloaded Books"
                setTextColor(AppColors.bg); setBackgroundColor(AppColors.vermilion)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
                setOnClickListener {
                    val dir = filesDir.resolve("library_books")
                    dir.listFiles()?.forEach { it.delete() }
                    showSettingsScreen("library_storage")
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    private suspend fun renderLanguageSettings() {
        val settings = settingsRepo.settingsFlow.first()
        settingsSubHeader("Language Settings")

        content.addView(settingsSection("🌐  APP LANGUAGE"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "Language"; setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(0, 0, 0, dp(8)) })
            val langRow = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("বাংলা", "English").forEach { lang ->
                langRow.addView(TextView(this@ShellActivity).apply {
                    text = if (lang == settings.language) "● $lang" else "○ $lang"
                    setTextColor(if (lang == settings.language) AppColors.goldBright else AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(0, 0, dp(16), 0)
                    setOnClickListener { lifecycleScope.launch { settingsRepo.setLanguage(lang); showSettingsScreen("language") } }
                })
            }
            addView(langRow)
        })

        content.addView(settingsSection("📝  SCRIPT PREFERENCE"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "Script"; setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(0, 0, 0, dp(8)) })
            val scriptRow = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("বাংলা লিপি", "Devanagari", "Roman").forEach { script ->
                scriptRow.addView(TextView(this@ShellActivity).apply {
                    text = if (script == settings.script) "● $script" else "○ $script"
                    setTextColor(if (script == settings.script) AppColors.goldBright else AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setPadding(0, 0, dp(12), 0)
                    setOnClickListener { lifecycleScope.launch { settingsRepo.setScript(script); showSettingsScreen("language") } }
                })
            }
            addView(scriptRow)
        })

        content.addView(settingsSection("🔤  TRANSLITERATION"))
        content.addView(card {
            addView(toggleRow("Sanskrit Transliteration", settings.transliteration) { checked ->
                lifecycleScope.launch { settingsRepo.setTransliteration(checked) }
            })
            addView(TextView(this@ShellActivity).apply {
                text = "Show romanized Sanskrit alongside Devanagari"
                setTextColor(AppColors.mutedDim); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setPadding(0, dp(2), 0, 0)
            })
        })

        content.addView(settingsSection("👁  PREVIEW"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = "ॐ अग्निमीले पुरोहितं यज्ञस्य देवमृत्विजम्।"
                setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setLineSpacing(0f, 1.4f)
            })
        })
    }

    private suspend fun renderNotificationSettings() {
        val settings = settingsRepo.settingsFlow.first()
        settingsSubHeader("Notification Settings")

        content.addView(settingsSection("🔔  NOTIFICATIONS"))
        content.addView(card {
            addView(toggleRow("📱 App Updates", false) { })
            addView(TextView(this@ShellActivity).apply { text = "New version available alerts"; setTextColor(AppColors.mutedDim); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setPadding(dp(24), 0, 0, dp(6)) })
            addView(toggleRow("📚 New Books", false) { })
            addView(TextView(this@ShellActivity).apply { text = "When new books are added to library"; setTextColor(AppColors.mutedDim); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setPadding(dp(24), 0, 0, dp(6)) })
            addView(toggleRow("📖 Daily Reading Reminder", false) { })
            addView(TextView(this@ShellActivity).apply { text = "Reminder to read a mantra each day"; setTextColor(AppColors.mutedDim); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setPadding(dp(24), 0, 0, dp(6)) })
        })

        content.addView(settingsSection("⏰  REMINDER TIME"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "06:00"; setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) })
            addView(TextView(this@ShellActivity).apply {
                text = "🕉 Daily Vedic Study Reminder — Read a mantra, sukta, or Ramayana shloka every day."
                setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setPadding(0, dp(8), 0, 0)
            })
        })

        content.addView(TextView(this).apply {
            text = "💾 Save Preferences"; setTextColor(Color.BLACK); setBackgroundColor(AppColors.gold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
    }

    private suspend fun renderSearchSettings() {
        settingsSubHeader("Search Settings")
        content.addView(settingsSection("🔍  DEFAULT SEARCH SCOPE"))
        content.addView(card {
            val sv = settingsRepo.settingsFlow.first()
            addView(TextView(this@ShellActivity).apply { text = "Default Scope"; setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(0, 0, 0, dp(8)) })
            val scopeRow = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("All Texts", "Vedas Only", "Ramayana Only").forEach { scope ->
                scopeRow.addView(TextView(this@ShellActivity).apply {
                    text = "● $scope"; setTextColor(AppColors.goldBright)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setPadding(0, 0, dp(10), 0)
                })
            }
            addView(scopeRow)
        })
        content.addView(settingsSection("⚙️  SEARCH OPTIONS"))
        content.addView(card {
            addView(toggleRow("🔤 Transliteration Search", false) { })
            addView(TextView(this@ShellActivity).apply { text = "Match Roman transliterations"; setTextColor(AppColors.mutedDim); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setPadding(dp(24), 0, 0, dp(6)) })
            addView(toggleRow("📜 Search Mantras", true) { })
            addView(toggleRow("📖 Search Suktas", true) { })
            addView(toggleRow("🕉 Search Mandalas", true) { })
            addView(toggleRow("🏹 Search Ramayana", true) { })
        })
        content.addView(settingsSection("🕒  RECENT SEARCHES"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "No recent searches"; setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(0, 0, 0, dp(10)) })
            addView(TextView(this@ShellActivity).apply {
                text = "Clear History"; setTextColor(AppColors.bg); setBackgroundColor(AppColors.vermilion)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(8), dp(14), dp(8))
            })
        })
        content.addView(TextView(this).apply {
            text = "💾 Save Settings"; setTextColor(Color.BLACK); setBackgroundColor(AppColors.gold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
    }

    private fun renderStaticPage(heading: String, body: String) {
        settingsSubHeader(heading)
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = body.replace("\\n", "
")
                setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setLineSpacing(0f, 1.5f)
            })
        })
    }

    private fun renderFeedbackForm() {
        settingsSubHeader("Send Feedback")
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "💡 Send Feedback"; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8)) })
            addView(TextView(this@ShellActivity).apply { text = "Share your thoughts to help improve স্বাধ্যায়."; setTextColor(AppColors.muted); setPadding(0, 0, 0, dp(16)) })
            val titleField = EditText(this@ShellActivity).apply { hint = "Your name or topic"; setHintTextColor(AppColors.muted); setTextColor(AppColors.ivory); setBackgroundColor(AppColors.elevated); setPadding(dp(10), dp(10), dp(10), dp(10)) }
            val bodyField = EditText(this@ShellActivity).apply { hint = "Your feedback…"; setHintTextColor(AppColors.muted); setTextColor(AppColors.ivory); setBackgroundColor(AppColors.elevated); setPadding(dp(10), dp(10), dp(10), dp(10)); minLines = 4; gravity = Gravity.TOP }
            addView(TextView(this@ShellActivity).apply { text = "NAME / TOPIC"; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setPadding(0, 0, 0, dp(4)) })
            addView(titleField, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
            addView(TextView(this@ShellActivity).apply { text = "FEEDBACK"; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setPadding(0, 0, 0, dp(4)) })
            addView(bodyField, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
            addView(TextView(this@ShellActivity).apply {
                text = "Send Feedback"; setTextColor(AppColors.bg); setBackgroundColor(AppColors.gold)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { Toast.makeText(this@ShellActivity, "ধন্যবাদ!", Toast.LENGTH_SHORT).show(); settingsScreen = null; lifecycleScope.launch { renderSettings() } }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun renderBugReportForm() {
        settingsSubHeader("Report a Bug")
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "🐛 Report a Bug"; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(4)) })
            addView(TextView(this@ShellActivity).apply { text = "Describe the issue so we can fix it quickly."; setTextColor(AppColors.muted); setPadding(0, 0, 0, dp(16)) })
            val titleField = EditText(this@ShellActivity).apply { hint = "Example: Book not opening"; setHintTextColor(AppColors.muted); setTextColor(AppColors.ivory); setBackgroundColor(AppColors.elevated); setPadding(dp(10), dp(10), dp(10), dp(10)) }
            val descField = EditText(this@ShellActivity).apply { hint = "Steps to reproduce the issue..."; setHintTextColor(AppColors.muted); setTextColor(AppColors.ivory); setBackgroundColor(AppColors.elevated); setPadding(dp(10), dp(10), dp(10), dp(10)); minLines = 4; gravity = Gravity.TOP }
            val deviceField = EditText(this@ShellActivity).apply { hint = "Android version / Device model"; setHintTextColor(AppColors.muted); setTextColor(AppColors.ivory); setBackgroundColor(AppColors.elevated); setPadding(dp(10), dp(10), dp(10), dp(10))
                setText("Android ${android.os.Build.VERSION.RELEASE} / ${android.os.Build.MODEL}") }
            listOf("PROBLEM TITLE" to titleField, "DESCRIPTION" to descField, "DEVICE INFO" to deviceField).forEach { (label, field) ->
                addView(TextView(this@ShellActivity).apply { text = label; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setPadding(0, 0, 0, dp(4)) })
                addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
            }
            addView(TextView(this@ShellActivity).apply {
                text = "Send Report"; setTextColor(AppColors.bg); setBackgroundColor(AppColors.vermilion)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { Toast.makeText(this@ShellActivity, "রিপোর্ট পাঠানো হয়েছে। ধন্যবাদ!", Toast.LENGTH_SHORT).show(); settingsScreen = null; lifecycleScope.launch { renderSettings() } }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun renderAbout() {
        settingsSubHeader("About স্বাধ্যায়")
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "🕉"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f); gravity = Gravity.CENTER })
            addView(TextView(this@ShellActivity).apply { text = "স্বাধ্যায়"; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(2)) })
            addView(TextView(this@ShellActivity).apply { text = "Version 1.0.0 (Build 1)"; setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); gravity = Gravity.CENTER })
        })
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "About"; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8)) })
            addView(TextView(this@ShellActivity).apply {
                text = "স্বাধ্যায় is a digital platform dedicated to preserving and presenting the timeless knowledge of Vedic literature, Sanskrit scriptures, and the Valmiki Ramayana.

The application combines ancient wisdom with modern technology to create a simple, accessible, and immersive reading experience for students, researchers, and knowledge seekers."
                setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setLineSpacing(0f, 1.5f); setPadding(0, 0, 0, dp(12))
            })
            addView(TextView(this@ShellActivity).apply {
                text = ""Knowledge preserved through time becomes wisdom for future generations.""
                setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setBackgroundColor(AppColors.elevated); setPadding(dp(12), dp(10), dp(12), dp(10))
            })
        })
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "Core Features"; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8)) })
            listOf(
                "🖥 Complete four Vedas with Bhāṣya packs",
                "🏹 Valmiki Ramayana — all 6 Kandas, 534 Sargas, 17,902 Shlokas",
                "🔤 Sanskrit · Word-by-word · English translation",
                "🔍 Unified search across Vedas and Ramayana",
                "📚 Digital Library with downloadable books",
                "🌙 Dark/Light/System theme support",
                "🔡 Adjustable font size and family"
            ).forEach { feature ->
                addView(TextView(this@ShellActivity).apply {
                    text = "• $feature"; setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, dp(3), 0, dp(3)); setLineSpacing(0f, 1.3f)
                })
            }
        })
        content.addView(card {
            addView(TextView(this@ShellActivity).apply { text = "👨‍💻 Developer"; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8)) })
            addView(TextView(this@ShellActivity).apply { text = "Ashim Datta"; setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD })
            addView(TextView(this@ShellActivity).apply { text = "Founder & Developer of স্বাধ্যায়"; setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) })
            addView(TextView(this@ShellActivity).apply {
                text = "Website: arsa-siddanto.blogspot.com"; setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(0, dp(4), 0, 0)
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://arsa-siddanto.blogspot.com"))) }
            })
        })
        content.addView(TextView(this).apply {
            text = "স্বাধ্যায় v1.0.0 · Build 1
© Copyright & Preservation
All rights reserved. This digital work is protected
and preserved for educational, spiritual and
research purposes.

Kyronix Innovation Group (KIG)
Ashim Datta
Founder & CEO"
            setTextColor(AppColors.mutedDim); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(8))
        })
    }


    /** Simple two-state row (label + ⚪/⚫ indicator) — no Switch widget dependency, matches this screen's plain-text-row style. */
    private fun toggleRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        var state = initial
        lateinit var indicator: TextView
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        indicator = TextView(this).apply {
            text = if (state) "চালু ●" else "○ বন্ধ"
            setTextColor(if (state) AppColors.goldBright else AppColors.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        row.addView(indicator)
        row.setOnClickListener {
            state = !state
            indicator.text = if (state) "চালু ●" else "○ বন্ধ"
            indicator.setTextColor(if (state) AppColors.goldBright else AppColors.muted)
            onChange(state)
        }
        return row
    }
    override fun onBackPressed() {
        if (current == Tab.SETTINGS && settingsScreen != null) {
            settingsScreen = null
            content.removeAllViews()
            lifecycleScope.launch { renderSettings() }
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

}
