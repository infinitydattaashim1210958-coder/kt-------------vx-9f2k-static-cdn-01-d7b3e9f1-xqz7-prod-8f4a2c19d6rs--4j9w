package com.kyronix.swadhyaa.ui.theme

import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView

/**
 * A TextView that:
 *  - Allows long-press text selection
 *  - Blocks Copy / Share / Select All
 *  - Shows only "হাইলাইট করুন" and "বুকমার্ক করুন"
 *  - Clears the selection handle on tap-away and after action
 *
 * Bug fixes:
 *
 *  Bug 1 — handle stays: ArrowKeyMovementMethod replaces the auto-installed
 *    LinkMovementMethod. onTouchEvent clears selection on ACTION_UP when no
 *    action mode is active.
 *
 *  Bug 2 — can't deselect: selection is cleared in onDestroyActionMode()
 *    (the correct View-level callback — NOT onActionModeFinished() which
 *    lives on Activity/Window.Callback and does not exist on View/TextView).
 *
 *  Bug 3 — bookmark not in tab: handled in ViewModel (calls userPrefs.addBookmark).
 */
class SelectableBookmarkTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    var onHighlight: ((start: Int, end: Int, text: String) -> Unit)? = null
    var onBookmark:  ((start: Int, end: Int, text: String) -> Unit)? = null

    private var activeActionMode: ActionMode? = null

    init {
        setTextIsSelectable(true)
        // ArrowKeyMovementMethod: keeps selection working,
        // does NOT suppress tap-away clearing like LinkMovementMethod does.
        movementMethod = ArrowKeyMovementMethod.getInstance()
        highlightColor = 0x44C850FF.toInt()  // purple selection tint
    }

    // ── Tap-away clears selection (fix Bug 1) ─────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val result = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && activeActionMode == null) {
            // No action menu open: a plain tap — clear any lingering selection
            (text as? Spannable)?.let { clearSel(it) }
        }
        return result
    }

    // ── Intercept action mode: replace with our custom menu ───────────────────

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        val mode = super.startActionMode(BookmarkActionCallback(), type)
        activeActionMode = mode
        return mode
    }

    private fun clearSel(sp: Spannable) {
        try { Selection.removeSelection(sp) } catch (_: Exception) {}
        invalidate()
    }

    // ── Custom action mode callback ───────────────────────────────────────────

    private inner class BookmarkActionCallback : ActionMode.Callback {

        private val ITEM_HIGHLIGHT = 1
        private val ITEM_BOOKMARK  = 2

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, ITEM_HIGHLIGHT, 0, "হাইলাইট করুন")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, ITEM_BOOKMARK, 1, "বুকমার্ক করুন")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val start    = selectionStart.coerceAtLeast(0)
            val end      = selectionEnd.coerceAtLeast(start)
            if (start == end) { mode.finish(); return true }
            val selected = text?.subSequence(start, end)?.toString() ?: ""
            when (item.itemId) {
                ITEM_HIGHLIGHT -> { applyHighlightSpan(start, end); onHighlight?.invoke(start, end, selected) }
                ITEM_BOOKMARK  -> { applyHighlightSpan(start, end); onBookmark?.invoke(start, end, selected)  }
            }
            mode.finish()   // → triggers onDestroyActionMode below
            return true
        }

        /**
         * onDestroyActionMode is the correct place to clear selection in a
         * View subclass. onActionModeFinished() does NOT exist on View —
         * it is a method of Activity / Window.Callback only.
         */
        override fun onDestroyActionMode(mode: ActionMode) {
            activeActionMode = null
            // Clear the selection highlight + handles (fix Bug 2)
            (text as? Spannable)?.let { clearSel(it) }
        }
    }

    // ── Span helpers ──────────────────────────────────────────────────────────

    fun applyHighlightSpan(start: Int, end: Int) {
        val sp = text as? Spannable ?: return
        if (start >= end || end > sp.length) return
        // Remove exact-range duplicates before adding
        sp.getSpans(start, end, BackgroundColorSpan::class.java)
            .filter { sp.getSpanStart(it) == start && sp.getSpanEnd(it) == end }
            .forEach { sp.removeSpan(it) }
        sp.setSpan(
            BackgroundColorSpan(0x66C850FF.toInt()),  // purple persistent highlight
            start, end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    fun restoreHighlights(ranges: List<Pair<Int, Int>>) {
        ranges.forEach { (s, e) -> applyHighlightSpan(s, e) }
    }
}
