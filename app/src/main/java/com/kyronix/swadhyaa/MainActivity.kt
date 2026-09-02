package com.kyronix.swadhyaa

import android.content.Intent
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
import com.kyronix.swadhyaa.presentation.reader.ReaderActivity
import kotlinx.coroutines.launch

/**
 * Home — 4 Vedas from core.db. Tap opens Reader.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels {
        val db = CoreDatabase.getInstance(applicationContext)
        val repo = VedaRepository(db)
        HomeViewModel.Factory(repo)
    }

    private lateinit var statusText: TextView
    private lateinit var vedaList: LinearLayout

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

        column.addView(TextView(this).apply {
            text = "স্বাধ্যায়"
            setTextColor(Color.parseColor("#F5E6C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setPadding(0, 0, 0, dp(8))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        })
        column.addView(TextView(this).apply {
            text = "সনাতন ধর্মশাস্ত্র"
            setTextColor(Color.parseColor("#C4A574"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, dp(24))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        })

        statusText = TextView(this).apply {
            text = "Loading…"
            setTextColor(Color.parseColor("#A89070"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, dp(16))
        }
        column.addView(statusText)

        vedaList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        column.addView(vedaList)

        root.addView(column)
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HomeUiState.Loading -> {
                            statusText.text = "Loading from core.db…"
                            vedaList.removeAllViews()
                        }
                        is HomeUiState.Success -> {
                            statusText.text = "Database OK — ${state.totalMantras} mantras total"
                            vedaList.removeAllViews()
                            state.vedas.forEach { v ->
                                val row = TextView(this@MainActivity).apply {
                                    text = "• ${v.name} (${v.code})\n  Mantras: ${v.mantraCount}"
                                    setTextColor(Color.parseColor("#F5E6C8"))
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                                    setLineSpacing(0f, 1.3f)
                                    setPadding(0, dp(12), 0, dp(12))
                                    setOnClickListener {
                                        startActivity(
                                            Intent(this@MainActivity, ReaderActivity::class.java)
                                                .putExtra(ReaderActivity.EXTRA_VEDA_ID, v.id)
                                        )
                                    }
                                }
                                vedaList.addView(row)
                            }
                        }
                        is HomeUiState.Error -> {
                            statusText.text = "Error: ${state.message}"
                            vedaList.removeAllViews()
                        }
                    }
                }
            }
        }
    }
}
