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

class LibraryDbBookReaderActivity : AppCompatActivity() {

    private lateinit var rootFrame:  FrameLayout
    private lateinit var scrollView: ScrollView
    private lateinit var content:    LinearLayout

    private lateinit var bookId:    String
    private lateinit var bookTitle: String
    private var activePopup: PopupWindow? = null
    private var baseFontSp: Float = 16f

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int)   = (v * density).toInt()
    private fun dp(v: Float) = (v * density)
    private lateinit var banglaTypeface: Typeface

    private val viewModel: LibraryDbBookReaderViewModel by lazy {
        ViewModelProvider(
            this,
            LibraryDbBookReaderViewModel.Factory(applicationContext, bookId, bookTitle)
        )[LibraryDbBookReaderViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookId    = intent.getStringExtra(EXTRA_BOOK_ID) ?: run { finish(); return }
        bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: bookId
        title     = bookTitle

        val settings = runBlocking {
            SettingsRepository(this@LibraryDbBookReaderActivity).settingsFlow.first()
        }
        banglaTypeface = FontManager.banglaTypeface(this, settings.banglaFont)
        baseFontSp     = settings.fontSize.toFloat().coerceIn(14f, 30f)

        buildLayout()
        observeState()
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private fun buildLayout() {
        rootFrame = FrameLayout(this)
        rootFrame.addView(CosmicBackgroundView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(40))
        }
        scrollView = ScrollView(this).apply { addView(content) }
        rootFrame.addView(scrollView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        rootFrame.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) dismissPopup()
            false
        }
        setContentView(rootFrame)
    }

    // ── State ─────────────────────────────────────────────────────────────────

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

    // ── Chapter render ────────────────────────────────────────────────────────

    private fun renderChapter(state: DbBookUiState.Success) {
        val chapters  = state.chapters
        val chapterId = state.selectedChapterId
        val chapter   = chapters.first { it.chapterId == chapterId }
        val idx       = chapters.indexOfFirst { it.chapterId == chapterId }
        val prev      = chapters.getOrNull(idx - 1)
        val next      = chapters.getOrNull(idx + 1)

        val refNoteByNum = state.paragraphs
            .flatMap { p -> p.refs.filter { !it.refNumber.isNullOrEmpty() && !it.refNote.isNullOrEmpty() } }
            .associate { it.refNumber!! to it.refNote!! }

        content.addView(buildNavRow(prev, next, chapters))
        content.addView(divider())
        content.addView(buildPicker(chapters, chapter))

        if (!chapter.isCover && !chapter.heading.isNullOrBlank())
            content.addView(buildHeadingView(chapter))
        content.addView(spacer(dp(8)))

        val chapterHeadingText = chapter.heading?.trim() ?: ""

        if (chapterId == "contents") {
            renderContentsChapter(state.paragraphs, chapters)
        } else {
            state.paragraphs.forEach { p ->
                val pText = p.content?.trim() ?: ""
                if (p.isBold && p.isCenter && chapterHeadingText.isNotBlank() &&
                    (pText == chapterHeadingText ||
                     chapterHeadingText.contains(pText) ||
                     pText.contains(chapterHeadingText))
                ) return@forEach

                val saved = state.selectionsByParaSeq[p.seq ?: -1] ?: emptyList()
                content.addView(buildParagraphView(p, refNoteByNum, saved, chapterId))
            }
        }

        content.addView(divider())
        content.addView(buildNavRow(prev, next, chapters))
    }

    // ── Contents chapter: tappable TOC ───────────────────────────────────────

    private fun renderContentsChapter(
        paragraphs: List<LibraryParagraph>,
        chapters: List<LibraryChapter>
    ) {
        paragraphs.forEach { p ->
            val raw = p.content ?: return@forEach
            val nullIdx     = raw.indexOf('\u0000')
            val displayText = if (nullIdx >= 0) raw.substring(0, nullIdx) else raw
            val targetChId  = if (nullIdx >= 0) raw.substring(nullIdx + 1) else null
            val isLinked    = targetChId != null
            val isHeading   = p.isBold

            val tv = TextView(this).apply {
                text = displayText
                typeface = if (isHeading) Typeface.create(banglaTypeface, Typeface.BOLD)
                            else banglaTypeface
                setTextSize(TypedValue.COMPLEX_UNIT_SP,
                    if (isHeading) (baseFontSp + 2f).coerceAtMost(24f) else baseFontSp)
                gravity   = if (isHeading) Gravity.CENTER else Gravity.START
                setPadding(if (isHeading) 0 else dp(8), 0, 0, 0)
                if (isLinked) {
                    setTextColor(AppColors.saffron)
                    paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    setOnClickListener {
                        if (chapters.any { it.chapterId == targetChId }) {
                            viewModel.selectChapter(targetChId!!)
                            scrollView.smoothScrollTo(0, 0)
                        }
                    }
                } else {
                    setTextColor(if (isHeading) AppColors.ivory else AppColors.muted)
                }
            }
            content.addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = if (isHeading) dp(6) else dp(14) })
        }
    }

    // ── Nav row ───────────────────────────────────────────────────────────────

    private fun buildNavRow(
        prev: LibraryChapter?, next: LibraryChapter?,
        allChapters: List<LibraryChapter>
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }
        }
        row.addView(navButton(
            label   = if (prev != null) "◀ ${abbrev(prev.heading ?: prev.chapterId)}" else "◀ শুরু",
            enabled = prev != null
        ) { viewModel.selectChapter(prev!!.chapterId); scrollView.smoothScrollTo(0, 0) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(this).apply {
            text = "• • •"; setTextColor(AppColors.muted); typeface = banglaTypeface
            gravity = Gravity.CENTER; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnClickListener { showChapterPicker(allChapters) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f))

        row.addView(navButton(
            label   = if (next != null) "${abbrev(next.heading ?: next.chapterId)} ▶" else "শেষ ▶",
            enabled = next != null
        ) { viewModel.selectChapter(next!!.chapterId); scrollView.smoothScrollTo(0, 0) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { gravity = Gravity.END })
        return row
    }

    private fun navButton(label: String, enabled: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label; typeface = banglaTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(if (enabled) AppColors.saffron else AppColors.mutedDim)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            isEnabled = enabled; isClickable = enabled
            maxLines  = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(8f); setColor(Color.TRANSPARENT)
                setStroke(dp(1f).toInt(), if (enabled) AppColors.saffron else AppColors.mutedDim)
            }
            if (enabled) {
                GlowBox.applyTo(this,
                    GlowBox.chip(this@LibraryDbBookReaderActivity,
                        color = AppColors.saffron, cornerRadiusDp = 8f, filled = false),
                    haloDp = 5)
                setOnClickListener { onClick() }
            }
        }

    private fun abbrev(s: String): String = if (s.length <= 20) s else s.take(18) + "…"

    // ── Picker ────────────────────────────────────────────────────────────────

    private fun buildPicker(chapters: List<LibraryChapter>, current: LibraryChapter): TextView {
        val idx     = chapters.indexOf(current) + 1
        val heading = current.heading?.takeIf { it.isNotBlank() } ?: current.chapterId
        return TextView(this).apply {
            text = "📑 $heading  ($idx/${chapters.size})"
            setTextColor(AppColors.saffron); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = banglaTypeface; setPadding(dp(12), dp(8), dp(12), dp(8))
            GlowBox.applyTo(this,
                GlowBox.chip(this@LibraryDbBookReaderActivity,
                    color = AppColors.saffron, cornerRadiusDp = 10f, filled = false), haloDp = 6)
            setOnClickListener { showChapterPicker(chapters) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
    }

    private fun showChapterPicker(chapters: List<LibraryChapter>) {
        dismissPopup()
        val labels = chapters.map { it.heading?.takeIf { h -> h.isNotBlank() } ?: it.chapterId }
            .toTypedArray()
        AlertDialog.Builder(this).setTitle("অধ্যায় নির্বাচন করুন")
            .setItems(labels) { _, i ->
                viewModel.selectChapter(chapters[i].chapterId)
                scrollView.smoothScrollTo(0, 0)
            }.show()
    }

    // ── Chapter heading ───────────────────────────────────────────────────────

    private fun buildHeadingView(chapter: LibraryChapter): TextView =
        TextView(this).apply {
            text = chapter.heading; setTextColor(AppColors.ivory)
            val scale = (chapter.headingSize / 12.0).toFloat().coerceIn(1f, 2f)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (baseFontSp * scale).coerceIn(14f, 32f))
            typeface = if (chapter.headingBold) Typeface.create(banglaTypeface, Typeface.BOLD)
                       else banglaTypeface
            gravity = if (chapter.headingCenter) Gravity.CENTER else Gravity.START
            paint.isUnderlineText = chapter.headingUnderline
            setPadding(0, 0, 0, dp(14))
        }

    // ── Paragraph ─────────────────────────────────────────────────────────────

    private fun buildParagraphView(
        p: LibraryParagraph,
        refNoteByNum: Map<String, String>,
        savedSelections: List<LibraryBookSelectionEntity>,
        chapterId: String
    ): View {
        val raw = p.content ?: return spacer(dp(4))

        val insertResult  = FootnoteMarkerInserter.insert(raw, p.refs)
        val processedText = insertResult.text
        val markerSpans   = insertResult.markers

        val isBody      = !p.isCenter && !p.isRight && !p.isBold
        val displayText = if (isBody) "\u2003$processedText" else processedText
        val indentOff   = if (isBody) 1 else 0

        val spannable = SpannableStringBuilder(displayText)
        if (p.isBold)      spannable.setSpan(StyleSpan(Typeface.BOLD),  0, displayText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (p.isUnderline) spannable.setSpan(UnderlineSpan(), 0, displayText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Ref markers: amber superscript + ClickableSpan
        markerSpans.forEach { marker ->
            val s = marker.start + indentOff
            val e = marker.end   + indentOff
            if (s < 0 || e > displayText.length || s >= e) return@forEach
            val refNum = displayText.substring(s, e)
            spannable.setSpan(BackgroundColorSpan(0x33F5A623.toInt()), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(0xFFF5A623.toInt()), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(SuperscriptSpan(),         s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(RelativeSizeSpan(0.65f),   s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) {
                    val note = refNoteByNum[refNum]
                        ?: p.refs.find { it.refNumber == refNum }?.refNote ?: return
                    showRefPopup(widget, "[$refNum] $note")
                }
                override fun updateDrawState(ds: android.text.TextPaint) { /* suppress underline */ }
            }, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Restore saved highlight spans
        savedSelections.forEach { sel ->
            val s = sel.selStart + indentOff
            val e = sel.selEnd   + indentOff
            if (s >= 0 && e <= displayText.length && s < e)
                spannable.setSpan(BackgroundColorSpan(0x66C850FF.toInt()), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val resolvedSp = when {
            p.isBold && p.isCenter -> (baseFontSp * (p.fontSize / 12.0).toFloat().coerceIn(1f, 2f)).coerceIn(14f, 32f)
            else -> baseFontSp
        }

        val tv = SelectableBookmarkTextView(this).apply {
            text = spannable
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, resolvedSp)
            typeface = if (p.isBold) Typeface.create(banglaTypeface, Typeface.BOLD) else banglaTypeface
            gravity  = when {
                p.isCenter -> Gravity.CENTER
                p.isRight  -> Gravity.END
                else       -> Gravity.START
            }
            val paraSeq = p.seq ?: 0
            onHighlight = { start, end, selected ->
                viewModel.saveSelection(chapterId, paraSeq,
                    start - indentOff, end - indentOff, selected, "highlight")
            }
            onBookmark = { start, end, selected ->
                viewModel.saveSelection(chapterId, paraSeq,
                    start - indentOff, end - indentOff, selected, "bookmark")
                android.widget.Toast.makeText(
                    this@LibraryDbBookReaderActivity,
                    "বুকমার্ক সংরক্ষিত হয়েছে ✓", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            // ClickableSpan (ref markers) needs LinkMovementMethod;
            // SelectableBookmarkTextView uses ArrowKeyMovementMethod by default
            // which handles selection WITHOUT swallowing tap-away.
            // For ref markers we override movementMethod here only when refs exist.
            if (markerSpans.isNotEmpty()) {
                movementMethod = object : android.text.method.LinkMovementMethod() {
                    // Suppress link-following on long press (we want selection instead)
                    override fun onTouchEvent(
                        widget: android.widget.TextView,
                        buffer: Spannable,
                        event: MotionEvent
                    ): Boolean {
                        // On ACTION_UP with no selection change, try link click
                        return super.onTouchEvent(widget, buffer, event)
                    }
                }
            }
            highlightColor = 0x44C850FF.toInt()  // purple selection tint
        }

        tv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) }
        return tv
    }

    // ── Ref popup ─────────────────────────────────────────────────────────────

    private fun showRefPopup(anchor: View, text: String) {
        dismissPopup()
        val popW = (resources.displayMetrics.widthPixels * 0.88).toInt()
        val tv = TextView(this).apply {
            this.text = text; typeface = banglaTypeface
            setTextColor(0xFFE8D5B7.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setLineSpacing(dp(2f), 1f)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12f); setColor(0xF01A0D1F.toInt())
                setStroke(dp(1.5f).toInt(), 0xFFBB44FF.toInt())  // purple border
            }
            addView(tv)
        }
        val popup = PopupWindow(container, popW, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true; elevation = dp(8f)
            setOnDismissListener { activePopup = null }
        }
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        popup.showAsDropDown(anchor, (resources.displayMetrics.widthPixels - popW) / 2 - loc[0], dp(4))
        activePopup = popup
    }

    private fun dismissPopup() { activePopup?.dismiss(); activePopup = null }

    // ── Utility ───────────────────────────────────────────────────────────────

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            .apply { topMargin = dp(6); bottomMargin = dp(6) }
        setBackgroundColor(AppColors.mutedDim)
    }

    private fun spacer(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
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

    override fun onBackPressed() {
        if (activePopup?.isShowing == true) { dismissPopup(); return }
        super.onBackPressed()
    }

    companion object {
        const val EXTRA_BOOK_ID    = "extra_book_id"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
    }
}
