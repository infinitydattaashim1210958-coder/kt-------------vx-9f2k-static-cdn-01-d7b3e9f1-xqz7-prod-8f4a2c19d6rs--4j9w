package com.kyronix.swadhyaa.presentation.audio

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kyronix.swadhyaa.service.MantraPlayerService
import kotlinx.coroutines.launch

/**
 * Dedicated full-screen "Listening Mode" player styled like a standalone
 * music-player screen (dark bg, centered glow, big transport controls) —
 * the glowing mantra text stands in for the album-art / waveform area.
 * MantraPlayerService keeps auto-advancing exactly as before; this screen
 * just observes + controls it (prev/next reuse the same ACTION_PREV/
 * ACTION_NEXT intents the notification buttons already send).
 */
class ListeningModeActivity : AppCompatActivity() {

    // Pink/black palette — deliberately distinct from the app's normal
    // amber reading theme, matching the reference music-player look.
    private val cBg        = Color.parseColor("#0A0510")
    private val cAccent    = Color.parseColor("#FF2F92")
    private val cAccentDim = Color.parseColor("#4A1030")
    private val cWhite     = Color.parseColor("#F5F0F5")
    private val cMuted     = Color.parseColor("#B58AA5")

    private lateinit var audioVm: AudioPlayerViewModel
    private lateinit var mantraView: MantraTextAnimView
    private lateinit var tvTitle: TextView
    private lateinit var tvPos: TextView
    private lateinit var tvDur: TextView
    private lateinit var seek: SeekBar
    private lateinit var btnPlayPause: TextView

    private var lastShownRef: String? = null
    private var hasSeenActive = false
    private var userSeeking = false

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioVm = ViewModelProvider(
            this, AudioPlayerViewModel.Factory(applicationContext)
        )[AudioPlayerViewModel::class.java]
        setContentView(buildUi())
        observe()
    }

    override fun onStart() { super.onStart(); audioVm.bindService(this) }
    override fun onStop()  { super.onStop();  audioVm.unbindService(this) }
    override fun onBackPressed() { exitListeningMode() }

    private fun exitListeningMode() {
        audioVm.setListeningMode(false)
        finish()
    }

    // ── UI construction ──────────────────────────────────────────────────

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "✕"
            setTextColor(cMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { exitListeningMode() }
        })

        // Center glow area — mantra text stands in for the waveform/art
        val glowArea = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(cAccentDim, Color.TRANSPARENT)
            ).apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(260).toFloat()
            }
        }
        mantraView = MantraTextAnimView(this)
        glowArea.addView(mantraView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(glowArea, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(12); bottomMargin = dp(16) })

        // Kicker + title — like "YOUR PLAYLIST" / "Favourites"
        root.addView(TextView(this).apply {
            text = "শ্রবণ মোড"
            setTextColor(cAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.15f
        })
        tvTitle = TextView(this).apply {
            setTextColor(cWhite)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, dp(2), 0, dp(16))
        }
        root.addView(tvTitle)

        // Time row
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tvPos = TextView(this).apply { setTextColor(cMuted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); text = "0:00" }
        tvDur = TextView(this).apply { setTextColor(cMuted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); text = "0:00" }
        timeRow.addView(tvPos)
        timeRow.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        timeRow.addView(tvDur)
        root.addView(timeRow)

        // Progress bar
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(cAccent)
            thumbTintList = ColorStateList.valueOf(cAccent)
            progressBackgroundTintList = ColorStateList.valueOf(cAccentDim)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(sb: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    userSeeking = false
                    audioVm.seekTo((sb?.progress ?: 0) / 1000f)
                }
            })
        }
        root.addView(seek, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4); bottomMargin = dp(24) })

        // Controls: prev / play-pause / next
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(iconButton("⏮", 22f) {
            startService(Intent(this, MantraPlayerService::class.java).setAction(MantraPlayerService.ACTION_PREV))
        }, LinearLayout.LayoutParams(dp(56), dp(56)))

        btnPlayPause = TextView(this).apply {
            text = "▶"
            setTextColor(cBg)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cWhite) }
            setOnClickListener { audioVm.togglePlayPause() }
        }
        controls.addView(btnPlayPause, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            marginStart = dp(24); marginEnd = dp(24)
        })

        controls.addView(iconButton("⏭", 22f) {
            startService(Intent(this, MantraPlayerService::class.java).setAction(MantraPlayerService.ACTION_NEXT))
        }, LinearLayout.LayoutParams(dp(56), dp(56)))

        root.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }

    private fun iconButton(glyph: String, textSizeSp: Float, onClick: () -> Unit) =
        TextView(this).apply {
            text = glyph
            setTextColor(cAccent)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            setOnClickListener { onClick() }
        }

    // ── State observation ────────────────────────────────────────────────

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                audioVm.state.collect { s ->
                    val ref = s.currentRef
                    if (ref != null) {
                        hasSeenActive = true
                        tvTitle.text = "${refVedaLabel(ref.vedaCode)} · ${ref.displayLabel}"
                        if (ref.mantraRefId != lastShownRef) {
                            lastShownRef = ref.mantraRefId
                            mantraView.showMantra(ref.devanagariText, ref.displayLabel)
                        }
                        btnPlayPause.text = if (s.isPlaying) "❚❚" else "▶"
                        if (!userSeeking) seek.progress = (s.fraction * 1000).toInt()
                        tvPos.text = msStr(s.positionMs)
                        tvDur.text = msStr(s.durationMs)
                    }
                    if (hasSeenActive && !s.isListeningMode) finish()
                }
            }
        }
    }

    private fun refVedaLabel(code: String) = when (code.trim().lowercase()) {
        "rigveda"     -> "Rigveda"
        "yajurveda"   -> "Yajurveda"
        "samaveda"    -> "Samaveda"
        "atharvaveda" -> "Atharvaveda"
        else          -> code
    }

    private fun msStr(ms: Int): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(sec / 60, sec % 60)
    }
}
