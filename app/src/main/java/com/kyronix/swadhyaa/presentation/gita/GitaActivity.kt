package com.kyronix.swadhyaa.presentation.gita

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.data.prefs.UserPrefs
import com.kyronix.swadhyaa.data.repository.GitaScholarInfo
import com.kyronix.swadhyaa.presentation.reader.ReaderActivity
import com.kyronix.swadhyaa.ui.gesture.attachSwipeNavigation
import com.kyronix.swadhyaa.ui.theme.AppColors
import com.kyronix.swadhyaa.ui.theme.FontManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * গীতা reader — same shape as [com.kyronix.swadhyaa.presentation.reader.ReaderActivity]'s
 * verse + bhashya panel, but with two differences forced by Gita's data:
 *   1. A download gate up front (mirrors
 *      [com.kyronix.swadhyaa.presentation.mahabharata.MahabharataActivity]) —
 *      the base verse text itself is a downloadable pack here, not bundled.
 *   2. Two jump boxes (অধ্যায়/শ্লোক) instead of Veda's three (MANDAL/SUKTA/MANTRA).
 */
class GitaActivity : AppCompatActivity() {

    private lateinit var vm: GitaViewModel
    private lateinit var prefs: UserPrefs
    private lateinit var devanagariTypeface: Typeface
    private lateinit var banglaTypeface: Typeface

    private lateinit var root: ScrollView
    private lateinit var col: LinearLayout
    private lateinit var gateCard: LinearLayout
    private lateinit var readerCol: LinearLayout
    private lateinit var jumpRow: LinearLayout
    private lateinit var sanskritText: TextView
    private lateinit var translitText: TextView
    private lateinit var statusText: TextView
    private lateinit var langTabRow: LinearLayout
    // NOTE (fix, 2026-09-11): there used to be a separate `bhashyaPanel`
    // container placed AFTER this whole list, so tapping a scholar showed
    // their content below every row instead of under the one you tapped.
    // Fixed the same way as ReaderActivity: [renderScholarList] now inserts
    // the selected scholar's content right after their own row, inside this
    // same container (accordion-style) — there is no separate panel anymore.
    private lateinit var scholarList: LinearLayout

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    private val BG      get() = AppColors.bg
    private val SURFACE get() = AppColors.surface
    private val IVORY   get() = AppColors.ivory
    private val GOLD    get() = AppColors.gold
    private val SAFFRON get() = AppColors.saffron
    private val MUTED   get() = AppColors.muted
    private val VERMILION get() = AppColors.vermilion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = UserPrefs(this)

        val settings = runBlocking { SettingsRepository(this@GitaActivity).settingsFlow.first() }
        devanagariTypeface = FontManager.devanagariTypeface(this, settings.devanagariFont)
        banglaTypeface = FontManager.banglaTypeface(this, settings.banglaFont)

        vm = ViewModelProvider(this, GitaViewModel.Factory(applicationContext))[GitaViewModel::class.java]

