package com.kyronix.swadhyaa.presentation.audio

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kyronix.swadhyaa.service.MantraPlayerService
import kotlinx.coroutines.launch

/**
 * Full-screen "Listening Mode" audio player — PixelPlayer-inspired design.
 *
 * Visual structure (bottom-up z-order):
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  OmSymbolView — animated 3D ওঁ + halo + sparks          │  (fills ~55% of screen)
 *  │──────────────────────────────────────────────────────────│
 *  │  Glowing synced mantra text (MantraTextAnimView)         │  (overlays Om area)
 *  │──────────────────────────────────────────────────────────│
 *  │  Veda + mantra label                                     │
 *  │  Progress bar (wavy style, saffron)                      │
 *  │  Time row                                                │
 *  │  Transport controls  ⏮  ❚❚/▶  ⏭                        │
 *  └──────────────────────────────────────────────────────────│
 *
 * This Activity is launched by:
 *  (a) Tapping the dedicated "🎧 Audio Player" button in MantraAudioPlayerView
 *  (b) Tapping the mode-toggle icon in MantraAudioPlayerView when audio is active
 *
 * MantraPlayerService continues to run/auto-advance; this screen
 * only observes + controls it.
 */
class ListeningModeActivity : AppCompatActivity() {

    // ── Palette (PixelPlayer-inspired: deep cosmic, saffron/gold accents) ──────
    private val cBg        = Color.parseColor("#06030F")
    private val cBgMid     = Color.parseColor("#110820")
    private val cSaffron   = Color.parseColor("#FF9A3C")
    private val cGold      = Color.parseColor("#D4A24C")
    private val cAmber     = Color.parseColor("#F0C26A")
    private val cGoldDim   = Color.parseColor("#3D2A08")
    private val cWhite     = Color.parseColor("#F5EED8")
    private val cMuted     = Color.parseColor("#8D7A55")
    private val cSurface   = Color.argb(180, 12, 8, 24)

    private lateinit var audioVm: AudioPlayerViewModel
    private lateinit var omView: OmSymbolView
    private lateinit var mantraView: MantraTextAnimView
    private lateinit var tvVedaLabel: TextView
    private lateinit var tvMantraLabel: TextView
    private lateinit var tvPos: TextView
    private lateinit var tvDur: TextView
    private lateinit var seek: SeekBar
    private lateinit var btnPlayPause: TextView
    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView
    private lateinit var tvRepeatHint: TextView

    private var lastShownRef: String? = null
    private var hasSeenActive = false
    private var userSeeking = false

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()
    private fun dp(v: Float) = (v * density)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge, hide status/nav bars for immersive feel
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        audioVm = ViewModelProvider(
            this, AudioPlayerViewModel.Factory(applicationContext)
        )[AudioPlayerViewModel::class.java]

