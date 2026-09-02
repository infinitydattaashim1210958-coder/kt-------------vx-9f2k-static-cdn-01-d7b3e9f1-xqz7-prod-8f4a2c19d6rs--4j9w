package com.kyronix.swadhyaa

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.repository.VedaRepository
import com.kyronix.swadhyaa.presentation.home.HomeUiState
import com.kyronix.swadhyaa.presentation.home.HomeViewModel
import kotlinx.coroutines.launch

/**
 * Minimal Home — programmatic UI (no XML layout dependency).
 * Proves: Room → Repository → ViewModel → UI with real core.db data.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels {
        val db = CoreDatabase.getInstance(applicationContext)
        val repo = VedaRepository(db)
        HomeViewModel.Factory(repo)
    }

    private lateinit var statusText: TextView
    private lateinit var vedaListText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F0D0A"))
            setFillViewport(true)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val title = TextView(this).apply {
            text = "স্বাধ্যায়"
            setTextColor(Color.parseColor("#F5E6C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setPadding(0, 0, 0, dp(8))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }

        val subtitle = TextView(this).apply {
            text = "সনাতন ধর্মশাস্ত্র"
            setTextColor(Color.parseColor("#C4A574"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, dp(24))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }

        statusText = TextView(this).apply {
            text = "Loading…"
            setTextColor(Color.parseColor("#A89070"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, dp(16))
        }

        vedaListText = TextView(this).apply {
            setTextColor(Color.parseColor("#F5E6C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.3f)
        }

        column.addView(title)
        column.addView(subtitle)
        column.addView(statusText)
        column.addView(vedaListText)
        root.addView(column)
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HomeUiState.Loading -> {
                            statusText.text = "Loading from core.db…"
                            vedaListText.text = ""
                        }
                        is HomeUiState.Success -> {
                            statusText.text = "Database OK — ${state.totalMantras} mantras total"
                            vedaListText.text = state.vedas.joinToString("\n\n") { v ->
                                "• ${v.name} (${v.code})\n  Mantras: ${v.mantraCount}"
                            }
                        }
                        is HomeUiState.Error -> {
                            statusText.text = "Error: ${state.message}"
                            vedaListText.text = ""
                        }
                    }
                }
            }
        }
    }
}
