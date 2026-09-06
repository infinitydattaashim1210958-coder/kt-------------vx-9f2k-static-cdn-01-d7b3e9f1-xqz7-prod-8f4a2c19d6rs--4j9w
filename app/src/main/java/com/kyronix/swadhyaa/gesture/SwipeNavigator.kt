package com.kyronix.swadhyaa.ui.gesture

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Attaches left/right swipe navigation to a View — e.g. a reader screen's
 * root ScrollView. Matches legacy gestures.js §23 (RISK_REGISTER.md R6):
 * swipe left = next, swipe right = prev, with the exact same distance/
 * velocity thresholds (60px / 0.35px-per-ms ≈ 350dp/sec) ported directly
 * from source rather than guessed.
 *
 * Ordinary vertical scrolling inside a reader screen keeps working
 * normally — the mostly-horizontal + fast + long-enough requirement below
 * is what legacy itself uses to distinguish an intentional page-swipe from
 * a vertical scroll.
 *
 * onTouchListener returns false so the underlying ScrollView still
 * receives and handles the same touch stream for normal scrolling/clicks
 * — this only *additionally* watches for the fling gesture, it doesn't
 * take over touch handling. Matches legacy's own "never hijack vertical
 * scrolling / never hijack buttons-links-inputs" safety contract, though
 * by a different mechanism (Android's touch dispatch already delivers
 * clicks to child buttons regardless of a parent's touch listener,
 * whereas legacy explicitly excludes BUTTON/A/INPUT/TEXTAREA/SELECT by
 * tag at touchstart).
 *
 * NOT ported: legacy gestures.js §24's pull-down-to-open chapter selector
 * (a bottom-sheet gesture, secondary to an always-present header button
 * per legacy's own comment: "the button is always present so gesture is
 * never the only path"). Since the button path is what legacy itself
 * documents as the required/primary interaction, and swipe navigation
 * (the part implemented here) is the gesture most directly requested,
 * this was a deliberate scope decision, not a silent omission — flagged
 * here for whoever picks up the bottom-sheet chrome next.
 */
fun View.attachSwipeNavigation(onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit) {
    val density = resources.displayMetrics.density
    // Thresholds verified against legacy gestures.js §23 exactly:
    // MIN_DISTANCE = 60 (css px, ≈ dp), MIN_VELOCITY = 0.35 px/ms = 350 dp/sec.
    // GestureDetector.onFling's velocityX is in real device px/sec, so both
    // are scaled by density to convert from legacy's dp-equivalent units.
    val minDistancePx = 60 * density
    val minVelocityPx = 350 * density

    val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val isMostlyHorizontal = abs(dx) > abs(dy) * 1.5f
                val isFastAndLongEnough = abs(dx) > minDistancePx && abs(velocityX) > minVelocityPx
                if (isMostlyHorizontal && isFastAndLongEnough) {
                    if (dx < 0) onSwipeLeft() else onSwipeRight()
                    return true
                }
                return false
            }
        }
    )
    setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
        false
    }
}
