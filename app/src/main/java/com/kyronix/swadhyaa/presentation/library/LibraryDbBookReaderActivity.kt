package com.kyronix.swadhyaa.presentation.library

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.data.repository.LibraryChapter
import com.kyronix.swadhyaa.data.repository.LibraryParagraph
import com.kyronix.swadhyaa.ui.theme.AppColors
import com.kyronix.swadhyaa.ui.theme.FontManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Structured reader for "db"-type library books (chapters/paragraphs/
 * footnotes merged into MasterDatabase by LibraryDbBookRepository).
 * "html"-type books never reach this screen — they open externally.
 *
 * Scope note: legacy's chapter-bookmark button and click-to-scroll
 * footnote links are not implemented here (see FootnoteMarkerInserter's
 * doc) — this screen covers correct chapter/paragraph/style/footnote
 * rendering, which is the part that affects what the reader actually
 * reads.
 */
class LibraryDbBookReaderActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private lateinit var bookId: String
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    // BUGFIX (font not applying app-wide): this screen — the actual page
    // the user reads a downloaded book on — never consulted the user's
    // font settings at all. Resolved the same way ReaderActivity/
    // GitaActivity already do it for their own body text.
    private lateinit var banglaTypeface: Typeface

    private val viewModel: LibraryDbBookReaderViewModel by lazy {
        ViewModelProvider(
            this,
            LibraryDbBookReaderViewModel.Factory(applicationContext, bookId)
        )[LibraryDbBookReaderViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookId = intent.getStringExtra(EXTRA_BOOK_ID)
            ?: run { finish(); return }
        val bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: bookId
        title = bookTitle

        // Resolve the Bangla font from user settings before UI is built.
        val settings = runBlocking { SettingsRepository(this@LibraryDbBookReaderActivity).settingsFlow.first() }
        banglaTypeface = FontManager.banglaTypeface(this, settings.banglaFont)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bg)
        }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                content.removeAllViews()
                when (state) {
                    is DbBookUiState.Loading -> content.addView(TextView(this@LibraryDbBookReaderActivity).apply {
                        text = "লোড হচ্ছে…"
                        setTextColor(AppColors.muted)
                    })
                    is DbBookUiState.Error -> content.addView(TextView(this@LibraryDbBookReaderActivity).apply {
                        text = state.message
                        setTextColor(AppColors.vermilion)
                    })
                    is DbBookUiState.Success -> renderChapter(state)
                }
            }
        }
    }

    private fun renderChapter(state: DbBookUiState.Success) {
        val chapter = state.chapters.first { it.chapterId == state.selectedChapterId }

        // Chapter picker
        content.addView(TextView(this).apply {
            text = "📑 অধ্যায় (${state.chapters.indexOf(chapter) + 1}/${state.chapters.size}) — বদলাতে চাপুন"
            setTextColor(AppColors.saffron)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, dp(12))
            setOnClickListener { showChapterPicker(state.chapters) }
        })

        if (!chapter.isCover) {
            content.addView(TextView(this).apply {
                text = chapter.heading ?: ""
                setTextColor(AppColors.ivory)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, chapter.headingSize.toFloat().coerceIn(14f, 28f))
                typeface = if (chapter.headingBold) Typeface.create(banglaTypeface, Typeface.BOLD) else banglaTypeface
                gravity = when {
                    chapter.headingCenter -> Gravity.CENTER
                    else -> Gravity.START
                }
                paint.isUnderlineText = chapter.headingUnderline
                setPadding(0, 0, 0, dp(16))
            })
        }

        val allFootnotes = mutableListOf<FootnoteMarkerInserter.Footnote>()

        state.paragraphs.forEach { p ->
            val raw = p.content ?: ""
            val (text, markers, footnotes) = FootnoteMarkerInserter.insert(raw, p.refs)
            allFootnotes += footnotes

            val spannable = SpannableString(text)
            markers.forEach { (start, end) ->
                if (start in 0..text.length && end in start..text.length) {
                    spannable.setSpan(SuperscriptSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(RelativeSizeSpan(0.7f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (p.isBold) spannable.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (p.isUnderline) spannable.setSpan(UnderlineSpan(), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            content.addView(TextView(this).apply {
                this.text = spannable
                setTextColor(AppColors.ivory)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, p.fontSize.toFloat().coerceIn(12f, 22f))
                typeface = banglaTypeface
                gravity = when {
                    p.isCenter -> Gravity.CENTER
                    p.isRight -> Gravity.END
                    else -> Gravity.START
                }
                setPadding(0, 0, 0, dp(14))
            })
        }

        val placedFootnotes = allFootnotes.filter { it.placed || it.note.isNotBlank() }
        if (placedFootnotes.isNotEmpty()) {
            content.addView(TextView(this).apply {
                text = "টীকা"
                setTextColor(AppColors.gold)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(6))
            })
            placedFootnotes.forEach { fn ->
                content.addView(TextView(this).apply {
                    text = "${fn.num}. ${fn.note}"
                    setTextColor(AppColors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = banglaTypeface
                    setPadding(0, 0, 0, dp(4))
                })
            }
        }
    }

    private fun showChapterPicker(chapters: List<LibraryChapter>) {
        val labels = chapters.map { it.heading?.takeIf { h -> h.isNotBlank() } ?: it.chapterId }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("অধ্যায় নির্বাচন করুন")
            .setItems(labels) { _, index ->
                viewModel.selectChapter(chapters[index].chapterId)
            }
            .show()
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
    }
}
