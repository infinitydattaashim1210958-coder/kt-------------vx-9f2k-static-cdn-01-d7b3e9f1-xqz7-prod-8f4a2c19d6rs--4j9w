package com.kyronix.swadhyaa.presentation.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import com.kyronix.swadhyaa.ui.theme.AppColors
import com.kyronix.swadhyaa.ui.theme.FontManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * রবীন্দ্র রচনাবলী — cascading picker-box reader.
 *
 * Reads the downloaded `rabindra.db` (schema "rabindra_reader_v1") directly.
 * Four picker boxes at the top; every page of the corpus is reachable from
 * the front page:
 *
 *   [বিভাগ] → [গ্রন্থ/রচনা] → [অধ্যায়/অংশ] (only if the work has sections) → [পৃষ্ঠা]
 *
 * Prev/Next walk the work's pages in reading order and keep all boxes in sync.
 */
class RabindraPickerActivity : AppCompatActivity() {

    private var db: SQLiteDatabase? = null

    // ── selection state ───────────────────────────────────────────────
    private var catId = -1L
    private var workId = -1L
    private var sectionId = -1L          // -1 = no section scope, 0 = "untitled" group, >0 = section id
    private var pageNo = 0
    private var workPageCount = 0
    private var workSectionCount = 0

    // ── views ─────────────────────────────────────────────────────────
    private lateinit var boxCat: TextView
    private lateinit var boxWork: TextView
    private lateinit var boxSec: TextView
    private lateinit var boxPage: TextView
    private lateinit var crumb: TextView
    private lateinit var scroll: ScrollView
    private lateinit var frontPanel: LinearLayout
    private lateinit var bodyView: TextView
    private lateinit var footer: LinearLayout
    private lateinit var counter: TextView
    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView

    private var fontSp = 18f
    private lateinit var typeface: Typeface
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    private lateinit var dbFileName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbFileName = intent.getStringExtra(EXTRA_DB_FILE) ?: run { finish(); return }
        title = intent.getStringExtra(EXTRA_TITLE) ?: "রবীন্দ্র রচনাবলী"

