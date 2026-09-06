package com.kyronix.swadhyaa.presentation.shell

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.DatabaseAssetManager
import com.kyronix.swadhyaa.data.local.RamayanaCoreDatabase
import com.kyronix.swadhyaa.data.prefs.UserPrefs
import com.kyronix.swadhyaa.data.repository.SearchRepository
import com.kyronix.swadhyaa.data.repository.VedaRepository
import com.kyronix.swadhyaa.presentation.agent.AgentActivity
import com.kyronix.swadhyaa.presentation.reader.ReaderActivity
import com.kyronix.swadhyaa.ui.theme.AppColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App shell: Home · Library · Bookmarks · Search · Settings
 *
 * FIX: CoreDatabase / RamayanaCoreDatabase must NOT be opened until
 * DatabaseAssetManager.ensureReady() has returned true. On a fresh install
 * the DBs are downloaded in SwadhyayApp's background scope; ShellActivity
 * used to call getInstance() immediately in onCreate(), which triggered the
 * require() guard inside build() → IllegalArgumentException → crash.
 *
 * Solution: show a loading screen, collect DatabaseAssetManager.progress
 * until state == COMPLETED (or FAILED), then initialise the repos and
 * render the real UI.
 */
class ShellActivity : AppCompatActivity() {

    private enum class Tab { HOME, LIBRARY, BOOKMARKS, SEARCH, SETTINGS }

    // These are null until the DB is confirmed ready
    private var vedaRepo: VedaRepository? = null
    private var searchRepo: SearchRepository? = null

    private lateinit var prefs: UserPrefs
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var tabBar: LinearLayout

