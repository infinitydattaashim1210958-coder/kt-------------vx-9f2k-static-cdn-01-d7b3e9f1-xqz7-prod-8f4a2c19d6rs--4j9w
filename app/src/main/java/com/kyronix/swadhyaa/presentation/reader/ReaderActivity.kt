package com.kyronix.swadhyaa.presentation.reader

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
                    if (s.jumpError != null) {
                        Toast.makeText(this@ReaderActivity, s.jumpError, Toast.LENGTH_SHORT).show()
                    }
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

    /**
     * Veda-specific numbering scheme, keyed by veda code:
     *  - rigveda:      মণ্ডল / সূক্ত / মন্ত্র
     *  - samaveda:     শুধু মন্ত্র
     *  - yajurveda:    অধ্যায় / মন্ত্র
     *  - atharvaveda:  কাণ্ড / সূক্ত / মন্ত্র
     */
    private data class JumpScheme(val level1Label: String?, val level2Label: String?)

    private fun schemeFor(vedaCode: String): JumpScheme = when (vedaCode.lowercase()) {
        "rigveda" -> JumpScheme("মণ্ডল", "সূক্ত")
        "samaveda" -> JumpScheme(null, null)
        "yajurveda" -> JumpScheme("অধ্যায়", null)
        "atharvaveda" -> JumpScheme("কাণ্ড", "সূক্ত")
        else -> JumpScheme("মণ্ডল", "সূক্ত")
    }

    private fun renderJump(s: ReaderUiState) {
        jumpRow.removeAllViews()
        val m = s.current ?: return
        val scheme = schemeFor(m.vedaCode)

        fun addListJump(label: String, value: String, options: List<Int>, onPick: (Int) -> Unit) {
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

        // মন্ত্র নং লিখে "ঠিক আছে" চাপার সিস্টেম — লম্বা তালিকা স্ক্রল করার বদলে সরাসরি সংখ্যা লেখা যায়।
        // এটাই সামবেদের ১৮৭৫+ মন্ত্রের মধ্যে খোঁজার জন্য মূল সমাধান, বাকি বেদেও একই সিস্টেম ব্যবহার হচ্ছে।
        fun addMantraNoJump(value: String, options: List<Int>, onPick: (Int) -> Unit) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(SURFACE)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    val input = EditText(this@ReaderActivity).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER
                        hint = if (options.isNotEmpty())
                            "${options.first()} - ${options.last()}"
                        else "মন্ত্র নং"
                        setText(value.takeIf { it != "—" }.orEmpty())
                        setSelection(text.length)
                    }
                    val container = LinearLayout(this@ReaderActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(20), dp(12), dp(20), dp(0))
                        addView(input)
                    }
                    AlertDialog.Builder(this@ReaderActivity)
                        .setTitle("মন্ত্র নং লিখুন")
                        .setView(container)
                        .setPositiveButton("ঠিক আছে") { _, _ ->
                            val no = input.text.toString().trim().toIntOrNull()
                            if (no != null) onPick(no)
                            else Toast.makeText(
                                this@ReaderActivity, "সঠিক সংখ্যা লিখুন", Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton("বাতিল", null)
                        .show()
                }
            }
            val l = TextView(this).apply {
                text = "মন্ত্র"
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

        if (scheme.level1Label != null) {
            addListJump(scheme.level1Label, "${m.level1 ?: "—"}", s.level1Options) { vm.jumpLevel1(it) }
        }
        if (scheme.level2Label != null) {
            addListJump(scheme.level2Label, "${m.level2 ?: "—"}", s.level2Options) { vm.jumpLevel2(it) }
        }
        addMantraNoJump("${m.mantraNo ?: "—"}", s.mantraNoOptions) { vm.jumpMantraNo(it) }
    }
}