        lifecycleScope.launch {
            val settings = SettingsRepository(this@RabindraPickerActivity).settingsFlow.first()
            typeface = FontManager.banglaTypeface(this@RabindraPickerActivity, settings.banglaFont)
            fontSp = settings.fontSize.toFloat().coerceIn(14f, 30f)
            buildLayout()
            openDb()
        }
    }

    override fun onDestroy() {
        db?.close()
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════════
    // DB
    // ══════════════════════════════════════════════════════════════════
    private suspend fun openDb() {
        val r = PackDownloadManager.openPack(applicationContext, FOLDER, dbFileName)
        val opened = r.getOrNull()
        if (opened == null) {
            Toast.makeText(this, "বই খোলা যায়নি — লাইব্রেরি থেকে আবার ডাউনলোড করুন।", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val ok = try {
            opened.rawQuery("SELECT value FROM meta WHERE key='schema'", null).use {
                it.moveToFirst() && it.getString(0) == SCHEMA
            }
        } catch (e: Exception) { false }
        if (!ok) {
            opened.close()
            Toast.makeText(this, "পুরনো ফাইল — লাইব্রেরি থেকে মুছে আবার ডাউনলোড করুন।", Toast.LENGTH_LONG).show()
            finish(); return
        }
        db = opened
        restoreLastPosition()
    }

    private fun q(sql: String, vararg args: Any): android.database.Cursor =
        db!!.rawQuery(sql, args.map { it.toString() }.toTypedArray())

    // ══════════════════════════════════════════════════════════════════
    // Layout
    // ══════════════════════════════════════════════════════════════════
    private fun box(hint: String): TextView = TextView(this).apply {
        text = hint
        setTextColor(AppColors.ivory)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = this@RabindraPickerActivity.typeface
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), AppColors.saffron)
            setColor(0x11FFFFFF)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6) }
    }

    private fun smallBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(AppColors.saffron)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = this@RabindraPickerActivity.typeface
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat(); setStroke(dp(1), AppColors.saffron)
        }
        setOnClickListener { onClick() }
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bg)
            setPadding(dp(12), dp(12), dp(12), dp(8))
        }

        root.addView(TextView(this).apply {
            text = "রবীন্দ্র রচনাবলী"
            setTextColor(AppColors.gold); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = this@RabindraPickerActivity.typeface
            gravity = Gravity.CENTER
        })

        boxCat = box("① বিভাগ বেছে নিন  ▾").also { it.setOnClickListener { pickCategory() }; root.addView(it) }
        boxWork = box("② গ্রন্থ / রচনা  ▾").also { it.setOnClickListener { pickWork() }; root.addView(it) }
        boxSec = box("③ অধ্যায় / অংশ  ▾").also { it.setOnClickListener { pickSection() }; root.addView(it) }
        boxPage = box("④ পৃষ্ঠা  ▾").also { it.setOnClickListener { pickPage() }; root.addView(it) }
        boxWork.visibility = View.GONE; boxSec.visibility = View.GONE; boxPage.visibility = View.GONE

        crumb = TextView(this).apply {
            setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = this@RabindraPickerActivity.typeface
            setPadding(dp(2), dp(8), dp(2), dp(4))
        }
        root.addView(crumb)

        // Reading area: front panel (category shortcuts) or page text
        frontPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bodyView = TextView(this).apply {
            setTextColor(AppColors.ivory); setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp)
            typeface = this@RabindraPickerActivity.typeface
            setLineSpacing(0f, 1.35f)
            setTextIsSelectable(true)
            setPadding(dp(4), dp(8), dp(4), dp(24))
            visibility = View.GONE
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(frontPanel); addView(bodyView)
        }
        scroll = ScrollView(this).apply { addView(holder) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Footer: ‹ আগের | পৃষ্ঠা n/m | পরের › + font size
        footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0); visibility = View.GONE
        }
        btnPrev = smallBtn("‹ আগের") { step(-1) }
        btnNext = smallBtn("পরের ›") { step(+1) }
        counter = TextView(this).apply {
            setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = this@RabindraPickerActivity.typeface; gravity = Gravity.CENTER
        }
        footer.addView(btnPrev)
        footer.addView(counter, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(smallBtn("A−") { changeFont(-1f) })
        footer.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
        footer.addView(smallBtn("A+") { changeFont(+1f) })
        footer.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
        footer.addView(btnNext)
        root.addView(footer)

        setContentView(root)
    }

    private fun showFrontPanel() {
        frontPanel.removeAllViews()
        frontPanel.visibility = View.VISIBLE; bodyView.visibility = View.GONE; footer.visibility = View.GONE
        frontPanel.addView(TextView(this).apply {
            text = "উপরের বাক্স থেকে ধাপে ধাপে বেছে নিন — অথবা নিচের বিভাগে চাপুন।"
            setTextColor(AppColors.muted); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = this@RabindraPickerActivity.typeface
            setPadding(dp(4), dp(12), dp(4), dp(12))
        })
        q("SELECT c.id, c.title, (SELECT COUNT(*) FROM works w WHERE w.category_id=c.id) FROM categories c ORDER BY c.sort_order").use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val t = c.getString(1); val n = c.getInt(2)
                frontPanel.addView(smallBtn("$t  ·  ${bn(n)}টি") { selectCategory(id) }.apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = dp(8) }
                })
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Selection logic
    // ══════════════════════════════════════════════════════════════════
    private fun pickCategory() {
        val ids = ArrayList<Long>(); val labels = ArrayList<String>()
        q("SELECT id, title FROM categories ORDER BY sort_order").use { c ->
            while (c.moveToNext()) { ids.add(c.getLong(0)); labels.add(c.getString(1)) }
        }
        showSearchList("বিভাগ", labels, searchable = false) { i -> selectCategory(ids[i]) }
    }

    private fun selectCategory(id: Long) {
        catId = id; workId = -1; sectionId = -1; pageNo = 0
        q("SELECT title FROM categories WHERE id=?", id).use { if (it.moveToFirst()) boxCat.text = "① ${it.getString(0)}  ▾" }
        boxWork.visibility = View.VISIBLE; boxWork.text = "② গ্রন্থ / রচনা  ▾"
        boxSec.visibility = View.GONE; boxPage.visibility = View.GONE
        crumb.text = ""; showFrontPanel(); frontPanel.removeAllViews()
        frontPanel.visibility = View.VISIBLE
        pickWork()
    }

    private fun pickWork() {
        if (catId < 0) { pickCategory(); return }
        val ids = ArrayList<Long>(); val labels = ArrayList<String>()
        q("SELECT id, title, page_count FROM works WHERE category_id=? ORDER BY sort_order", catId).use { c ->
            while (c.moveToNext()) {
                ids.add(c.getLong(0)); labels.add("${c.getString(1)}  (${bn(c.getInt(2))} পৃষ্ঠা)")
            }
        }
        showSearchList("গ্রন্থ / রচনা (${bn(ids.size)})", labels, searchable = true) { i -> selectWork(ids[i], 1) }
    }

    private fun selectWork(id: Long, startPage: Int) {
        workId = id
        q("SELECT title, page_count, section_count, category_id FROM works WHERE id=?", id).use {
            if (it.moveToFirst()) {
                boxWork.text = "② ${it.getString(0)}  ▾"
                workPageCount = it.getInt(1); workSectionCount = it.getInt(2); catId = it.getLong(3)
            }
        }
        boxWork.visibility = View.VISIBLE
        boxSec.visibility = if (workSectionCount > 0) View.VISIBLE else View.GONE
        boxSec.text = "③ অধ্যায় / অংশ  ▾"
        boxPage.visibility = View.VISIBLE
        loadPage(startPage)
        if (workSectionCount > 0 && startPage == 1) pickSection()
    }

    private fun pickSection() {
        if (workId < 0) return
        val ids = ArrayList<Long>(); val labels = ArrayList<String>()
        q("SELECT id, title, page_count FROM sections WHERE work_id=? ORDER BY sort_order", workId).use { c ->
            while (c.moveToNext()) {
                ids.add(c.getLong(0)); labels.add("${c.getString(1)}  (${bn(c.getInt(2))})")
            }
        }
        q("SELECT COUNT(*) FROM pages WHERE work_id=? AND section_id IS NULL", workId).use {
            if (it.moveToFirst() && it.getInt(0) > 0) { ids.add(0L); labels.add("অন্যান্য / শিরোনামহীন  (${bn(it.getInt(0))})") }
        }
        showSearchList("অধ্যায় / অংশ (${bn(ids.size)})", labels, searchable = ids.size > 12) { i ->
            val sid = ids[i]
            val first = if (sid == 0L)
                q("SELECT MIN(page_no) FROM pages WHERE work_id=? AND section_id IS NULL", workId)
            else q("SELECT MIN(page_no) FROM pages WHERE section_id=?", sid)
            val p = first.use { if (it.moveToFirst()) it.getInt(0) else 1 }
            loadPage(p)
            // multi-page section → let the user pick the exact page next
            val cnt = if (sid == 0L) 2 else q("SELECT page_count FROM sections WHERE id=?", sid).use { if (it.moveToFirst()) it.getInt(0) else 1 }
            if (cnt > 1) pickPage()
        }
    }

    private fun pickPage() {
        if (workId < 0) return
        val nos = ArrayList<Int>(); val labels = ArrayList<String>()
        val cur = when {
            sectionId > 0 -> q("SELECT page_no, label FROM pages WHERE section_id=? ORDER BY page_no", sectionId)
            sectionId == 0L -> q("SELECT page_no, label FROM pages WHERE work_id=? AND section_id IS NULL ORDER BY page_no", workId)
            else -> q("SELECT page_no, label FROM pages WHERE work_id=? ORDER BY page_no", workId)
        }
        cur.use { c ->
            while (c.moveToNext()) {
                val n = c.getInt(0); nos.add(n)
                labels.add("${bn(n)}.  ${c.getString(1) ?: ""}")
            }
        }
        showSearchList("পৃষ্ঠা (${bn(nos.size)})", labels, searchable = nos.size > 12) { i -> loadPage(nos[i]) }
    }

    private fun step(delta: Int) {
        val p = pageNo + delta
        if (p in 1..workPageCount) loadPage(p) else
            Toast.makeText(this, if (delta > 0) "এটিই শেষ পৃষ্ঠা" else "এটিই প্রথম পৃষ্ঠা", Toast.LENGTH_SHORT).show()
    }

    private fun changeFont(d: Float) {
        fontSp = (fontSp + d).coerceIn(14f, 34f)
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp)
    }

    // ══════════════════════════════════════════════════════════════════
    // Page loading
    // ══════════════════════════════════════════════════════════════════
    private fun loadPage(n: Int) {
        val c = q("""SELECT p.body, p.section_id, s.title FROM pages p
                     LEFT JOIN sections s ON s.id=p.section_id
                     WHERE p.work_id=? AND p.page_no=?""", workId, n)
        c.use {
            if (!it.moveToFirst()) return
            pageNo = n
            sectionId = if (it.isNull(1)) (if (workSectionCount > 0) 0L else -1L) else it.getLong(1)
            val secTitle = if (it.isNull(2)) null else it.getString(2)
            bodyView.text = it.getString(0)
            boxSec.text = "③ ${secTitle ?: "অন্যান্য / শিরোনামহীন"}  ▾"
            boxPage.text = "④ পৃষ্ঠা ${bn(n)} / ${bn(workPageCount)}  ▾"
            counter.text = "${bn(n)} / ${bn(workPageCount)}"
            val cat = boxCat.text.toString().removePrefix("① ").removeSuffix("  ▾")
            val work = boxWork.text.toString().removePrefix("② ").removeSuffix("  ▾")
            crumb.text = listOfNotNull(cat, work, secTitle).joinToString("  ›  ")
        }
        frontPanel.visibility = View.GONE; bodyView.visibility = View.VISIBLE; footer.visibility = View.VISIBLE
        btnPrev.alpha = if (pageNo > 1) 1f else 0.35f
        btnNext.alpha = if (pageNo < workPageCount) 1f else 0.35f
        scroll.post { scroll.scrollTo(0, 0) }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("work", workId).putInt("page", pageNo).apply()
    }

    private fun restoreLastPosition() {
        val sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val w = sp.getLong("work", -1L); val p = sp.getInt("page", 1)
        val exists = w > 0 && q("SELECT 1 FROM works WHERE id=?", w).use { it.moveToFirst() }
        if (exists) selectWorkQuiet(w, p) else showFrontPanel()
    }

    /** Restore without auto-opening pickers. */
    private fun selectWorkQuiet(w: Long, p: Int) {
        q("SELECT category_id FROM works WHERE id=?", w).use { if (it.moveToFirst()) catId = it.getLong(0) }
        q("SELECT title FROM categories WHERE id=?", catId).use { if (it.moveToFirst()) boxCat.text = "① ${it.getString(0)}  ▾" }
        selectWorkNoPicker(w, p)
    }

    private fun selectWorkNoPicker(w: Long, p: Int) {
        workId = w
        q("SELECT title, page_count, section_count FROM works WHERE id=?", w).use {
            if (it.moveToFirst()) {
                boxWork.text = "② ${it.getString(0)}  ▾"
                workPageCount = it.getInt(1); workSectionCount = it.getInt(2)
            }
        }
        boxWork.visibility = View.VISIBLE; boxPage.visibility = View.VISIBLE
        boxSec.visibility = if (workSectionCount > 0) View.VISIBLE else View.GONE
        loadPage(p.coerceIn(1, workPageCount.coerceAtLeast(1)))
    }

    // ══════════════════════════════════════════════════════════════════
    // Generic searchable list dialog
    // ══════════════════════════════════════════════════════════════════
    private fun showSearchList(title: String, all: List<String>, searchable: Boolean, onPick: (Int) -> Unit) {
        val shown = ArrayList<Int>()
        val listAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList<String>()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(context).apply {
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = this@RabindraPickerActivity.typeface
                }
                tv.setTextColor(AppColors.ivory)
                tv.text = getItem(position)
                return tv
            }
        }
        fun refill(filter: String) {
            shown.clear(); listAdapter.clear()
            val f = filter.trim()
            all.forEachIndexed { i, s -> if (f.isEmpty() || s.contains(f, ignoreCase = true)) { shown.add(i); listAdapter.add(s) } }
            listAdapter.notifyDataSetChanged()
        }
        refill("")

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(AppColors.bg); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        if (searchable) {
            col.addView(EditText(this).apply {
                hint = "খুঁজুন…"; setHintTextColor(AppColors.mutedDim); setTextColor(AppColors.ivory)
                typeface = this@RabindraPickerActivity.typeface; maxLines = 1; isSingleLine = true
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { refill(s?.toString() ?: "") }
                    override fun afterTextChanged(s: Editable?) {}
                })
            })
        }
        val list = ListView(this).apply {
            this.adapter = listAdapter
            isFastScrollEnabled = true
            divider = android.graphics.drawable.ColorDrawable(AppColors.mutedDim); dividerHeight = 1
        }
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)))

        val dlg = AlertDialog.Builder(this).setTitle(title).setView(col).setNegativeButton("বন্ধ", null).create()
        list.setOnItemClickListener { _, _, pos, _ -> dlg.dismiss(); onPick(shown[pos]) }
        dlg.show()
    }

    private fun bn(n: Int): String = n.toString().map { if (it in '0'..'9') "০১২৩৪৫৬৭৮৯"[it - '0'] else it }.joinToString("")

    companion object {
        const val EXTRA_DB_FILE = "extra_rabindra_db_file"
        const val EXTRA_TITLE = "extra_rabindra_title"
        private const val FOLDER = "library_books"
        private const val SCHEMA = "rabindra_reader_v1"
        private const val PREFS = "rabindra_picker_state"
    }
}
