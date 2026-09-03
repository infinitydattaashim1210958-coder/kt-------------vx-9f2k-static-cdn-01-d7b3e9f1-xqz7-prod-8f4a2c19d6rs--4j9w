package com.kyronix.swadhyaa.presentation.ramayana

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
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
import com.kyronix.swadhyaa.data.local.RamayanaCoreDatabase
import com.kyronix.swadhyaa.data.repository.BhashyaField
import com.kyronix.swadhyaa.data.repository.RamayanaBhashyaRepository
import com.kyronix.swadhyaa.data.repository.RamayanaRepository
import com.kyronix.swadhyaa.data.repository.ShlokaContent
import kotlinx.coroutines.launch

/**
 * Ramayana Reader — same 3-box jump pattern as the Veda reader (কাণ্ড/সর্গ/শ্লোক
 * instead of মণ্ডল/সূক্ত/মন্ত্র), but visually distinct (copper accent instead of
 * saffron/gold) since there are 6 kandas instead of 4 vedas, and only one bhashya
 * source instead of many scholars.
 */
class RamayanaActivity : AppCompatActivity() {

    companion object {
        private val BG = Color.parseColor("#0F0D0A")
        private val SURFACE = Color.parseColor("#17130F")
        private val IVORY = Color.parseColor("#F5E6C8")
        private val GOLD = Color.parseColor("#C4A574")
        private val COPPER = Color.parseColor("#C1652B") // Ramayana accent — distinct from Veda's saffron
        private val MUTED = Color.parseColor("#A89070")
    }

    // Traditional Bangla names for the 6 kandas, keyed by the DB's English name column.
    private val kandaNameBn = mapOf(
        "Bala" to "বালকাণ্ড",
        "Ayodhya" to "অযোধ্যাকাণ্ড",
        "Aranya" to "অরণ্যকাণ্ড",
        "Kishkindha" to "কিষ্কিন্ধ্যাকাণ্ড",
        "Sundara" to "সুন্দরকাণ্ড",
        "Yuddha" to "যুদ্ধকাণ্ড"
    )

    private val vm: RamayanaViewModel by viewModels {
        val db = RamayanaCoreDatabase.getInstance(applicationContext)
        RamayanaViewModel.Factory(RamayanaRepository(db))
    }

    private lateinit var titleBar: TextView
    private lateinit var jumpRow: LinearLayout
    private lateinit var sanskritText: TextView
    private lateinit var statusText: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var bhashyaContent: LinearLayout

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
            text = "রামায়ণ"
            setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(back)
        header.addView(titleBar)
        col.addView(header)

