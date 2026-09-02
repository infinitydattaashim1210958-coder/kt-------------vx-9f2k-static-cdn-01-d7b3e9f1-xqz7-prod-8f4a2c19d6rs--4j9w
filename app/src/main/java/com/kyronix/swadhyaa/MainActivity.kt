package com.kyronix.swadhyaa

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
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
import com.kyronix.swadhyaa.domain.model.VedaSummary
import com.kyronix.swadhyaa.presentation.home.HomeUiState
import com.kyronix.swadhyaa.presentation.home.HomeViewModel
import com.kyronix.swadhyaa.presentation.reader.ReaderActivity
import kotlinx.coroutines.launch

/**
 * Home screen — topic grid ported 1:1 from the old Capacitor app's HOME_SECTIONS
 * (www/js/app.js). Available topics open their reader; "soon" topics are disabled.
 */
class MainActivity : AppCompatActivity() {

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

    // 1:1 port of HOME_SECTIONS from the old app/js/app.js
    private val sections = listOf(
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

    private companion object {
        val BG = Color.parseColor("#0F0D0A")
        val SURFACE = Color.parseColor("#17130F")
        val IVORY = Color.parseColor("#F5E6C8")
        val GOLD = Color.parseColor("#C4A574")
        val MUTED = Color.parseColor("#A89070")
    }

    private val viewModel: HomeViewModel by viewModels {
        val db = CoreDatabase.getInstance(applicationContext)
        HomeViewModel.Factory(VedaRepository(db))
    }

    private var vedaSummaries: List<VedaSummary> = emptyList()
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this).apply {
            setBackgroundColor(BG)
            setFillViewport(true)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        column.addView(TextView(this).apply {
            text = "স্বাধ্যায়"
            setTextColor(IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        })
        column.addView(TextView(this).apply {
            text = "ও৩ম্ কৃণ্বন্তো বিশ্বমার্যম্"
            setTextColor(GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, dp(4), 0, dp(24))
        })

        val grid = GridLayout(this).apply {
            columnCount = 3
            useDefaultMargins = false
        }
        sections.forEachIndexed { index, section ->
            val enabled = section.action != HomeAction.Soon
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(SURFACE)
                setPadding(dp(8), dp(16), dp(8), dp(16))
                alpha = if (enabled) 1f else 0.4f
                setOnClickListener { onSectionTap(section) }
            }
            card.addView(TextView(this).apply {
                text = section.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                gravity = Gravity.CENTER
            })
            card.addView(TextView(this).apply {
                text = section.label
                setTextColor(if (enabled) IVORY else MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            if (!enabled) {
                card.addView(TextView(this).apply {
                    text = "শীঘ্রই"
                    setTextColor(MUTED)
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
            grid.addView(card, params)
        }
        column.addView(grid)
        root.addView(column)
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is HomeUiState.Success) vedaSummaries = state.vedas
                }
            }
        }
    }

    private fun onSectionTap(section: HomeSection) {
        when (section.action) {
            HomeAction.Vedas -> openVedaPicker()
            HomeAction.Ramayana, HomeAction.Mahabharata, HomeAction.Library ->
                Toast.makeText(this, "${section.label} স্ক্রিন শীঘ্রই যুক্ত হবে", Toast.LENGTH_SHORT).show()
            HomeAction.Soon ->
                Toast.makeText(this, "${section.label} শীঘ্রই আসছে", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVedaPicker() {
        if (vedaSummaries.isEmpty()) return
        val names = vedaSummaries.map {
            when (it.code.lowercase()) {
                "rigveda" -> "ঋগ্বেদ"
                "yajurveda" -> "যজুর্বেদ"
                "samaveda" -> "সামবেদ"
                "atharvaveda" -> "অথর্ববেদ"
                else -> it.name
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("বেদ নির্বাচন করুন")
            .setItems(names) { _, which ->
                startActivity(
                    Intent(this, ReaderActivity::class.java)
                        .putExtra(ReaderActivity.EXTRA_VEDA_ID, vedaSummaries[which].id)
                )
            }
            .show()
    }
}
