package com.kyronix.swadhyaa.presentation.reader

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
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.repository.VedaRepository
import kotlinx.coroutines.launch

/**
 * Veda Reader — matches original UX:
 * 4 Veda chips · Mandal/Sukta/Mantra jump · Sanskrit · Prev/Next
 */
class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VEDA_ID = "veda_id"
        private val BG = Color.parseColor("#0F0D0A")
        private val SURFACE = Color.parseColor("#17130F")
        private val IVORY = Color.parseColor("#F5E6C8")
        private val GOLD = Color.parseColor("#C4A574")
        private val SAFFRON = Color.parseColor("#E8A317")
        private val MUTED = Color.parseColor("#A89070")
    }

    private val vedaId by lazy { intent.getIntExtra(EXTRA_VEDA_ID, 1) }

    private val vm: ReaderViewModel by viewModels {
        val db = CoreDatabase.getInstance(applicationContext)
        ReaderViewModel.Factory(VedaRepository(db), vedaId)
    }

    private lateinit var titleBar: TextView
    private lateinit var vedaChips: LinearLayout
    private lateinit var jumpRow: LinearLayout
    private lateinit var sanskritText: TextView
    private lateinit var metaText: TextView
    private lateinit var statusText: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        observe()
    }

    private fun buildUi(): ScrollView {
        val root = ScrollView(this).apply {
            setBackgroundColor(BG)
            setFillViewport(true)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // Back + title
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = TextView(this).apply {
            text = "←"
            setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dp(4), dp(4), dp(16), dp(4))
            setOnClickListener { finish() }
        }
        titleBar = TextView(this).apply {
            setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            text = "…"
        }
        header.addView(back)
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

        // Jump chips: Mandal / Sukta / Mantra
        jumpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
        }
        col.addView(jumpRow)

        // Sanskrit card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }
        sanskritText = TextView(this).apply {
            setTextColor(SAFFRON)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.35f)
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
            setPadding(0, dp(12), 0, dp(8))
        }
        col.addView(statusText)

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

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    if (s.loading) {
                        statusText.text = "Loading…"
                        return@collect
                    }
                    if (s.error != null) {
                        statusText.text = "Error: ${s.error}"
                        return@collect
                    }
                    val m = s.current ?: return@collect
                    titleBar.text = m.refLabel
                    sanskritText.text = m.sanskrit.ifBlank { "(no text)" }
                    metaText.text = buildString {
                        if (!m.devata.isNullOrBlank()) append("দেবতা: ${m.devata}  ")
                        if (!m.rishi.isNullOrBlank()) append("ঋষি: ${m.rishi}  ")
                        if (!m.chhanda.isNullOrBlank()) append("ছন্দ: ${m.chhanda}")
                    }
                    statusText.text = "${m.vedaName} · id ${m.id}"
                    renderVedaChips(s)
                    renderJump(s)
                }
            }
        }
    }

    private fun renderVedaChips(s: ReaderUiState) {
        vedaChips.removeAllViews()
        val currentId = s.current?.vedaId
        s.vedas.forEach { v ->
            val chip = TextView(this).apply {
                text = v.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                val selected = v.id == currentId
                setBackgroundColor(if (selected) SAFFRON else SURFACE)
                setTextColor(if (selected) Color.BLACK else IVORY)
                setOnClickListener { vm.openVeda(v.id) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            vedaChips.addView(chip, lp)
        }
    }

    private fun renderJump(s: ReaderUiState) {
        jumpRow.removeAllViews()
        val m = s.current ?: return

        fun addJump(label: String, value: String, options: List<Int>, onPick: (Int) -> Unit) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(SURFACE)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    if (options.isEmpty()) return@setOnClickListener
                    val labels = options.map { it.toString() }.toTypedArray()
                    AlertDialog.Builder(this@ReaderActivity)
                        .setTitle(label)
                        .setItems(labels) { _, which -> onPick(options[which]) }
                        .show()
                }
            }
            val l = TextView(this).apply {
                text = label
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
            }
            val v = TextView(this).apply {
                text = value
                setTextColor(IVORY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            box.addView(l)
            box.addView(v)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = dp(6) }
            jumpRow.addView(box, lp)
        }

        addJump("MANDAL", "${m.level1 ?: "—"}", s.level1Options) { vm.jumpLevel1(it) }
        addJump("SUKTA", "${m.level2 ?: "—"}", s.level2Options) { vm.jumpLevel2(it) }
        addJump("MANTRA", "${m.mantraNo ?: "—"}", s.mantraNoOptions) { vm.jumpMantraNo(it) }
    }
}
