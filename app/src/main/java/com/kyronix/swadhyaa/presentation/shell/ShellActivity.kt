package com.kyronix.swadhyaa.presentation.shell

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.RamayanaCoreDatabase
import com.kyronix.swadhyaa.data.prefs.UserPrefs
import com.kyronix.swadhyaa.data.repository.SearchRepository
import com.kyronix.swadhyaa.data.repository.VedaRepository
import com.kyronix.swadhyaa.presentation.reader.ReaderActivity
import com.kyronix.swadhyaa.ui.theme.AppColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App shell: Home · Library · Bookmarks · Search · Settings
 * Implements M8 A–D foundation on one activity (phone-friendly).
 */
class ShellActivity : AppCompatActivity() {

    private enum class Tab { HOME, LIBRARY, BOOKMARKS, SEARCH, SETTINGS }

    private lateinit var content: LinearLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var prefs: UserPrefs
    private lateinit var vedaRepo: VedaRepository
    private lateinit var searchRepo: SearchRepository

    private var current = Tab.HOME
    private var searchJob: Job? = null
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = UserPrefs(this)
        val core = CoreDatabase.getInstance(this)
        val ram = RamayanaCoreDatabase.getInstance(this)
        vedaRepo = VedaRepository(core)
        searchRepo = SearchRepository(core, ram)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bg)
        }
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
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(AppColors.surface)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(scroll)
        root.addView(tabBar)
        setContentView(root)

        buildTabs()
        show(Tab.HOME)
    }

    override fun onResume() {
        super.onResume()
        if (current == Tab.HOME || current == Tab.BOOKMARKS) show(current)
    }

    private fun buildTabs() {
        tabBar.removeAllViews()
        listOf(
            Tab.HOME to "হোম",
            Tab.LIBRARY to "লাইব্রেরি",
            Tab.BOOKMARKS to "বুকমার্ক",
            Tab.SEARCH to "খুঁজুন",
            Tab.SETTINGS to "সেটিংস"
        ).forEach { (tab, label) ->
            val t = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setTextColor(if (tab == current) AppColors.saffron else AppColors.muted)
                setOnClickListener { show(tab) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            tabBar.addView(t)
        }
    }

    private fun show(tab: Tab) {
        current = tab
        buildTabs()
        content.removeAllViews()
        when (tab) {
            Tab.HOME -> renderHome()
            Tab.LIBRARY -> renderLibrary()
            Tab.BOOKMARKS -> renderBookmarks()
            Tab.SEARCH -> renderSearch()
            Tab.SETTINGS -> renderSettings()
        }
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

    private fun card(block: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.surface)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            layoutParams = lp
            block()
        }
    }

    private fun renderHome() {
        content.addView(title("স্বাধ্যায়"))
        content.addView(subtitle("সনাতন ধর্মশাস্ত্র"))

        lifecycleScope.launch {
            // Continue reading
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
                val vedas = vedaRepo.getVedaSummaries()
                val total = vedaRepo.getTotalMantraCount()
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
                // Ramayana entry
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
                    // If RamayanaActivity exists on device build, user can wire intent;
                    // placeholder keeps shell complete.
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
            vedaRepo.getVedaSummaries().forEach { v ->
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
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                results.removeAllViews()
                if (q.trim().length < 2) return@launch
                results.addView(TextView(this@ShellActivity).apply {
                    text = "Searching…"
                    setTextColor(AppColors.muted)
                })
                try {
                    val hits = searchRepo.search(q.trim())
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
