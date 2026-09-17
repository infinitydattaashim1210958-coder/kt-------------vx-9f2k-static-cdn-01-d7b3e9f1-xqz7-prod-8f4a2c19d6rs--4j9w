package com.kyronix.swadhyaa.ui.theme

import android.content.Context
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.AppCompatTextView

/**
 * A TextView that:
 *  - Allows text SELECTION (long-press to select)
 *  - Blocks COPY / SHARE / SELECT ALL from the action menu
 *  - Shows custom "হাইলাইট করুন" and "বুকমার্ক করুন" actions instead
 *  - Calls [onHighlight] / [onBookmark] with the selected text + offsets
 *
 * Implementation note: overriding startActionMode() intercepts the
 * platform's floating action mode and replaces it with our own menu,
 * which contains only our two actions. The standard COPY/SHARE/etc.
 * items are never shown because we never call through to super's menu.
 */
class SelectableBookmarkTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    var onHighlight: ((start: Int, end: Int, text: String) -> Unit)? = null
    var onBookmark:  ((start: Int, end: Int, text: String) -> Unit)? = null

    init {
        setTextIsSelectable(true)
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        // Replace the platform's action mode with our own custom one
        return super.startActionMode(BookmarkActionCallback(), type)
    }

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
            val start = selectionStart.coerceAtLeast(0)
            val end   = selectionEnd.coerceAtLeast(start)
            if (start == end) { mode.finish(); return true }
            val selected = text?.subSequence(start, end)?.toString() ?: ""
            when (item.itemId) {
                ITEM_HIGHLIGHT -> { onHighlight?.invoke(start, end, selected); applyHighlightSpan(start, end) }
                ITEM_BOOKMARK  -> { onBookmark?.invoke(start, end, selected);  applyHighlightSpan(start, end) }
            }
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    /** Applies an amber highlight span to [start]..[end] in the current text. */
    fun applyHighlightSpan(start: Int, end: Int) {
        val sp = text as? Spannable ?: return
        if (start >= end || end > sp.length) return
        sp.setSpan(
            BackgroundColorSpan(0x55F5A623.toInt()),
            start, end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    /** Restores all highlight spans from a saved list of (start, end) pairs. */
    fun restoreHighlights(ranges: List<Pair<Int, Int>>) {
        ranges.forEach { (s, e) -> applyHighlightSpan(s, e) }
    }
}
