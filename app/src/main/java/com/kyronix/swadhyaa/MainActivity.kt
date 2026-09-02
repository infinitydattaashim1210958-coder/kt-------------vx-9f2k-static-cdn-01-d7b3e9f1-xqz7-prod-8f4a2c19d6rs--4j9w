package com.kyronix.swadhyaa

import android.os.Bundle
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
 * Minimal Home screen.
 * Proves: Room → Repository → ViewModel → UI with real production data.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels {
        val db = CoreDatabase.getInstance(applicationContext)
        val repo = VedaRepository(db)
        HomeViewModel.Factory(repo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val vedaListText = findViewById<TextView>(R.id.vedaListText)

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
