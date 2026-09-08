package com.kyronix.swadhyaa.presentation.shell

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
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
            Tab.SETTINGS -> renderSettings()
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

    private fun renderSettings() {
        content.addView(title("সেটিংস"))

        lifecycleScope.launch {
            val settings = settingsRepo.settingsFlow.first()

            // ── Accent — real visual effect, verified against app.css's
            // four [data-accent] blocks (AppColors.applyAccent) ─────────
            content.addView(card {
                addView(TextView(this@ShellActivity).apply {
                    text = "রঙের থিম (Accent)"
                    setTextColor(AppColors.ivory)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, dp(10))
                })
                val accentRow = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.HORIZONTAL }
                val accents = listOf("gold" to "সোনালি", "emerald" to "পান্না", "indigo" to "নীল", "violet" to "বেগুনি")
                accents.forEach { (key, label) ->
                    accentRow.addView(TextView(this@ShellActivity).apply {
                        text = if (key == settings.accentTheme) "● $label" else "○ $label"
                        setTextColor(if (key == settings.accentTheme) AppColors.goldBright else AppColors.muted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setPadding(dp(4), dp(6), dp(12), dp(6))
                        setOnClickListener {
                            lifecycleScope.launch {
                                settingsRepo.setAccentTheme(key)
                                AppColors.applyAccent(key)
                                recreate() // re-render every screen with the new accent, matching legacy's immediate re-theme
                            }
                        }
                    })
                }
                addView(accentRow)
            })

            // ── Theme (auto/light/dark) — stored for settings/migration
            // parity, but see AppColors.kt's doc comment: legacy itself
            // has no [data-theme="light"] CSS or prefers-color-scheme
            // media query anywhere, so selecting "light" today changes
            // nothing visually in the real app either. Being upfront
            // about this rather than silently implying it does something. ──
            content.addView(card {
                addView(TextView(this@ShellActivity).apply {
                    text = "থিম মোড"
                    setTextColor(AppColors.ivory)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(this@ShellActivity).apply {
                    text = "বর্তমানে অ্যাপটি সবসময় গাঢ় (dark) থিমে দেখায় — মূল সংস্করণেও 'light' মোডের কোনো ভিজ্যুয়াল প্রভাব নেই।"
                    setTextColor(AppColors.mutedDim)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(0, dp(2), 0, dp(10))
                })
                val themeRow = LinearLayout(this@ShellActivity).apply { orientation = LinearLayout.HORIZONTAL }
                listOf("auto" to "স্বয়ংক্রিয়", "light" to "হালকা", "dark" to "গাঢ়").forEach { (key, label) ->
                    themeRow.addView(TextView(this@ShellActivity).apply {
                        text = if (key == settings.theme) "● $label" else "○ $label"
                        setTextColor(if (key == settings.theme) AppColors.goldBright else AppColors.muted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setPadding(dp(4), dp(6), dp(12), dp(6))
                        setOnClickListener {
                            lifecycleScope.launch { settingsRepo.setTheme(key) }
                            // No recreate() needed — see note above, this
                            // setting currently has no visual effect to refresh.
                        }
                    })
                }
                addView(themeRow)
            })

            // ── Font size ──────────────────────────────────────────────
            content.addView(card {
                addView(TextView(this@ShellActivity).apply {
                    text = "লেখার আকার"
                    setTextColor(AppColors.ivory)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, dp(8))
                })
                val row = LinearLayout(this@ShellActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val valueLabel = TextView(this@ShellActivity).apply {
                    text = "${settings.fontSize}"
                    setTextColor(AppColors.ivory)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(dp(20), 0, dp(20), 0)
                }
                row.addView(TextView(this@ShellActivity).apply {
                    text = "−"
                    setTextColor(AppColors.gold)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                    setOnClickListener {
                        lifecycleScope.launch {
                            val cur = settingsRepo.settingsFlow.first().fontSize
                            val next = (cur - 1).coerceIn(12, 32)
                            settingsRepo.setFontSize(next)
                            valueLabel.text = "$next"
                        }
                    }
                })
                row.addView(valueLabel)
                row.addView(TextView(this@ShellActivity).apply {
                    text = "+"
                    setTextColor(AppColors.gold)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                    setOnClickListener {
                        lifecycleScope.launch {
                            val cur = settingsRepo.settingsFlow.first().fontSize
                            val next = (cur + 1).coerceIn(12, 32)
                            settingsRepo.setFontSize(next)
                            valueLabel.text = "$next"
                        }
                    }
                })
                addView(row)
            })

            // ── Toggles ────────────────────────────────────────────────
            content.addView(card {
                addView(toggleRow("লেখা জাস্টিফাই করুন", settings.justifyText) { checked ->
                    lifecycleScope.launch { settingsRepo.setJustifyText(checked) }
                })
                addView(toggleRow("পড়ার সময় স্ক্রিন জ্বলে থাকুক", settings.keepAwake) { checked ->
                    lifecycleScope.launch { settingsRepo.setKeepAwake(checked) }
                })
            })

            content.addView(card {
                addView(TextView(this@ShellActivity).apply {
                    text = "Offline-first"
                    setTextColor(AppColors.ivory)
                })
                addView(TextView(this@ShellActivity).apply {
                    text = "Core DB APK-এ bundled। Scholar packs আলাদা DB repo Release থেকে।"
                    setTextColor(AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                })
            })
            content.addView(card {
                addView(TextView(this@ShellActivity).apply {
                    text = "Design"
                    setTextColor(AppColors.ivory)
                })
                addView(TextView(this@ShellActivity).apply {
                    text = "Illuminated manuscript · saffron accent · content-first"
                    setTextColor(AppColors.muted)
                })
            })
        }
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
}