        setContentView(buildUi())
        observe()
    }

    override fun onStart() { super.onStart(); audioVm.bindService(this) }
    override fun onStop()  { super.onStop();  audioVm.unbindService(this) }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { exitListeningMode() }

    private fun exitListeningMode() {
        audioVm.setListeningMode(false)
        finish()
    }

    // ── UI Construction ───────────────────────────────────────────────────────

    private fun buildUi(): View {
        // Root: full-screen FrameLayout for overlay layering
        val root = FrameLayout(this).apply {
            setBackgroundColor(cBg)
        }

        // ── Layer 1: Om symbol (fills upper ~58% of screen) ──────────────────
        omView = OmSymbolView(this)
        root.addView(omView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // ── Layer 2: bottom glass panel that houses controls ──────────────────
        val panel = buildBottomPanel()
        root.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))

        // ── Layer 3: mantra text view — floats in centre of Om area ──────────
        mantraView = MantraTextAnimView(this)
        // We position it above the panel; use a vertical bias via margin trick
        val mantraParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            bottomMargin = dp(320)   // lifts above controls panel
            leftMargin   = dp(32)
            rightMargin  = dp(32)
        }
        root.addView(mantraView, mantraParams)

        // ── Layer 4: close button top-left ────────────────────────────────────
        val btnClose = TextView(this).apply {
            text = "✕"
            setTextColor(cMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(16), dp(48), dp(16), dp(16))
            setOnClickListener { exitListeningMode() }
        }
        root.addView(btnClose, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.TOP
        ))

        return root
    }

    /** Glass-morphic bottom panel with title, seeker, and controls. */
    private fun buildBottomPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(36))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(cSurface)
                cornerRadii = floatArrayOf(dp(28f), dp(28f), dp(28f), dp(28f), 0f, 0f, 0f, 0f)
            }
        }

        // Drag handle indicator (like a bottom sheet)
        val handle = View(this).apply {
            setBackgroundColor(cGoldDim)
            background = GradientDrawable().apply {
                shape  = GradientDrawable.RECTANGLE
                setColor(cMuted)
                cornerRadius = dp(2f)
            }
        }
        val handleParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(20)
        }
        panel.addView(handle, handleParams)

        // Veda label (like "YOUR PLAYLIST" in PixelPlayer)
        tvVedaLabel = TextView(this).apply {
            setTextColor(cSaffron)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.12f
            text = "শ্রবণ মোড"
        }
        panel.addView(tvVedaLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) })

        // Mantra reference label (like "Favourites" / track title)
        tvMantraLabel = TextView(this).apply {
            setTextColor(cWhite)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            maxLines = 2
            text = ""
        }
        panel.addView(tvMantraLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(20) })

        // Seek bar
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList           = ColorStateList.valueOf(cSaffron)
            thumbTintList              = ColorStateList.valueOf(cAmber)
            progressBackgroundTintList = ColorStateList.valueOf(cGoldDim)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(sb: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    userSeeking = false
                    audioVm.seekTo((sb?.progress ?: 0) / 1000f)
                }
            })
        }
        panel.addView(seek, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        // Time row
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tvPos = tv("0:00", 11f, cMuted)
        tvDur = tv("0:00", 11f, cMuted).apply { gravity = Gravity.END }
        timeRow.addView(tvPos)
        timeRow.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        timeRow.addView(tvDur)
        panel.addView(timeRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(28) })

        // Transport controls row
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        }

        // Prev button
        btnPrev = tv("⏮", 24f, cGold).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                startService(
                    Intent(this@ListeningModeActivity, MantraPlayerService::class.java)
                        .setAction(MantraPlayerService.ACTION_PREV)
                )
            }
        }
        controls.addView(btnPrev, LinearLayout.LayoutParams(dp(56), dp(56)))

        // Play/Pause — large glowing pill button (PixelPlayer style)
        btnPlayPause = TextView(this).apply {
            text = "▶"
            setTextColor(cBg)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cAmber)
                // Outer glow via shadow — needs software layer on parent
            }
            elevation = dp(4f)
            setOnClickListener { audioVm.togglePlayPause() }
        }
        val ppParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            marginStart = dp(28); marginEnd = dp(28)
        }
        controls.addView(btnPlayPause, ppParams)

        // Next button
        btnNext = tv("⏭", 24f, cGold).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                startService(
                    Intent(this@ListeningModeActivity, MantraPlayerService::class.java)
                        .setAction(MantraPlayerService.ACTION_NEXT)
                )
            }
        }
        controls.addView(btnNext, LinearLayout.LayoutParams(dp(56), dp(56)))

        panel.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Subtle hint text
        tvRepeatHint = tv("অটো-অগ্রসর সক্রিয়", 10f, cMuted).apply {
            gravity = Gravity.CENTER
        }
        panel.addView(tvRepeatHint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        return panel
    }

    // ── State Observation ─────────────────────────────────────────────────────

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                audioVm.state.collect { s ->
                    val ref = s.currentRef
                    if (ref != null) {
                        hasSeenActive = true

                        // Veda label e.g. "ঋগ্বেদ · শ্রবণ মোড"
                        tvVedaLabel.text = "${refVedaLabel(ref.vedaCode)} · শ্রবণ মোড"
                        tvMantraLabel.text = ref.displayLabel

                        // Update mantra animation view on mantra change
                        if (ref.mantraRefId != lastShownRef) {
                            lastShownRef = ref.mantraRefId
                            mantraView.showMantra(ref.devanagariText, ref.displayLabel)
                        }

                        // Play/pause icon
                        btnPlayPause.text = if (s.isPlaying) "❚❚" else "▶"

                        // Seek + time
                        if (!userSeeking) {
                            seek.progress = (s.fraction * 1000).toInt()
                        }
                        tvPos.text = msStr(s.positionMs)
                        tvDur.text = msStr(s.durationMs)
                    }

                    // Exit listening mode if service signals it should stop
                    if (hasSeenActive && !s.isListeningMode) finish()
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun refVedaLabel(code: String) = when (code.trim().lowercase()) {
        "rigveda"     -> "ঋগ্বেদ"
        "yajurveda"   -> "যজুর্বেদ"
        "samaveda"    -> "সামবেদ"
        "atharvaveda" -> "অথর্ববেদ"
        else          -> code
    }

    private fun msStr(ms: Int): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(sec / 60, sec % 60)
    }

    private fun tv(text: String, sizeSp: Float, color: Int) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
}
