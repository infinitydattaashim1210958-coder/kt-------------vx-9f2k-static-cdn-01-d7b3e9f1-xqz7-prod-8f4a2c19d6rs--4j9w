package com.kyronix.swadhyaa.presentation.library

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.data.repository.LibraryHtmlBookRepository
import com.kyronix.swadhyaa.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * In-app reader for "html"-type library books.
 *
 * These are downloaded as real, self-contained HTML files (with their own
 * CSS/JS) — see LibraryHtmlBookRepository's doc. Previously opened via
 * ACTION_VIEW, which handed off to Android's "Open with" chooser (Chrome,
 * Docs, a bare HTML viewer, etc.) instead of staying inside স্বাধ্যায়.
 * This loads the same content:// Uri (from the app's existing FileProvider
 * setup — see getShareableUri) directly into an in-app WebView instead, so
 * reading never leaves the app. The page's own styling/interactivity is
 * left untouched — no font override here, since these pages carry their
 * own typography.
 */
class LibraryHtmlBookReaderActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: run { finish(); return }
        val bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: bookId

        setContentView(buildUi(bookTitle))

        lifecycleScope.launch {
            val entry = LibraryHtmlBookRepository.getDownloadedManifest(this@LibraryHtmlBookReaderActivity)[bookId]
            if (entry == null) {
                Toast.makeText(this@LibraryHtmlBookReaderActivity, "ফাইল খোলা যাচ্ছে না", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            val uri = LibraryHtmlBookRepository.getShareableUri(this@LibraryHtmlBookReaderActivity, entry.filename)
            webView.loadUrl(uri.toString())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUi(bookTitle: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        header.addView(TextView(this).apply {
            text = "←"
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dp(4), dp(4), dp(16), dp(4))
            setOnClickListener { handleBack() }
        })
        header.addView(TextView(this).apply {
            text = bookTitle
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
        })
        root.addView(header)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
        }
        root.addView(progressBar)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowContentAccess = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.visibility = View.GONE
                }
                // Book pages may link elsewhere (footnotes, cross-refs) —
                // keep those in-app too rather than falling back to
                // ACTION_VIEW for a second hop out of the app.
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
            }
        }
        root.addView(webView)

        return root
    }

    private fun handleBack() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        handleBack()
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
    }
}
