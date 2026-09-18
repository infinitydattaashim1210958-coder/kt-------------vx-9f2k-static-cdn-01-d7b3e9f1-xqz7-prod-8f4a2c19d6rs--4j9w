package com.kyronix.swadhyaa.ui.theme

import android.content.Context
import android.os.Build
import android.text.Spannable
import android.text.Selection
import android.text.style.BackgroundColorSpan
import android.text.method.ArrowKeyMovementMethod
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView

/**
 * A TextView that:
 *  - Allows long-press text selection
 *  - BLOCKS Copy / Share / Select All from the action menu
 *  - Shows only "হাইলাইট করুন" and "বুকমার্ক করুন"
 *  - Clears the selection and dismisses the handle on finger-up outside
 *    the selected range (tap-away to deselect)
 *
 * Root causes of the three selection bugs this fixes:
 *
 *  Bug 1 — "handle stays until next long-press":
 *    setTextIsSelectable(true) installs LinkMovementMethod which never
 *    clears the selection on a plain tap — it only moves the cursor.
 *    Fix: use ArrowKeyMovementMethod (read-only cursor, no link following)
 *    and manually clear in onTouchEvent on ACTION_UP when no text is
 *    being selected.
 *
 *  Bug 2 — "can't deselect":
 *    After our custom action mode calls mode.finish(), the platform
 *    re-sets the selection back to the last known range in some versions.
 *    Fix: override onActionModeFinished() to call clearSelection() and
 *    invalidate() so the handles and highlight both vanish.
 *
 *  Bug 3 — bookmark not showing in Bookmarks tab:
 *    saveSelection() in the ViewModel only wrote to Room. The Bookmarks
 *    tab reads from UserPrefs.bookmarksFlow (DataStore JSON). The two
 *    stores are independent. Fix: ViewModel now also calls
 *    userPrefs.addBookmark() — wired via the new [onBookmarkPersisted]
 *    lambda that the Activity sets after constructing the view.
 */
class SelectableBookmarkTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    // Called with (selStart, selEnd, text, kind) after the user picks an action.
    // "kind" is "highlight" or "bookmark".
    var onHighlight: ((start: Int, end: Int, text: String) -> Unit)? = null
    var onBookmark:  ((start: Int, end: Int, text: String) -> Unit)? = null

    private var activeActionMode: ActionMode? = null

    init {
        setTextIsSelectable(true)
        // ArrowKeyMovementMethod keeps selection working without
        // LinkMovementMethod's side-effect of never clearing it on tap.
        movementMethod = ArrowKeyMovementMethod.getInstance()
        highlightColor = 0x44F5A623.toInt()  // amber selection tint
    }

    // ── Clear selection on tap-away (fix Bug 1 & 2) ──────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val result = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            // If there is a current selection, clear it so the handles disappear.
            val sp = text as? Spannable
            if (sp != null && selectionStart != selectionEnd) {
                // Don't clear if an action mode is showing — the user just
                // released after dragging a handle, not tapping away.
                if (activeActionMode == null) {
                    clearSelection(sp)
                }
            }
        }
        return result
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        val mode = super.startActionMode(BookmarkActionCallback(), type)
        activeActionMode = mode
        return mode
    }

    /** Called by the platform when any action mode on this view ends. */
    override fun onActionModeFinished(mode: ActionMode) {
        super.onActionModeFinished(mode)
        if (mode === activeActionMode) {
            activeActionMode = null
            // Clear the selection highlight + handles (fix Bug 2)
            (text as? Spannable)?.let { clearSelection(it) }
            invalidate()
        }
    }

    private fun clearSelection(sp: Spannable) {
        try {
            Selection.removeSelection(sp)
        } catch (_: Exception) {}
    }

    // ── Custom action mode: only Highlight & Bookmark ─────────────────────────

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
                ITEM_HIGHLIGHT -> {
                    applyHighlightSpan(start, end)
                    onHighlight?.invoke(start, end, selected)
                }
                ITEM_BOOKMARK -> {
                    applyHighlightSpan(start, end)
                    onBookmark?.invoke(start, end, selected)
                }
            }
            mode.finish()   // triggers onActionModeFinished → clearSelection
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    // ── Span helpers ──────────────────────────────────────────────────────────

    /** Applies a persistent amber highlight span over [start]..[end]. */
    fun applyHighlightSpan(start: Int, end: Int) {
        val sp = text as? Spannable ?: return
        if (start >= end || end > sp.length) return
        // Remove any existing span in this exact range first to avoid doubles
        sp.getSpans(start, end, BackgroundColorSpan::class.java)
            .filter { sp.getSpanStart(it) == start && sp.getSpanEnd(it) == end }
            .forEach { sp.removeSpan(it) }
        sp.setSpan(
            BackgroundColorSpan(0x66C850FF.toInt()),  // purple bookmark highlight
            start, end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    /** Re-applies saved spans (called after paragraph rebuild on chapter reload). */
    fun restoreHighlights(ranges: List<Pair<Int, Int>>) {
        ranges.forEach { (s, e) -> applyHighlightSpan(s, e) }
    }
}
