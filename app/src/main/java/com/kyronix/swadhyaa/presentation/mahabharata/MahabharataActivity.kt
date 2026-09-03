package com.kyronix.swadhyaa.presentation.mahabharata

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
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kyronix.swadhyaa.data.repository.Adhyay
import com.kyronix.swadhyaa.data.repository.MahabharataRepository
import com.kyronix.swadhyaa.data.repository.ParbaInfo
import com.kyronix.swadhyaa.data.repository.Upakhyan
import kotlinx.coroutines.launch

/**
 * Mahabharata Reader.
 * Row 1: পর্ব chips (18, horizontal scroll) + a bigger ভাষ্য box (translator — one today,
 *        কালীপ্রসন্ন সিংহ অনূদিত, kept as a picker so more can be added like Veda scholars).
 * Row 2: one long box — তap to pick অধ্যায় by its full title.
 * Below: the adhyay's upakhyanas rendered as subheading (বিষয়) + prose (content).
 */
class MahabharataActivity : AppCompatActivity() {

    companion object {
        private val BG = Color.parseColor("#0F0D0A")
        private val SURFACE = Color.parseColor("#17130F")
        private val IVORY = Color.parseColor("#F5E6C8")
        private val GOLD = Color.parseColor("#C4A574")
        private val STEEL = Color.parseColor("#7C93A8") // Mahabharata accent — distinct from Veda/Ramayana
        private val MUTED = Color.parseColor("#A89070")
    }

    private val vm: MahabharataViewModel by viewModels {
        MahabharataViewModel.Factory(applicationContext)
    }

    private lateinit var parbaRow: LinearLayout
    private lateinit var adhyayBox: LinearLayout
    private lateinit var adhyayLabel: TextView
    private lateinit var contentArea: LinearLayout
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
        header.addView(TextView(this).apply {
            text = "মহাভারত"
            setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(header)

        // Row 1: পর্ব chips + ভাষ্য box
        val row1Scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(16), 0, dp(8))
        }
        parbaRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1Scroll.addView(parbaRow)
        col.addView(row1Scroll)

        // Row 2: long অধ্যায় box
        adhyayBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        adhyayLabel = TextView(this).apply {
            setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            text = "…"
        }
        adhyayBox.addView(TextView(this).apply {
            text = "অধ্যায়"
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        })
        adhyayBox.addView(adhyayLabel)
        col.addView(adhyayBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4); bottomMargin = dp(12) })

        contentArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(contentArea)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        btnPrev = Button(this).apply {
            text = "← আগের অধ্যায়"
            setOnClickListener { vm.prevAdhyay() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnNext = Button(this).apply {
            text = "পরের অধ্যায় →"
            setOnClickListener { vm.nextAdhyay() }
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
                    renderParbaRow(s)
                    adhyayLabel.text = s.selectedAdhyay?.title ?: "—"
                    adhyayBox.setOnClickListener {
                        if (s.adhyayas.isEmpty()) return@setOnClickListener
                        val titles = s.adhyayas.map { it.title }.toTypedArray()
                        AlertDialog.Builder(this@MahabharataActivity)
                            .setTitle("অধ্যায় নির্বাচন করুন")
                            .setItems(titles) { _, which -> vm.selectAdhyay(s.adhyayas[which]) }
                            .show()
                    }
                    renderContent(s)
                    s.downloadError?.let {
                        Toast.makeText(this@MahabharataActivity, it, Toast.LENGTH_LONG).show()
                    }
                    s.error?.let {
                        Toast.makeText(this@MahabharataActivity, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun renderParbaRow(s: MahabharataUiState) {
        parbaRow.removeAllViews()
        s.parbas.forEach { parba ->
            val selected = parba.parbaNo == s.selectedParba.parbaNo
            val chip = TextView(this).apply {
                text = parba.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setBackgroundColor(if (selected) STEEL else SURFACE)
                setTextColor(if (selected) Color.BLACK else IVORY)
                setOnClickListener { vm.selectParba(parba) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            parbaRow.addView(chip, lp)
        }
        // ভাষ্য box — bigger than the পর্ব chips; only one translator today, but tappable/extensible
        val bhashyaBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(GOLD)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                Toast.makeText(this@MahabharataActivity, "এই মুহূর্তে একটাই ভাষ্য উপলব্ধ", Toast.LENGTH_SHORT).show()
            }
        }
        bhashyaBox.addView(TextView(this).apply {
            text = "ভাষ্য"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
        })
        bhashyaBox.addView(TextView(this).apply {
            text = MahabharataRepository.TRANSLATOR_LABEL
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
        })
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(6) }
        parbaRow.addView(bhashyaBox, lp)
    }

    private fun renderContent(s: MahabharataUiState) {
        contentArea.removeAllViews()

        if (!s.downloaded) {
            val sizeKb = s.selectedParba.packSizeBytes / 1024
            contentArea.addView(TextView(this).apply {
                text = "${s.selectedParba.name} ডাউনলোড করা হয়নি (${sizeKb} KB, ${s.selectedParba.adhyayCount} অধ্যায়)"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(12), 0, dp(10))
            })
            contentArea.addView(Button(this).apply {
                text = if (s.downloading) "ডাউনলোড হচ্ছে…" else "ডাউনলোড করুন"
                isEnabled = !s.downloading
                setOnClickListener { vm.download() }
            })
            return
        }

        if (s.loadingContent) {
            contentArea.addView(TextView(this).apply {
                text = "লোড হচ্ছে…"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(12), 0, 0)
            })
            return
        }

        if (s.upakhyanas.isEmpty()) {
            contentArea.addView(TextView(this).apply {
                text = "এই অধ্যায়ে কোনো বিষয়বস্তু নেই"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(12), 0, 0)
            })
            return
        }

        s.upakhyanas.forEach { u: Upakhyan ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(SURFACE)
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }
            if (!u.bishoy.isNullOrBlank()) {
                card.addView(TextView(this).apply {
                    text = u.bishoy
                    setTextColor(GOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, dp(6))
                })
            }
            card.addView(TextView(this).apply {
                text = u.content
                setTextColor(IVORY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setLineSpacing(0f, 1.4f)
            })
            contentArea.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) })
        }
    }
}
