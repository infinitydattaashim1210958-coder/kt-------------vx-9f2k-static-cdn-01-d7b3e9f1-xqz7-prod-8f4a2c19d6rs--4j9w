package com.kyronix.swadhyaa.presentation.library

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.data.repository.LibraryBookInfo
import com.kyronix.swadhyaa.data.repository.LibraryBookStatus
import com.kyronix.swadhyaa.data.repository.LibraryBookWithStatus
import com.kyronix.swadhyaa.data.repository.LibraryRepository
import com.kyronix.swadhyaa.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * Digital Library catalog — the actual downloadable-books feature
 * (RISK_REGISTER.md R4), distinct from ShellActivity's "লাইব্রেরি" tab
 * (which is a Veda/Ramayana reading shortcut, unrelated — see the entry
 * point card added there).
 *
 * "html"-type books: tap downloads, then opens externally in the system
 * browser (matches legacy exactly — no in-app reader for these at all).
 * "db"-type books: tap downloads+merges into MasterDatabase, then opens
 * LibraryDbBookReaderActivity for the structured chapter/paragraph reader.
 */
class LibraryActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    private val viewModel: LibraryViewModel by lazy {
        ViewModelProvider(this, LibraryViewModel.Factory(applicationContext))[LibraryViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bg)
        }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)

        observeState()
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(AppColors.ivory)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(8))
    }

    private fun subtitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(AppColors.gold)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, 0, 0, dp(16))
    }

    private fun card(block: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AppColors.surface)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        block()
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                content.removeAllViews()
                content.addView(title("ডিজিটাল লাইব্রেরি"))
                content.addView(subtitle("ডাউনলোডযোগ্য বই ও গ্রন্থ"))
                when (state) {
                    is LibraryUiState.Loading -> content.addView(TextView(this@LibraryActivity).apply {
                        text = "লোড হচ্ছে…"
                        setTextColor(AppColors.muted)
                    })
                    is LibraryUiState.Error -> content.addView(TextView(this@LibraryActivity).apply {
                        text = state.message
                        setTextColor(AppColors.vermilion)
                    })
                    is LibraryUiState.Success -> renderBooks(state.books)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.downloadProgress.collect { progress ->
                // Re-render only the progress text on cards currently
                // showing one — cheapest correct approach given this
                // screen already fully re-renders on uiState changes;
                // full re-render here too keeps one code path, avoiding a
                // second, subtly-different partial-update path to maintain.
                val current = viewModel.uiState.value
                if (current is LibraryUiState.Success) renderBooks(current.books, progress)
            }
        }
        lifecycleScope.launch {
            viewModel.actionError.collect { message ->
                if (message != null) {
                    Toast.makeText(this@LibraryActivity, message, Toast.LENGTH_LONG).show()
                    viewModel.clearActionError()
                }
            }
        }
    }

    private fun renderBooks(books: List<LibraryBookWithStatus>, progress: Map<String, String> = emptyMap()) {
        // Remove only the book cards (index 2+ — title/subtitle stay), so
        // this can be called from the progress-observer without fighting
        // the uiState-observer's own removeAllViews().
        while (content.childCount > 2) content.removeViewAt(2)

        if (books.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "কোনো বই পাওয়া যায়নি।"
                setTextColor(AppColors.muted)
            })
            return
        }

        books.forEach { entry ->
            val inProgress = progress[entry.info.id]
            content.addView(card {
                addView(TextView(this@LibraryActivity).apply {
                    text = entry.info.title
                    setTextColor(AppColors.ivory)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                })
                if (entry.info.date.isNotBlank()) {
                    addView(TextView(this@LibraryActivity).apply {
                        text = entry.info.date
                        setTextColor(AppColors.muted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    })
                }
                addView(TextView(this@LibraryActivity).apply {
                    text = when {
                        inProgress != null -> inProgress
                        entry.status == LibraryBookStatus.DOWNLOADED -> "✅ ডাউনলোড করা আছে — পড়তে চাপুন"
                        else -> "ডাউনলোড করতে চাপুন"
                    }
                    setTextColor(if (entry.status == LibraryBookStatus.DOWNLOADED) AppColors.gold else AppColors.saffron)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, dp(6), 0, 0)
                })
                setOnClickListener {
                    if (inProgress != null) return@setOnClickListener
                    if (entry.status == LibraryBookStatus.DOWNLOADED) {
                        openBook(entry.info)
                    } else {
                        viewModel.download(entry.info)
                    }
                }
                if (entry.status == LibraryBookStatus.DOWNLOADED) {
                    setOnLongClickListener {
                        viewModel.delete(entry.info)
                        true
                    }
                }
            })
        }
    }

    private fun openBook(book: LibraryBookInfo) {
        if (book.type == "db") {
            startActivity(
                Intent(this, LibraryDbBookReaderActivity::class.java)
                    .putExtra(LibraryDbBookReaderActivity.EXTRA_BOOK_ID, book.id)
                    .putExtra(LibraryDbBookReaderActivity.EXTRA_BOOK_TITLE, book.title)
            )
        } else {
            lifecycleScope.launch {
                val uri = LibraryRepository.getHtmlShareableUri(applicationContext, book)
                if (uri == null) {
                    Toast.makeText(this@LibraryActivity, "ফাইল পাওয়া যায়নি — আবার ডাউনলোড করুন।", Toast.LENGTH_LONG).show()
                    return@launch
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(this@LibraryActivity, "এই ফাইল খোলার মতো কোনো অ্যাপ পাওয়া যায়নি।", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