    private var current = Tab.HOME
    private var searchJob: Job? = null
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = UserPrefs(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bg)
        }
        setContentView(root)

        // Show loading screen first, wait for DB, then build real UI
        showLoadingScreen()
        waitForDatabaseThenInit()
    }

    // ── Loading screen ────────────────────────────────────────────────

    private fun showLoadingScreen() {
        root.removeAllViews()

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "স্বাধ্যায়"
            textSize = 28f
            setTextColor(AppColors.gold)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "সনাতন ধর্মশাস্ত্র"
            textSize = 14f
            setTextColor(AppColors.muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(32))
        }
        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateDrawable?.setTint(AppColors.saffron)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        val statusText = TextView(this).apply {
            tag = "status"
            text = "ডেটাবেস প্রস্তুত হচ্ছে…"
            textSize = 13f
            setTextColor(AppColors.muted)
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(16), dp(32), 0)
        }

        wrapper.addView(title)
        wrapper.addView(subtitle)
        wrapper.addView(spinner)
        wrapper.addView(statusText)
        root.addView(wrapper)
    }

    private fun updateLoadingStatus(msg: String) {
        val tv = root.findViewWithTag<TextView>("status") ?: return
        tv.text = msg
    }

    // ── Wait for DB, then initialise ──────────────────────────────────

    private fun waitForDatabaseThenInit() {
        lifecycleScope.launch {
            // Collect progress until terminal state
            DatabaseAssetManager.progress.collect { progress ->
                when (progress.state) {
                    DatabaseAssetManager.State.IDLE,
                    DatabaseAssetManager.State.CHECKING -> {
                        updateLoadingStatus("ডেটাবেস পরীক্ষা করা হচ্ছে…")
                    }
                    DatabaseAssetManager.State.DOWNLOADING -> {
                        val pct = if (progress.totalBytes > 0)
                            " (${(progress.downloadedBytes * 100 / progress.totalBytes)}%)"
                        else ""
                        updateLoadingStatus("ডাউনলোড হচ্ছে${pct}…\n${progress.currentAsset ?: ""}")
                    }
                    DatabaseAssetManager.State.VERIFYING  -> updateLoadingStatus("যাচাই করা হচ্ছে…")
                    DatabaseAssetManager.State.EXTRACTING -> updateLoadingStatus("প্রক্রিয়া করা হচ্ছে…")
                    DatabaseAssetManager.State.INSTALLING -> updateLoadingStatus("ইনস্টল হচ্ছে…")

                    DatabaseAssetManager.State.COMPLETED -> {
                        // DB files are on disk — safe to open Room now
                        initReposAndBuildUi()
                        return@collect   // stop collecting
                    }

                    DatabaseAssetManager.State.FAILED -> {
                        showError(progress.error ?: "অজানা ত্রুটি")
                        return@collect
                    }
                }
            }
        }
    }

    private fun initReposAndBuildUi() {
        try {
            val core = CoreDatabase.getInstance(this)
            val ram  = RamayanaCoreDatabase.getInstance(this)
            vedaRepo   = VedaRepository(core)
            searchRepo = SearchRepository(core, ram)
            buildFullUi()
        } catch (e: Exception) {
            showError("DB খুলতে ব্যর্থ: ${e.message}")
        }
    }

    private fun showError(msg: String) {
        root.removeAllViews()
        val tv = TextView(this).apply {
            text = "⚠ ডেটাবেস লোড ব্যর্থ\n\n$msg\n\nঅ্যাপ পুনরায় চালু করুন।"
            setTextColor(AppColors.vermilion)
            textSize = 14f
            setPadding(dp(24), dp(48), dp(24), 0)
            gravity = Gravity.CENTER
        }
        root.addView(tv)
    }

    // ── Full UI (only called after DB is ready) ───────────────────────

    private fun buildFullUi() {
        root.removeAllViews()   // clear loading screen

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(content)

        // শাস্ত্র-সহায়ক button (above tab bar)
        val agentBtn = TextView(this).apply {
            text = "✦ শাস্ত্র-সহায়ক"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(AppColors.saffron)
            setBackgroundColor(AppColors.surface)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                startActivity(Intent(this@ShellActivity, AgentActivity::class.java))
            }
        }

        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(AppColors.surface)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        root.addView(scroll)
        root.addView(agentBtn)
        root.addView(tabBar)

        buildTabs()
        show(Tab.HOME)
    }

    override fun onResume() {
        super.onResume()
        // Only refresh if UI is already built (DB was ready)
        if (vedaRepo != null && (current == Tab.HOME || current == Tab.BOOKMARKS)) {
            show(current)
        }
    }

    // ── Tab helpers ───────────────────────────────────────────────────

    private fun buildTabs() {
        tabBar.removeAllViews()
        listOf(
            Tab.HOME      to "হোম",
            Tab.LIBRARY   to "লাইব্রেরি",
            Tab.BOOKMARKS to "বুকমার্ক",
            Tab.SEARCH    to "খুঁজুন",
            Tab.SETTINGS  to "সেটিংস"
        ).forEach { (tab, label) ->
            val t = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setTextColor(if (tab == current) AppColors.saffron else AppColors.muted)
                setOnClickListener { show(tab) }
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            tabBar.addView(t)
        }
    }

    private fun show(tab: Tab) {
        current = tab
        buildTabs()
        content.removeAllViews()
        when (tab) {
            Tab.HOME      -> renderHome()
            Tab.LIBRARY   -> renderLibrary()
            Tab.BOOKMARKS -> renderBookmarks()
            Tab.SEARCH    -> renderSearch()
            Tab.SETTINGS  -> renderSettings()
        }
    }

    // ── View helpers ──────────────────────────────────────────────────

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

    private fun card(block: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.surface)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            block()
        }
    }

    // ── Tab renderers ─────────────────────────────────────────────────

    private fun renderHome() {
        content.addView(title("স্বাধ্যায়"))
        content.addView(subtitle("সনাতন ধর্মশাস্ত্র"))

        lifecycleScope.launch {
            val cont = prefs.continueFlow.first()
            if (cont != null) {
                content.addView(card {
                    addView(TextView(this@ShellActivity).apply {
                        text = "Continue reading"
                        setTextColor(AppColors.muted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    })
                    addView(TextView(this@ShellActivity).apply {
                        text = cont.label
                        setTextColor(AppColors.ivory)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        setPadding(0, dp(4), 0, 0)
                    })
                    setOnClickListener {
                        if (cont.kind == "veda") {
                            startActivity(
                                Intent(this@ShellActivity, ReaderActivity::class.java)
                                    .putExtra(ReaderActivity.EXTRA_VEDA_ID, cont.corpusId)
                            )
                        }
                    }
                })
            }

            try {
                val repo = vedaRepo ?: return@launch
                val vedas = repo.getVedaSummaries()
                val total = repo.getTotalMantraCount()
                content.addView(TextView(this@ShellActivity).apply {
                    text = "Database OK — $total mantras"
                    setTextColor(AppColors.gold)
                    setPadding(0, 0, 0, dp(12))
                })
                vedas.forEach { v ->
                    content.addView(card {
                        addView(TextView(this@ShellActivity).apply {
                            text = v.name
                            setTextColor(AppColors.saffron)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                            typeface = Typeface.DEFAULT_BOLD
                        })
                        addView(TextView(this@ShellActivity).apply {
                            text = "${v.mantraCount} mantras · ${v.code}"
                            setTextColor(AppColors.muted)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        })
                        setOnClickListener {
                            startActivity(
                                Intent(this@ShellActivity, ReaderActivity::class.java)
                                    .putExtra(ReaderActivity.EXTRA_VEDA_ID, v.id)
                            )
                        }
                    })
                }
                content.addView(card {
                    addView(TextView(this@ShellActivity).apply {
                        text = "রামায়ণ"
                        setTextColor(AppColors.saffron)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    addView(TextView(this@ShellActivity).apply {
                        text = "৬ কাণ্ড · core offline"
                        setTextColor(AppColors.muted)
                    })
                })
            } catch (e: Exception) {
                content.addView(TextView(this@ShellActivity).apply {
                    text = "Error: ${e.message}"
                    setTextColor(AppColors.vermilion)
                })
            }
        }
    }

    private fun renderLibrary() {
        content.addView(title("লাইব্রেরি"))
        content.addView(subtitle("Vedas · Itihāsa"))
        lifecycleScope.launch {
            val repo = vedaRepo ?: return@launch
            repo.getVedaSummaries().forEach { v ->
                content.addView(card {
                    addView(TextView(this@ShellActivity).apply {
                        text = v.name
                        setTextColor(AppColors.ivory)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    })
                    addView(TextView(this@ShellActivity).apply {
                        text = "${v.mantraCount} mantras"
                        setTextColor(AppColors.muted)
                    })
                    setOnClickListener {
                        startActivity(
                            Intent(this@ShellActivity, ReaderActivity::class.java)
                                .putExtra(ReaderActivity.EXTRA_VEDA_ID, v.id)
                        )
                    }
                })
            }
            content.addView(card {
                addView(TextView(this@ShellActivity).apply {
                    text = "রামায়ণ (৬ কাণ্ড)"
                    setTextColor(AppColors.ivory)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                })
            })
        }
    }

    private fun renderBookmarks() {
        content.addView(title("বুকমার্ক"))
        content.addView(subtitle("Saved wisdom"))
        lifecycleScope.launch {
            val list = prefs.bookmarksFlow.first()
            if (list.isEmpty()) {
                content.addView(TextView(this@ShellActivity).apply {
                    text = "এখনো কিছু নেই।\nরিডারে বুকমার্ক চাপুন।"
                    setTextColor(AppColors.muted)
                    setPadding(0, dp(24), 0, 0)
                })
            } else {
                list.forEach { b ->
                    content.addView(card {
                        addView(TextView(this@ShellActivity).apply {
                            text = b.label
                            setTextColor(AppColors.saffron)
                        })
                        addView(TextView(this@ShellActivity).apply {
                            text = b.snippet
                            setTextColor(AppColors.ivory)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        })
                        setOnClickListener {
                            if (b.kind == "veda") {
                                startActivity(
                                    Intent(this@ShellActivity, ReaderActivity::class.java)
                                        .putExtra(ReaderActivity.EXTRA_VEDA_ID, b.corpusId)
                                )
                            }
                        }
                        setOnLongClickListener {
                            lifecycleScope.launch {
                                prefs.removeBookmark(b.kind, b.itemId)
                                show(Tab.BOOKMARKS)
                            }
                            true
                        }
                    })
                }
            }
        }
    }

    private fun renderSearch() {
        content.addView(title("খুঁজুন"))
        content.addView(subtitle("Offline · Veda + Rāmāyaṇa"))
        val input = EditText(this).apply {
            hint = "সন্ধান… (অগ্নি / तपः …)"
            setHintTextColor(AppColors.muted)
            setTextColor(AppColors.ivory)
            setBackgroundColor(AppColors.elevated)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(input)
        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(results)

        fun runSearch(q: String) {
            val repo = searchRepo ?: return
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                results.removeAllViews()
                if (q.trim().length < 2) return@launch
                results.addView(TextView(this@ShellActivity).apply {
                    text = "Searching…"
                    setTextColor(AppColors.muted)
                })
                try {
                    val hits = repo.search(q.trim())
                    results.removeAllViews()
                    if (hits.isEmpty()) {
                        results.addView(TextView(this@ShellActivity).apply {
                            text = "কিছু পাওয়া যায়নি"
                            setTextColor(AppColors.muted)
                        })
                    } else {
                        hits.forEach { h ->
                            results.addView(card {
                                addView(TextView(this@ShellActivity).apply {
                                    text = h.label
                                    setTextColor(AppColors.saffron)
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                })
                                addView(TextView(this@ShellActivity).apply {
                                    text = h.snippet
                                    setTextColor(AppColors.ivory)
                                })
                                setOnClickListener {
                                    if (h.kind == "veda") {
                                        startActivity(
                                            Intent(this@ShellActivity, ReaderActivity::class.java)
                                                .putExtra(ReaderActivity.EXTRA_VEDA_ID, h.corpusId)
                                        )
                                    }
                                }
                            })
                        }
                    }
                } catch (e: Exception) {
                    results.removeAllViews()
                    results.addView(TextView(this@ShellActivity).apply {
                        text = "Error: ${e.message}"
                        setTextColor(AppColors.vermilion)
                    })
                }
            }
        }

        input.setOnEditorActionListener { _, _, _ ->
            runSearch(input.text.toString())
            true
        }
    }

    private fun renderSettings() {
        content.addView(title("সেটিংস"))
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = "Offline-first"
                setTextColor(AppColors.ivory)
            })
            addView(TextView(this@ShellActivity).apply {
                text = "Core DB APK-এ bundled। Scholar packs আলাদা DB repo Release থেকে।"
                setTextColor(AppColors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        })
        content.addView(card {
            addView(TextView(this@ShellActivity).apply {
                text = "Design"
                setTextColor(AppColors.ivory)
            })
            addView(TextView(this@ShellActivity).apply {
                text = "Illuminated manuscript · saffron accent · content-first"
                setTextColor(AppColors.muted)
            })
        })
    }
}
