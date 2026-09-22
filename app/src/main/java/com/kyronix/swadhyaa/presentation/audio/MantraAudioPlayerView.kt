package com.kyronix.swadhyaa.presentation.audio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Self-contained audio player widget that binds to [AudioPlayerViewModel].
 *
 * Layout (vertical):
 * ┌────────────────────────────────────────────────────┐
 * │  মন্ত্র উচ্চারণ                                  │
 * │  ▶   ──────●──────────  0:07 / 0:24   📖 Reading  │
 * │             [status / spinner row]                 │
 * └────────────────────────────────────────────────────┘
 *
 * Usage:
 * ```kotlin
 * val playerView = MantraAudioPlayerView(context)
 * container.addView(playerView)
 * playerView.bind(viewLifecycleOwner, audioPlayerViewModel)
 * // then call audioPlayerViewModel.loadMantra(...)
 * ```
 */
class MantraAudioPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    // ── Colours (matching app dark theme) ────────────────────────────────────
    private val cGold    = Color.parseColor("#C4A574")
    private val cIvory   = Color.parseColor("#F5E6C8")
    private val cMuted   = Color.parseColor("#A89070")
    private val cSurface = Color.parseColor("#1A1510")
    private val cTrack   = Color.parseColor("#3C3020")
    private val cError   = Color.parseColor("#E57373")

    // ── Dimension helpers ─────────────────────────────────────────────────────
    private val dm = context.resources.displayMetrics
    private fun dp(v: Number) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), dm).toInt()
    private fun sp(v: Number) = v.toFloat()   // setTextSize accepts SP directly

    // ── Child views ───────────────────────────────────────────────────────────
    private val vLabel:     TextView
    private val vPlayerRow: LinearLayout
    private val vPlayBtn:   TextView
    private val vSeek:      SeekBar
    private val vTime:      TextView
    private val vModeBtn:   LinearLayout
    private val vModeIcon:  TextView
    private val vModeLbl:   TextView
    private val vStatusRow: LinearLayout
    private val vSpinner:   ProgressBar
    private val vStatus:    TextView

    private var vm: AudioPlayerViewModel? = null
    private var isSeeking = false

    // ─────────────────────────────────────────────────────────────────────────
    init {
        orientation = VERTICAL
        setBackgroundColor(cSurface)
        setPadding(dp(14), dp(10), dp(14), dp(12))

        // ── "মন্ত্র উচ্চারণ" label ────────────────────────────────────────
        vLabel = tv("মন্ত্র উচ্চারণ", 10.5f, cMuted).also {
            it.setPadding(0, 0, 0, dp(7))
            addView(it)
        }

        // ── Player row ────────────────────────────────────────────────────────
        vPlayerRow = row(Gravity.CENTER_VERTICAL).also { addView(it, lp(MP, WC)) }

        // Play / Pause button
        vPlayBtn = tv("▶", 22f, cGold).apply {
            gravity = Gravity.CENTER
            setOnClickListener { vm?.togglePlayPause() }
            setPadding(0, 0, dp(4), 0)
        }
        vPlayerRow.addView(vPlayBtn, lp(dp(40), dp(40)))

        // Seek bar
        vSeek = SeekBar(context).apply {
            max = 1000
            thumbTintList              = ColorStateList.valueOf(cGold)
            progressTintList           = ColorStateList.valueOf(cGold)
            progressBackgroundTintList = ColorStateList.valueOf(cTrack)
            setOnSeekBarChangeListener(seekListener)
        }
        vPlayerRow.addView(vSeek, lp(0, WC, 1f).apply { marginStart = dp(6); marginEnd = dp(6) })

        // Time label
        vTime = tv("0:00", 10.5f, cMuted).apply {
            minWidth = dp(60); gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        vPlayerRow.addView(vTime, lp(dp(64), WC))

        // Divider
        vPlayerRow.addView(divider())

        // ── Mode toggle button ─────────────────────────────────────────────
        // Vertical: [icon] / [label]
        vModeBtn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), 0, dp(2))
            setOnClickListener {
                val svc = vm ?: return@setOnClickListener
                val nowListening = svc.state.value.isListeningMode
                svc.setListeningMode(!nowListening)
            }
        }
        vModeIcon = tv("📖", 18f, cMuted).apply { gravity = Gravity.CENTER }
        vModeLbl  = tv("Reading\nMode", 8.5f, cMuted).apply {
            gravity = Gravity.CENTER; maxLines = 2
        }
        vModeBtn.addView(vModeIcon, lp(WC, WC))
        vModeBtn.addView(vModeLbl,  lp(WC, WC))
        vPlayerRow.addView(vModeBtn, lp(dp(58), WC))

        // ── Status row (spinner + text for loading/error/unavailable) ─────────
        vStatusRow = row(Gravity.CENTER_VERTICAL).also {
            it.visibility = GONE
            it.setPadding(0, dp(6), 0, 0)
            addView(it, lp(MP, WC))
        }
        vSpinner = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            indeterminateTintList = ColorStateList.valueOf(cGold)
        }
        vStatusRow.addView(vSpinner, lp(dp(20), dp(20)).apply { marginEnd = dp(8) })
        vStatus = tv("", 11f, cMuted)
        vStatusRow.addView(vStatus, lp(0, WC, 1f))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attach this view to a ViewModel and start collecting state.
     * Call once after the view is created.
     */
    fun bind(owner: LifecycleOwner, viewModel: AudioPlayerViewModel) {
        vm = viewModel
        owner.lifecycleScope.launch {
            viewModel.state.collect { s -> render(s) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────────

    private fun render(s: MantraPlaybackState) {
        when (s) {
            // ── Idle ───────────────────────────────────────────────────────────
            MantraPlaybackState.Idle -> {
                vPlayerRow.visibility = GONE
                hideStatus()
            }

            // ── Loading (buffering stream) ─────────────────────────────────────
            MantraPlaybackState.Loading -> {
                vPlayerRow.visibility = GONE
                showStatus(spinner = true, text = "লোড হচ্ছে...", color = cMuted)
            }

            // ── Playing / Paused ──────────────────────────────────────────────
            is MantraPlaybackState.Playing,
            is MantraPlaybackState.Paused -> {
                vPlayerRow.visibility = VISIBLE
                hideStatus()

                val playing   = s is MantraPlaybackState.Playing
                val fraction  = s.fraction
                val posMs     = s.positionMs
                val durMs     = s.durationMs
                val listening = s.isListeningMode

                vPlayBtn.text = if (playing) "⏸" else "▶"
                vPlayBtn.setTextColor(cGold)

                if (!isSeeking) {
                    vSeek.progress = (fraction * 1000).toInt()
                    vTime.text     = "${msStr(posMs)} / ${msStr(durMs)}"
                }

                // Mode button state
                if (listening) {
                    vModeIcon.text = "🎧"
                    vModeLbl.text  = "Listening\nMode"
                    vModeIcon.setTextColor(cGold)
                    vModeLbl.setTextColor(cGold)
                } else {
                    vModeIcon.text = "📖"
                    vModeLbl.text  = "Reading\nMode"
                    vModeIcon.setTextColor(cMuted)
                    vModeLbl.setTextColor(cMuted)
                }
            }

            // ── Not available ─────────────────────────────────────────────────
            MantraPlaybackState.NotAvailable -> {
                vPlayerRow.visibility = GONE
                showStatus(spinner = false,
                    text  = "এই মন্ত্রের অডিও পাওয়া যায়নি",
                    color = cMuted)
            }

            // ── Error ──────────────────────────────────────────────────────────
            is MantraPlaybackState.Error -> {
                vPlayerRow.visibility = GONE
                showStatus(spinner = false,
                    text  = "⚠  ${s.message}",
                    color = cError)
            }
        }
    }

    private fun showStatus(spinner: Boolean, text: String, color: Int) {
        vStatusRow.visibility = VISIBLE
        vSpinner.visibility   = if (spinner) VISIBLE else GONE
        vStatus.text          = text
        vStatus.setTextColor(color)
    }

    private fun hideStatus() { vStatusRow.visibility = GONE }

    // ─────────────────────────────────────────────────────────────────────────
    // SeekBar listener
    // ─────────────────────────────────────────────────────────────────────────

    private val seekListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(sb: SeekBar) { isSeeking = true }
        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            // Live time update while dragging
            val dur = (vm?.state?.value?.durationMs ?: 0)
            val pos = (progress.toFloat() / 1000 * dur).toInt()
            vTime.text = "${msStr(pos)} / ${msStr(dur)}"
        }
        override fun onStopTrackingTouch(sb: SeekBar) {
            isSeeking = false
            vm?.seekTo(sb.progress.toFloat() / 1000f)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layout / view factories
    // ─────────────────────────────────────────────────────────────────────────

    private val MP = LayoutParams.MATCH_PARENT
    private val WC = LayoutParams.WRAP_CONTENT

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LayoutParams(w, h, weight)

    private fun tv(text: String, sizeSp: Float, color: Int) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun row(gravity: Int) = LinearLayout(context).apply {
        orientation  = HORIZONTAL
        this.gravity = gravity
    }

    private fun divider() = android.view.View(context).apply {
        setBackgroundColor(cTrack)
    }.also { lp(dp(1), dp(36)).also { p -> p.marginStart = dp(8); it.layoutParams = p } }

    private fun msStr(ms: Int): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
}