        buildUi()
        root.attachSwipeNavigation(onSwipeLeft = { vm.next() }, onSwipeRight = { vm.prev() })
        setContentView(root)
        observe()
    }

    // ── UI construction ───────────────────────────────────────────────────

    private fun buildUi() {
        root = ScrollView(this).apply { setBackgroundColor(BG); setFillViewport(true) }
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "←"; setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dp(4), dp(4), dp(16), dp(4))
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            text = "শ্রীমদ্ভগবদ্গীতা"; setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(header)

        // Download-gate card — visible only while the base text pack isn't ready.
        gateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }
        col.addView(gateCard)

        // Everything below is only populated once the gate clears.
        readerCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(readerCol)

        jumpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(12))
        }
        readerCol.addView(jumpRow)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }
        sanskritText = TextView(this).apply {
            setTextColor(SAFFRON)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.45f)
            typeface = devanagariTypeface
        }
        translitText = TextView(this).apply {
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(10), 0, 0)
        }
        card.addView(sanskritText)
        card.addView(translitText)
        readerCol.addView(card)

        statusText = TextView(this).apply {
            setTextColor(GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(10), 0, dp(4))
        }
        readerCol.addView(statusText)

        readerCol.addView(sectionDivider())

        val langScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(8), 0, dp(8))
        }
        langTabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        langScroll.addView(langTabRow)
        readerCol.addView(langScroll)

        scholarList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        readerCol.addView(scholarList)

        readerCol.addView(sectionDivider())

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        nav.addView(android.widget.Button(this).apply {
            text = "← আগের শ্লোক"
            setOnClickListener { vm.prev() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        nav.addView(android.widget.Button(this).apply {
            text = "পরের শ্লোক →"
            setOnClickListener { vm.next() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        readerCol.addView(nav)

        col.addView(sectionDivider()) // trailing spacer to match ReaderActivity's bottom padding rhythm
        root.addView(col)
    }

    /**
     * `speaker` came as a bonus once the pack's real schema was confirmed
     * (see GitaCoreTextRepository's doc comment) — the source data's values
     * are unconfirmed/untranslated, so this only maps the names virtually
     * certain to appear in a Gita dataset and otherwise shows the raw value
     * rather than hiding it.
     */
    private fun speakerInBengali(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().lowercase()) {
            "krishna", "sri krishna", "shri krishna", "the blessed lord" -> "শ্রীকৃষ্ণ"
            "arjuna" -> "অর্জুন"
            "sanjaya" -> "সঞ্জয়"
            "dhritarashtra", "dhritrashtra" -> "ধৃতরাষ্ট্র"
            else -> raw
        }
    }

    private fun sectionDivider() = TextView(this).apply {
        setBackgroundColor(SURFACE)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { topMargin = dp(12); bottomMargin = dp(12) }
    }

    // ── Observation ───────────────────────────────────────────────────────

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    renderGate(s)
                    readerCol.visibility = if (s.downloaded) android.view.View.VISIBLE else android.view.View.GONE
                    if (!s.downloaded) return@collect

                    if (s.loading) { statusText.text = "লোড হচ্ছে…"; return@collect }
                    if (s.error != null) { statusText.text = "Error: ${s.error}"; return@collect }
                    val m = s.current ?: return@collect

                    sanskritText.text = m.devanagari.ifBlank { "(শ্লোক পাওয়া যায়নি)" }
                    translitText.text = m.transliteration.orEmpty()
                    translitText.visibility =
                        if (m.transliteration.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
                    val speakerBn = speakerInBengali(m.speaker)
                    statusText.text = "গীতা ${m.adhyaya}/${m.shloka}" +
                        (speakerBn?.let { " · বক্তা: $it" } ?: "")

                    renderJump(s)
                    renderLangTabs(s)
                    renderScholarList(s) // also renders the selected scholar's content inline now

                    prefs.saveContinue(
                        UserPrefs.ContinuePos(
                            kind = "gita",
                            corpusId = m.adhyaya,
                            itemId = m.shloka,
                            label = "গীতা ${m.adhyaya}/${m.shloka}"
                        )
                    )
                }
            }
        }
    }

    // ── Download gate ─────────────────────────────────────────────────────

    private fun renderGate(s: GitaUiState) {
        gateCard.removeAllViews()
        gateCard.visibility = if (s.downloaded) android.view.View.GONE else android.view.View.VISIBLE
        if (s.downloaded) return

        gateCard.addView(TextView(this).apply {
            text = "গীতার মূল শ্লোক ডাউনলোড করা হয়নি"
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, dp(12))
        })
        if (s.downloadError != null) {
            gateCard.addView(TextView(this).apply {
                text = s.downloadError; setTextColor(VERMILION)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, 0, 0, dp(12))
            })
        }
        if (s.downloadProgress != null) {
            gateCard.addView(TextView(this).apply {
                text = "ডাউনলোড হচ্ছে… ${s.downloadProgress}"
                setTextColor(GOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        } else {
            gateCard.addView(TextView(this).apply {
                text = "ডাউনলোড করুন"
                setTextColor(Color.BLACK)
                setBackgroundColor(GOLD)
                setPadding(dp(20), dp(10), dp(20), dp(10))
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setOnClickListener { vm.downloadCoreText() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    // ── Jump row (অধ্যায় / শ্লোক) ───────────────────────────────────────

    private fun renderJump(s: GitaUiState) {
        jumpRow.removeAllViews()
        val m = s.current ?: return
        addJump("অধ্যায়", "${m.adhyaya}", s.adhyayaOptions) { vm.jumpAdhyaya(it) }

        // Every chapter's pack has one extra "verse" beyond the real last
        // shloka — the closing colophon ("ॐ तत्सदिति... अध्यायः"), stored as
        // its own row with verse = lastRealVerse + 1. Showing it as a plain
        // number confused verse counts, so it's always the highest number in
        // shlokaOptions (see GitaCoreTextRepository — verses come back
        // ascending) and gets relabeled "সমাপন" here, both in the picker
        // list and in the box itself when it's the current selection. The
        // underlying Int is untouched — jumpShloka still receives the real
        // verse number either way, only the label shown to the reader changes.
        val lastShloka = s.shlokaOptions.maxOrNull()
        val shlokaValue = if (lastShloka != null && m.shloka == lastShloka) "সমাপন" else "${m.shloka}"
        addJump("শ্লোক", shlokaValue, s.shlokaOptions,
            labelFor = { v -> if (v == lastShloka) "সমাপন" else v.toString() }
        ) { vm.jumpShloka(it) }
    }

    private fun addJump(
        label: String,
        value: String,
        options: List<Int>,
        labelFor: (Int) -> String = { it.toString() },
        onPick: (Int) -> Unit
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                if (options.isEmpty()) return@setOnClickListener
                AlertDialog.Builder(this@GitaActivity)
                    .setTitle(label)
                    .setItems(options.map { labelFor(it) }.toTypedArray()) { _, i -> onPick(options[i]) }
                    .show()
            }
        }
        box.addView(TextView(this).apply {
            text = label; setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = value; setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        })
        jumpRow.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginEnd = dp(6) })
    }

    // ── Language tabs — reuses ReaderActivity's Bengali/English/Hindi/… labels ──

    private fun renderLangTabs(s: GitaUiState) {
        langTabRow.removeAllViews()
        if (s.availableLanguages.isEmpty()) {
            langTabRow.addView(TextView(this).apply {
                text = "এই শ্লোকের কোনো ভাষ্য নেই"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            return
        }
        s.availableLanguages.forEach { lang ->
            val selected = lang == s.selectedLanguage
            langTabRow.addView(TextView(this).apply {
                text = ReaderActivity.langDisplayName(lang)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setBackgroundColor(if (selected) SAFFRON else SURFACE)
                setTextColor(if (selected) Color.BLACK else IVORY)
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setOnClickListener { vm.selectLanguage(lang) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
    }

    // ── Scholar list (accordion: content expands inline under its own row) ─

    private fun renderScholarList(s: GitaUiState) {
        scholarList.removeAllViews()
        val scholars = s.scholarsByLang[s.selectedLanguage] ?: return
        scholars.forEach { scholar ->
            val isDownloaded = s.scholarDownloadStatus[scholar.id] == true
            val isSelected = scholar.id == s.selectedScholar?.id
            val suffix = if (isDownloaded) "" else " ↓"

            scholarList.addView(TextView(this).apply {
                text = "${scholar.name}$suffix"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(if (isSelected) GOLD else SURFACE)
                setTextColor(if (isSelected) Color.BLACK else IVORY)
                setOnClickListener { vm.selectScholar(scholar) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) })

            // The fix: insert this scholar's content right here, between
            // their row and the next scholar's row — not after the loop.
            if (isSelected) {
                scholarList.addView(buildBhashyaContent(s, scholar))
            }
        }
    }

    // ── Bhashya content (built per-scholar, inserted inline above) ─────────

    private fun buildBhashyaContent(s: GitaUiState, scholar: GitaScholarInfo): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(12))
            setBackgroundColor(SURFACE)
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val isDownloaded = s.scholarDownloadStatus[scholar.id] == true

        if (!isDownloaded) {
            container.addView(TextView(this).apply {
                text = "এই ভাষ্য ডাউনলোড করা হয়নি"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, 0, 0, dp(12))
            })
            val progress = s.bhashyaDownloadProgress
            if (progress != null) {
                container.addView(TextView(this).apply {
                    text = "ডাউনলোড হচ্ছে… $progress"
                    setTextColor(GOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                })
            } else {
                container.addView(TextView(this).apply {
                    text = "ডাউনলোড করুন"
                    setTextColor(Color.BLACK)
                    setBackgroundColor(GOLD)
                    setPadding(dp(20), dp(10), dp(20), dp(10))
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener { vm.downloadScholar(scholar) }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            return container
        }

        if (s.bhashyaLoading) {
            container.addView(TextView(this).apply {
                text = "ভাষ্য লোড হচ্ছে…"; setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, 0)
            })
            return container
        }

        if (s.bhashyaError != null) {
            container.addView(TextView(this).apply {
                text = s.bhashyaError; setTextColor(VERMILION)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, 0)
            })
            return container
        }

        // Header: work title (now IS scholar.name — see GitaManifest) + delete button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameCol.addView(TextView(this).apply {
            text = scholar.name
            setTextColor(GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        })
        if (scholar.workTitle != null) {
            nameCol.addView(TextView(this).apply {
                text = scholar.workTitle
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(2), 0, 0)
            })
        }
        headerRow.addView(nameCol)
        headerRow.addView(TextView(this).apply {
            text = "মুছুন"
            setTextColor(VERMILION)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                AlertDialog.Builder(this@GitaActivity)
                    .setTitle("ভাষ্য মুছবেন?")
                    .setMessage("\"${scholar.name}\" এর ডাউনলোড করা ভাষ্য মুছে যাবে।")
                    .setPositiveButton("মুছুন") { _, _ -> vm.deleteScholar(scholar) }
                    .setNegativeButton("বাতিল", null)
                    .show()
            }
        })
        container.addView(headerRow)

        if (s.bhashyaContent.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "এই শ্লোকের জন্য এই ভাষ্যে কিছু পাওয়া যায়নি"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, 0)
            })
            return container
        }

        s.bhashyaContent.forEach { field ->
            container.addView(TextView(this).apply {
                text = field.label
                setTextColor(SAFFRON)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(12), 0, dp(4))
            })
            container.addView(TextView(this).apply {
                text = field.value
                setTextColor(IVORY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setLineSpacing(0f, 1.5f)
                typeface = banglaTypeface
            })
        }
        return container
    }
}
