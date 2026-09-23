package com.kyronix.swadhyaa.presentation.audio

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kyronix.swadhyaa.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * Dedicated full-screen "Listening Mode" player — only the glowing mantra
 * text + a slim playback bar, entered when the Reading→Listening toggle
 * is tapped (instead of the old in-place overlay). MantraPlayerService
 * keeps auto-advancing exactly as before; this screen just observes it.
 */
class ListeningModeActivity : AppCompatActivity() {

    private lateinit var audioVm: AudioPlayerViewModel
    private lateinit var mantraView: MantraTextAnimView
    private lateinit var playerBar: MantraAudioPlayerView
    private var lastShownRef: String? = null
    private var hasSeenActive = false

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioVm = ViewModelProvider(
            this,
            AudioPlayerViewModel.Factory(applicationContext)
        )[AudioPlayerViewModel::class.java]

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bg)
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "✕  পঠন মোডে ফিরুন"
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.START
            setPadding(dp(4), dp(4), dp(16), dp(20))
            setOnClickListener { exitListeningMode() }
        })

        mantraView = MantraTextAnimView(this)
        root.addView(mantraView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        playerBar = MantraAudioPlayerView(this)
        root.addView(playerBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        setContentView(root)

        playerBar.bind(this, audioVm)
        observe()
    }

    override fun onStart() {
        super.onStart()
        audioVm.bindService(this)
    }

    override fun onStop() {
        super.onStop()
        audioVm.unbindService(this)
    }

    override fun onBackPressed() {
        exitListeningMode()
    }

    private fun exitListeningMode() {
        audioVm.setListeningMode(false)
        finish()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                audioVm.state.collect { s ->
                    val ref = s.currentRef
                    if (ref != null) {
                        hasSeenActive = true
                        playerBar.currentMantraId = ref.mantraId
                        if (ref.mantraRefId != lastShownRef) {
                            lastShownRef = ref.mantraRefId
                            mantraView.showMantra(ref.devanagariText, ref.displayLabel)
                        }
                    }
                    if (hasSeenActive && !s.isListeningMode) {
                        finish()
                    }
                }
            }
        }
    }
}
