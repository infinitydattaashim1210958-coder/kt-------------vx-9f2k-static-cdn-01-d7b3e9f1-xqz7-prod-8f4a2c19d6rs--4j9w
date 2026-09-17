package com.kyronix.swadhyaa.presentation.library

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.data.local.entity.LibraryBookSelectionEntity
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.data.repository.LibraryChapter
import com.kyronix.swadhyaa.data.repository.LibraryParagraph
import com.kyronix.swadhyaa.ui.theme.AppColors
import com.kyronix.swadhyaa.ui.theme.CosmicBackgroundView
import com.kyronix.swadhyaa.ui.theme.FontManager
import com.kyronix.swadhyaa.ui.theme.GlowBox
import com.kyronix.swadhyaa.ui.theme.SelectableBookmarkTextView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Structured reader for "db"-type library books.
 *
 * Features:
 *  1. Animated cosmic background (stars, nebulae, shooting stars, aurora).
 *  2. Text selection → highlight / bookmark. Copy is blocked.
 *     Existing highlights are restored from Room on every chapter load.
 *  3. Prev / Next chapter navigation at both top and bottom of the page.
 *  4. In-book reference markers: amber superscript → tap → glowing float
 *     popup; touch anywhere to dismiss.
 *  5. Paragraph indent (em-space) + 1-line gap between paragraphs.
 *  6. Center alignment honoured from is_center DB flag.
 *  7. Bengali chapter names in picker from DB headings.
 */
class LibraryDbBookReaderActivity : AppCompatActivity() {

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var rootFrame:  FrameLayout
    private lateinit var scrollView: ScrollView
    private lateinit var content:    LinearLayout

    // ── State ────────────────────────────────────────────────────────────────
    private lateinit var bookId: String
    private var activePopup: PopupWindow? = null

