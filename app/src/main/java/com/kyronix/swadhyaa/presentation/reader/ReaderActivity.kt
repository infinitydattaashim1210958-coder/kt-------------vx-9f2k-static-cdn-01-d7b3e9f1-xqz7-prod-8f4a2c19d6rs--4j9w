package com.kyronix.swadhyaa.presentation.reader

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
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
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.entity.ScholarEntity
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.data.prefs.UserPrefs
import com.kyronix.swadhyaa.data.repository.BhashyaRepository
import com.kyronix.swadhyaa.data.repository.VedaRepository
import com.kyronix.swadhyaa.ui.gesture.attachSwipeNavigation
import com.kyronix.swadhyaa.ui.theme.AppColors
import com.kyronix.swadhyaa.ui.theme.FontManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VEDA_ID = "veda_id"
        private val BG      get() = AppColors.bg
        private val SURFACE get() = AppColors.surface
        private val IVORY   get() = AppColors.ivory
        private val GOLD    get() = AppColors.gold
        private val SAFFRON get() = AppColors.saffron
        private val MUTED   get() = AppColors.muted
        private val VERMILION get() = AppColors.vermilion

        fun langDisplayName(lang: String) = when (lang.lowercase()) {
            "bengali"   -> "Bengali"
            "english"   -> "English"
            "gujarati"  -> "Gujarati"
            "hindi"     -> "Hindi"
            "marathi"   -> "Marathi"
            "sanskrit"  -> "Sanskrit"
            "kannada"   -> "Kannada"
            "tamil"     -> "Tamil"
            "telugu"    -> "Telugu"
            "odia"      -> "Odia"
            "punjabi"   -> "Punjabi"
            else        -> lang.replaceFirstChar { it.uppercase() }
        }
    }

    private val vedaId by lazy { intent.getIntExtra(EXTRA_VEDA_ID, 1) }

    private lateinit var vm: ReaderViewModel
    private lateinit var prefs: UserPrefs
    private lateinit var devanagariTypeface: Typeface
    private lateinit var banglaTypeface: Typeface

    // View references
    private lateinit var titleBar: TextView
    private lateinit var vedaChips: LinearLayout
    private lateinit var jumpRow: LinearLayout
    private lateinit var sanskritText: TextView
    private lateinit var metaText: TextView
    private lateinit var statusText: TextView
    private lateinit var langTabRow: LinearLayout
    private lateinit var scholarList: LinearLayout
    private lateinit var bhashyaPanel: LinearLayout
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = UserPrefs(this)

        // Resolve fonts from user settings before UI is built
        val settings = runBlocking { SettingsRepository(this@ReaderActivity).settingsFlow.first() }
        devanagariTypeface = FontManager.devanagariTypeface(this, settings.devanagariFont)
        banglaTypeface = FontManager.banglaTypeface(this, settings.banglaFont)

        // Build ViewModel
        val db = CoreDatabase.getInstance(applicationContext)
        val vedaRepo = VedaRepository(db)
        val bhashyaRepo = BhashyaRepository(db)
        vm = ViewModelProvider(
            this,
            ReaderViewModel.Factory(vedaRepo, bhashyaRepo, applicationContext, vedaId)
        )[ReaderViewModel::class.java]

        val root = buildUi()
        root.attachSwipeNavigation(onSwipeLeft = { vm.next() }, onSwipeRight = { vm.prev() })
        setContentView(root)
        observe()
    }

    // ── UI construction ───────────────────────────────────────────────────

    private fun buildUi(): ScrollView {
        val root = ScrollView(this).apply {
            setBackgroundColor(BG)
            setFillViewport(true)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "←"
            setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dp(4), dp(4), dp(16), dp(4))
            setOnClickListener { finish() }
        })
        titleBar = TextView(this).apply {
            setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            text = "…"
        }
        header.addView(titleBar)
        col.addView(header)

        // Veda chips
        val vedaScroll = HorizontalScrollView(this).apply {
            setPadding(0, dp(12), 0, dp(8))
            isHorizontalScrollBarEnabled = false
        }
        vedaChips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        vedaScroll.addView(vedaChips)
        col.addView(vedaScroll)

        // Jump chips
        jumpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
        }
        col.addView(jumpRow)

        // Sanskrit mantra card
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
            text = "…"
        }
        metaText = TextView(this).apply {
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        card.addView(sanskritText)
        card.addView(metaText)
        col.addView(card)

        statusText = TextView(this).apply {
            setTextColor(GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(10), 0, dp(4))
        }
        col.addView(statusText)

        // ── Bhashya section ───────────────────────────────────────────
        col.addView(sectionDivider())

        // Language tabs (horizontal scroll)
        val langScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(8), 0, dp(8))
        }
        langTabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        langScroll.addView(langTabRow)
        col.addView(langScroll)

        // Scholar list
        scholarList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        col.addView(scholarList)

        // Bhashya content panel
        bhashyaPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        col.addView(bhashyaPanel)

        col.addView(sectionDivider())

        // Prev / Next
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        btnPrev = Button(this).apply {
            text = "← আগের মন্ত্র"
            setOnClickListener { vm.prev() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnNext = Button(this).apply {
            text = "পরের মন্ত্র →"
            setOnClickListener { vm.next() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nav.addView(btnPrev)
        nav.addView(btnNext)
        col.addView(nav)

        root.addView(col)
        return root
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
                    if (s.loading) { statusText.text = "লোড হচ্ছে…"; return@collect }
                    if (s.error != null) { statusText.text = "Error: ${s.error}"; return@collect }
                    val m = s.current ?: return@collect

                    titleBar.text = m.refLabel
                    sanskritText.text = m.sanskrit.ifBlank { "(text unavailable)" }
                    metaText.text = listOfNotNull(
                        m.devata?.takeIf { it.isNotBlank() }?.let { "দেবতা: $it" },
                        m.rishi?.takeIf { it.isNotBlank() }?.let { "ঋষি: $it" },
                        m.chhanda?.takeIf { it.isNotBlank() }?.let { "ছন্দ: $it" }
                    ).joinToString("  ·  ")
                    statusText.text = "${m.vedaName} · id ${m.id}"

                    renderVedaChips(s)
                    renderJump(s)
                    renderLangTabs(s)
                    renderScholarList(s)
                    renderBhashyaPanel(s)

                    // Save continue position
                    prefs.saveContinue(
                        UserPrefs.ContinuePos(
                            kind = "veda",
                            corpusId = m.vedaId,
                            itemId = m.id,
                            label = m.refLabel
                        )
                    )
                }
            }
        }
    }

    // ── Veda chips ────────────────────────────────────────────────────────

    private fun renderVedaChips(s: ReaderUiState) {
        vedaChips.removeAllViews()
        val currentId = s.current?.vedaId
        s.vedas.forEach { v ->
            val selected = v.id == currentId
            vedaChips.addView(TextView(this).apply {
                text = v.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setBackgroundColor(if (selected) SAFFRON else SURFACE)
                setTextColor(if (selected) Color.BLACK else IVORY)
                setOnClickListener { vm.openVeda(v.id) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
    }

    // ── Jump row ──────────────────────────────────────────────────────────

    private fun renderJump(s: ReaderUiState) {
        jumpRow.removeAllViews()
        val m = s.current ?: return
        addJump("MANDAL", "${m.level1 ?: "—"}", s.level1Options) { vm.jumpLevel1(it) }
        addJump("SUKTA",  "${m.level2 ?: "—"}", s.level2Options) { vm.jumpLevel2(it) }
        addJump("MANTRA", "${m.mantraNo ?: "—"}", s.mantraNoOptions) { vm.jumpMantraNo(it) }
    }

    private fun addJump(label: String, value: String, options: List<Int>, onPick: (Int) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                if (options.isEmpty()) return@setOnClickListener
                AlertDialog.Builder(this@ReaderActivity)
                    .setTitle(label)
                    .setItems(options.map { it.toString() }.toTypedArray()) { _, i -> onPick(options[i]) }
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

    // ── Language tabs ─────────────────────────────────────────────────────

    private fun renderLangTabs(s: ReaderUiState) {
        langTabRow.removeAllViews()
        if (s.availableLanguages.isEmpty()) {
            langTabRow.addView(TextView(this).apply {
                text = "এই মন্ত্রের কোনো ভাষ্য নেই"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            return
        }
        s.availableLanguages.forEach { lang ->
            val selected = lang == s.selectedLanguage
            langTabRow.addView(TextView(this).apply {
                text = langDisplayName(lang)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setBackgroundColor(if (selected) SAFFRON else SURFACE)
                setTextColor(if (selected) Color.BLACK else IVORY)
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setOnClickListener { vm.selectLanguage(lang) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
    }

    // ── Scholar list ──────────────────────────────────────────────────────

    private fun renderScholarList(s: ReaderUiState) {
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
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) })
        }
    }

    // ── Bhashya panel ─────────────────────────────────────────────────────

    private fun renderBhashyaPanel(s: ReaderUiState) {
        bhashyaPanel.removeAllViews()
        val scholar = s.selectedScholar ?: return
        val isDownloaded = s.scholarDownloadStatus[scholar.id] == true

        if (!isDownloaded) {
            // Show download card
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(SURFACE)
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            val sizeKb = scholar.packSizeBytes?.let { " (${it / 1024} KB" } ?: ""
            val entries = scholar.entryCount?.let { ", $it এন্ট্রি)" } ?: if (sizeKb.isNotEmpty()) ")" else ""
            card.addView(TextView(this).apply {
                text = "এই ভাষ্য ডাউনলোড করা হয়নি$sizeKb$entries"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, 0, 0, dp(12))
            })

            val progress = s.downloadProgress
            if (progress != null) {
                card.addView(TextView(this).apply {
                    text = "ডাউনলোড হচ্ছে… $progress"
                    setTextColor(GOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                })
            } else {
                val btn = TextView(this).apply {
                    text = "ডাউনলোড করুন"
                    setTextColor(Color.BLACK)
                    setBackgroundColor(GOLD)
                    setPadding(dp(20), dp(10), dp(20), dp(10))
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener { vm.downloadScholar(scholar) }
                }
                card.addView(btn, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            bhashyaPanel.addView(card)
            return
        }

        if (s.bhashyaLoading) {
            bhashyaPanel.addView(TextView(this).apply {
                text = "ভাষ্য লোড হচ্ছে…"; setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, 0)
            })
            return
        }

        if (s.bhashyaError != null) {
            bhashyaPanel.addView(TextView(this).apply {
                text = s.bhashyaError; setTextColor(VERMILION)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, 0)
            })
            return
        }

        if (s.bhashyaContent.isEmpty()) return

        // Scholar name header + delete button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(12))
        }
        headerRow.addView(TextView(this).apply {
            text = scholar.name
            setTextColor(GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(this).apply {
            text = "এই ভাষ্য মুছুন"
            setTextColor(VERMILION)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                AlertDialog.Builder(this@ReaderActivity)
                    .setTitle("ভাষ্য মুছবেন?")
                    .setMessage("\"${scholar.name}\" এর ডাউনলোড করা ভাষ্য মুছে যাবে।")
                    .setPositiveButton("মুছুন") { _, _ -> vm.deleteScholar(scholar) }
                    .setNegativeButton("বাতিল", null)
                    .show()
            }
        })
        bhashyaPanel.addView(headerRow)

        // Field-by-field content
        s.bhashyaContent.forEach { field ->
            bhashyaPanel.addView(TextView(this).apply {
                text = field.label
                setTextColor(SAFFRON)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(12), 0, dp(4))
            })
            bhashyaPanel.addView(TextView(this).apply {
                text = field.value
                setTextColor(IVORY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setLineSpacing(0f, 1.5f)
                typeface = banglaTypeface
            })
        }
    }
}