        // কাণ্ড / সর্গ / শ্লোক — same 3-box pattern as the Veda reader
        jumpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, dp(12))
        }
        col.addView(jumpRow)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }
        sanskritText = TextView(this).apply {
            setTextColor(COPPER)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.35f)
            text = "…"
        }
        card.addView(sanskritText)
        col.addView(card)

        statusText = TextView(this).apply {
            setTextColor(GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(12), 0, dp(8))
        }
        col.addView(statusText)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        btnPrev = Button(this).apply {
            text = "← আগের শ্লোক"
            setOnClickListener { vm.prev() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnNext = Button(this).apply {
            text = "পরের শ্লোক →"
            setOnClickListener { vm.next() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nav.addView(btnPrev)
        nav.addView(btnNext)
        col.addView(nav)

        // ভাষ্য — একটাই উৎস (স্কলার বাছাইয়ের দরকার নেই, বেদের মতো)
        val bhashyaHeader = TextView(this).apply {
            text = "ভাষ্য"
            setTextColor(GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(24), 0, dp(4))
        }
        val bhashyaSource = TextView(this).apply {
            text = "${RamayanaBhashyaRepository.LANGUAGE_LABEL} · ${RamayanaBhashyaRepository.SOURCE_LABEL}"
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(10))
        }
        bhashyaContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(bhashyaHeader)
        col.addView(bhashyaSource)
        col.addView(bhashyaContent)

        root.addView(col)
        return root
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    val m = s.current ?: return@collect
                    titleBar.text = "রামায়ণ ${kandaNameBn[m.kandaName] ?: m.kandaName}"
                    sanskritText.text = m.sanskrit.ifBlank { "(no text)" }
                    statusText.text = "${kandaNameBn[m.kandaName] ?: m.kandaName} · সর্গ ${m.sargaChapter} · শ্লোক ${m.shlokaNo}"
                    renderJump(s)
                    loadBhashya(m)
                    if (s.jumpError != null) {
                        Toast.makeText(this@RamayanaActivity, s.jumpError, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun renderJump(s: RamayanaUiState) {
        jumpRow.removeAllViews()
        val m = s.current ?: return

        fun addBox(label: String, value: String, onTap: () -> Unit) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(SURFACE)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { onTap() }
            }
            box.addView(TextView(this).apply {
                text = label
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
            })
            box.addView(TextView(this).apply {
                text = value
                setTextColor(IVORY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            })
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = dp(6) }
            jumpRow.addView(box, lp)
        }

        addBox("কাণ্ড", kandaNameBn[m.kandaName] ?: m.kandaName) {
            val names = s.kandas.map { kandaNameBn[it.name] ?: it.name ?: "" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("কাণ্ড")
                .setItems(names) { _, which -> s.kandas[which].id?.let { vm.openKanda(it) } }
                .show()
        }
        addBox("সর্গ", "${m.sargaChapter}") {
            if (s.sargaOptions.isEmpty()) return@addBox
            val labels = s.sargaOptions.map { it.toString() }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("সর্গ")
                .setItems(labels) { _, which -> vm.jumpSarga(s.sargaOptions[which]) }
                .show()
        }
        addBox("শ্লোক", "${m.shlokaNo}") {
            if (s.shlokaNoOptions.isEmpty()) return@addBox
            val labels = s.shlokaNoOptions.map { it.toString() }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("শ্লোক")
                .setItems(labels) { _, which -> vm.jumpShlokaNo(s.shlokaNoOptions[which]) }
                .show()
        }
    }

    private fun loadBhashya(m: ShlokaContent) {
        bhashyaContent.removeAllViews()

        if (!RamayanaBhashyaRepository.isDownloaded(this, m.kandaId)) {
            val info = TextView(this).apply {
                text = "এই কাণ্ডের ভাষ্য ডাউনলোড করা হয়নি"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, 0, 0, dp(10))
            }
            val downloadBtn = Button(this).apply {
                text = "ডাউনলোড করুন"
                setOnClickListener {
                    text = "ডাউনলোড হচ্ছে…"
                    isEnabled = false
                    lifecycleScope.launch {
                        val result = RamayanaBhashyaRepository.downloadIfNeeded(this@RamayanaActivity, m.kandaId)
                        if (result.isSuccess) {
                            loadBhashya(m)
                        } else {
                            Toast.makeText(
                                this@RamayanaActivity,
                                "ডাউনলোড ব্যর্থ হয়েছে: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            text = "ডাউনলোড করুন"
                            isEnabled = true
                        }
                    }
                }
            }
            bhashyaContent.addView(info)
            bhashyaContent.addView(downloadBtn)
            return
        }

        val loading = TextView(this).apply {
            text = "লোড হচ্ছে…"
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        bhashyaContent.addView(loading)

        lifecycleScope.launch {
            val result = RamayanaBhashyaRepository.getBhashya(this@RamayanaActivity, m.kandaId, m.id)
            bhashyaContent.removeView(loading)
            result.onSuccess { fields -> renderBhashyaFields(fields) }
                .onFailure {
                    val err = TextView(this@RamayanaActivity).apply {
                        text = "ভাষ্য পড়া যায়নি: ${it.message}"
                        setTextColor(MUTED)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    }
                    bhashyaContent.addView(err)
                }
        }
    }

    private fun renderBhashyaFields(fields: List<BhashyaField>) {
        if (fields.isEmpty()) {
            bhashyaContent.addView(TextView(this).apply {
                text = "এই শ্লোকের জন্য কোনো তথ্য নেই"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            return
        }
        fields.forEach { field ->
            bhashyaContent.addView(TextView(this).apply {
                text = field.label
                setTextColor(GOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(10), 0, dp(2))
            })
            bhashyaContent.addView(TextView(this).apply {
                text = field.value
                setTextColor(IVORY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.3f)
            })
        }
    }
}