    // ── Helpers ──────────────────────────────────────────────────────────────
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int)   = (v * density).toInt()
    private fun dp(v: Float) = (v * density)
    private lateinit var banglaTypeface: Typeface

    private val viewModel: LibraryDbBookReaderViewModel by lazy {
        ViewModelProvider(
            this,
            LibraryDbBookReaderViewModel.Factory(applicationContext, bookId)
        )[LibraryDbBookReaderViewModel::class.java]
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: run { finish(); return }
        title  = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: bookId

        val settings = runBlocking { SettingsRepository(this@LibraryDbBookReaderActivity).settingsFlow.first() }
        banglaTypeface = FontManager.banglaTypeface(this, settings.banglaFont)

        buildLayout()
        observeState()
    }

    private fun buildLayout() {
        // ── Root FrameLayout — cosmic bg + scroll overlay ────────────────────
        rootFrame = FrameLayout(this)

        // Layer 1: animated cosmic background
        val cosmic = CosmicBackgroundView(this)
        rootFrame.addView(cosmic, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Layer 2: scroll + content
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(40))
        }
        scrollView = ScrollView(this).apply { addView(content) }
        rootFrame.addView(scrollView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Touch on root frame (outside text) dismisses any open ref popup
        rootFrame.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) dismissPopup()
            false
        }

        setContentView(rootFrame)
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                content.removeAllViews()
                when (state) {
                    is DbBookUiState.Loading -> content.addView(loadingView())
                    is DbBookUiState.Error   -> content.addView(errorView(state.message))
                    is DbBookUiState.Success -> renderChapter(state)
                }
            }
        }
    }

    // ── Chapter rendering ────────────────────────────────────────────────────

    private fun renderChapter(state: DbBookUiState.Success) {
        val chapters   = state.chapters
        val chapterId  = state.selectedChapterId
        val chapter    = chapters.first { it.chapterId == chapterId }
        val idx        = chapters.indexOfFirst { it.chapterId == chapterId }
        val prevChapter = chapters.getOrNull(idx - 1)
        val nextChapter = chapters.getOrNull(idx + 1)

        // Pre-build ref note lookup for this chapter
        val refNoteByNum = state.paragraphs
            .flatMap { p -> p.refs.filter { !it.refNumber.isNullOrEmpty() && !it.refNote.isNullOrEmpty() } }
            .associate { it.refNumber!! to it.refNote!! }

        // ── TOP NAVIGATION ────────────────────────────────────────────────────
        content.addView(buildNavRow(prevChapter, nextChapter, chapters))
        content.addView(divider())

        // ── PICKER ────────────────────────────────────────────────────────────
        content.addView(buildPicker(chapters, chapter))

        // ── CHAPTER HEADING ───────────────────────────────────────────────────
        if (!chapter.isCover && !chapter.heading.isNullOrBlank()) {
            content.addView(buildHeadingView(chapter))
        }

        content.addView(spacer(dp(8)))

        // ── PARAGRAPHS ────────────────────────────────────────────────────────
        state.paragraphs.forEach { p ->
            val savedSelections = state.selectionsByParaSeq[p.seq ?: -1] ?: emptyList()
            content.addView(buildParagraphView(p, refNoteByNum, savedSelections, chapterId))
        }

        // ── BOTTOM NAVIGATION ─────────────────────────────────────────────────
        content.addView(divider())
        content.addView(buildNavRow(prevChapter, nextChapter, chapters))
    }

    // ── Navigation row (prev / next) ─────────────────────────────────────────

    private fun buildNavRow(
        prev: LibraryChapter?,
        next: LibraryChapter?,
        allChapters: List<LibraryChapter>
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8); lp.bottomMargin = dp(8)
            layoutParams = lp
        }

        // PREV button
        val prevBtn = navButton(
            label = if (prev != null) "◀ ${abbrev(prev.heading ?: prev.chapterId)}" else "◀ শুরু",
            enabled = prev != null
        ) {
            if (prev != null) {
                viewModel.selectChapter(prev.chapterId)
                scrollView.smoothScrollTo(0, 0)
            }
        }

        // Chapter picker label in centre
        val pickerTv = TextView(this).apply {
            text = "${allChapters.indexOfFirst { it.chapterId == (next?.let { allChapters.getOrNull(allChapters.indexOf(next) - 1) }?.chapterId ?: "") } + 1}"
            // Just show "•••" as a tap-for-picker hint
            text = "• • •"
            setTextColor(AppColors.muted)
            typeface = banglaTypeface
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener { showChapterPicker(allChapters) }
        }

        // NEXT button
        val nextBtn = navButton(
            label = if (next != null) "${abbrev(next.heading ?: next.chapterId)} ▶" else "শেষ ▶",
            enabled = next != null
        ) {
            if (next != null) {
                viewModel.selectChapter(next.chapterId)
                scrollView.smoothScrollTo(0, 0)
            }
        }

        val wrapPrev = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val wrapMid  = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)
        val wrapNext = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        wrapNext.gravity = Gravity.END

        row.addView(prevBtn, wrapPrev)
        row.addView(pickerTv, wrapMid)
        row.addView(nextBtn, wrapNext)
        return row
    }

    private fun navButton(label: String, enabled: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            typeface = banglaTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(if (enabled) AppColors.saffron else AppColors.mutedDim)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            isEnabled = enabled
            isClickable = enabled
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (enabled) {
                GlowBox.applyTo(
                    this,
                    GlowBox.chip(this@LibraryDbBookReaderActivity,
                        color = AppColors.saffron, cornerRadiusDp = 8f, filled = false),
                    haloDp = 5
                )
                setOnClickListener { onClick() }
            } else {
                // subtle muted border for disabled state
                val gd = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(8f)
                    setStroke(dp(1f).toInt(), AppColors.mutedDim)
                    setColor(Color.TRANSPARENT)
                }
                background = gd
            }
        }
    }

    /** Shorten a chapter heading to ≤22 chars for the nav button label. */
    private fun abbrev(s: String): String =
        if (s.length <= 22) s else s.take(20) + "…"

    // ── Picker ───────────────────────────────────────────────────────────────

    private fun buildPicker(chapters: List<LibraryChapter>, current: LibraryChapter): TextView {
        val idx = chapters.indexOf(current) + 1
        val heading = current.heading?.takeIf { it.isNotBlank() } ?: current.chapterId
        return TextView(this).apply {
            text = "📑 $heading  ($idx/${chapters.size})"
            setTextColor(AppColors.saffron)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = banglaTypeface
            setPadding(dp(12), dp(8), dp(12), dp(8))
            GlowBox.applyTo(
                this,
                GlowBox.chip(this@LibraryDbBookReaderActivity,
                    color = AppColors.saffron, cornerRadiusDp = 10f, filled = false),
                haloDp = 6
            )
            setOnClickListener { showChapterPicker(chapters) }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(16)
            layoutParams = lp
        }
    }

    private fun showChapterPicker(chapters: List<LibraryChapter>) {
        dismissPopup()
        val labels = chapters.map { it.heading?.takeIf { h -> h.isNotBlank() } ?: it.chapterId }
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("অধ্যায় নির্বাচন করুন")
            .setItems(labels) { _, i ->
                viewModel.selectChapter(chapters[i].chapterId)
                scrollView.smoothScrollTo(0, 0)
            }
            .show()
    }

    // ── Chapter heading ───────────────────────────────────────────────────────

    private fun buildHeadingView(chapter: LibraryChapter): TextView {
        return TextView(this).apply {
            text = chapter.heading
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP,
                chapter.headingSize.toFloat().coerceIn(13f, 28f))
            typeface = if (chapter.headingBold)
                Typeface.create(banglaTypeface, Typeface.BOLD) else banglaTypeface
            gravity = if (chapter.headingCenter) Gravity.CENTER else Gravity.START
            paint.isUnderlineText = chapter.headingUnderline
            setPadding(0, 0, 0, dp(14))
        }
    }

    // ── Paragraph rendering ───────────────────────────────────────────────────

    private fun buildParagraphView(
        p: LibraryParagraph,
        refNoteByNum: Map<String, String>,
        savedSelections: List<LibraryBookSelectionEntity>,
        chapterId: String
    ): View {
        val raw = p.content ?: return spacer(dp(4))

        // Build spannable with ref markers highlighted
        val insertResult = FootnoteMarkerInserter.insert(raw, p.refs)
        val processedText = insertResult.text
        val markerSpans   = insertResult.markers   // List<MarkerSpan>

        // Paragraph indent for body text
        val isBody = !p.isCenter && !p.isRight && !p.isBold
        val displayText = if (isBody) "\u2003$processedText" else processedText
        val indentOffset = if (isBody) 1 else 0

        val spannable = SpannableStringBuilder(displayText)

        if (p.isBold)      spannable.setSpan(StyleSpan(Typeface.BOLD), 0, displayText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (p.isUnderline) spannable.setSpan(UnderlineSpan(), 0, displayText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Ref markers: amber superscript + tap to show float popup
        markerSpans.forEach { marker ->
            val s = marker.start + indentOffset
            val e = marker.end   + indentOffset
            if (s < 0 || e > displayText.length || s >= e) return@forEach
            val refNum = displayText.substring(s, e)
            spannable.setSpan(BackgroundColorSpan(0x33F5A623.toInt()), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(0xFFF5A623.toInt()), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(SuperscriptSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(RelativeSizeSpan(0.65f), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            // Store refNum in the span range for click detection
            spannable.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) {
                    val note = refNoteByNum[refNum]
                        ?: p.refs.find { it.refNumber == refNum }?.refNote ?: return
                    showRefPopup(widget, "[$refNum] $note")
                }
                override fun updateDrawState(ds: android.text.TextPaint) { /* no underline */ }
            }, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Restore saved highlight spans (offset by indent)
        savedSelections.forEach { sel ->
            val s = sel.selStart + indentOffset
            val e = sel.selEnd   + indentOffset
            if (s >= 0 && e <= displayText.length && s < e) {
                spannable.setSpan(
                    BackgroundColorSpan(0x55F5A623.toInt()), s, e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val tv = SelectableBookmarkTextView(this).apply {
            text = spannable
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, p.fontSize.toFloat().coerceIn(11f, 24f))
            typeface = when {
                p.isBold -> Typeface.create(banglaTypeface, Typeface.BOLD)
                else     -> banglaTypeface
            }
            gravity = when {
                p.isCenter -> Gravity.CENTER
                p.isRight  -> Gravity.END
                else       -> Gravity.START
            }
            // Wire up bookmark/highlight callbacks
            val paraSeq = p.seq ?: 0
            onHighlight = { start, end, selected ->
                viewModel.saveSelection(chapterId, paraSeq,
                    start - indentOffset, end - indentOffset, selected, "highlight")
            }
            onBookmark = { start, end, selected ->
                viewModel.saveSelection(chapterId, paraSeq,
                    start - indentOffset, end - indentOffset, selected, "bookmark")
                // Visual confirmation toast
                android.widget.Toast.makeText(
                    this@LibraryDbBookReaderActivity,
                    "বুকমার্ক সংরক্ষিত হয়েছে", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            // Make ClickableSpans (ref markers) work inside selectable text
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) }  // 1 blank line gap between paragraphs
        tv.layoutParams = lp
        return tv
    }

    // ── Reference float popup ─────────────────────────────────────────────────

    private fun showRefPopup(anchor: View, text: String) {
        dismissPopup()
        val popupWidth = (resources.displayMetrics.widthPixels * 0.88).toInt()

        val tv = TextView(this).apply {
            this.text = text
            typeface  = banglaTypeface
            setTextColor(0xFFE8D5B7.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setLineSpacing(dp(2f), 1f)
        }

        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(12f)
            setColor(0xF0231A0F.toInt())
            setStroke(dp(1.5f).toInt(), 0xFFF5A623.toInt())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = bg
            addView(tv)
        }

        val popup = PopupWindow(container, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.elevation = dp(8f)
        popup.setOnDismissListener { activePopup = null }

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val xOff = (resources.displayMetrics.widthPixels - popupWidth) / 2 - loc[0]
        popup.showAsDropDown(anchor, xOff, dp(4))
        activePopup = popup
    }

    private fun dismissPopup() { activePopup?.dismiss(); activePopup = null }

    // ── Utility views ─────────────────────────────────────────────────────────

    private fun divider(): View = View(this).apply {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        lp.topMargin = dp(6); lp.bottomMargin = dp(6)
        layoutParams = lp
        background = GlowBox.glowLine(AppColors.mutedDim)
    }

    private fun spacer(heightPx: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
    }

    private fun loadingView() = TextView(this).apply {
        text = "লোড হচ্ছে…"; setTextColor(AppColors.muted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = banglaTypeface
        setPadding(0, dp(24), 0, 0)
    }

    private fun errorView(msg: String) = TextView(this).apply {
        text = msg; setTextColor(AppColors.vermilion)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = banglaTypeface
        setPadding(0, dp(24), 0, 0)
    }

    // ── Back button ───────────────────────────────────────────────────────────

    override fun onBackPressed() {
        if (activePopup?.isShowing == true) { dismissPopup(); return }
        super.onBackPressed()
    }

    companion object {
        const val EXTRA_BOOK_ID    = "extra_book_id"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
    }
}
