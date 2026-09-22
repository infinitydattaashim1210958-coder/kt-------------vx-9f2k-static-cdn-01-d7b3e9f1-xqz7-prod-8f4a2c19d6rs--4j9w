package com.kyronix.swadhyaa.presentation.audio

import com.kyronix.swadhyaa.domain.model.MantraAudioRef

/**
 * Immutable snapshot of the audio player state, collected by UI components.
 */
sealed class MantraPlaybackState {

    /** Nothing loaded yet. */
    data object Idle : MantraPlaybackState()

    /** Buffering the stream — shows spinner in UI. */
    data object Loading : MantraPlaybackState()

    /** Audio is actively playing. */
    data class Playing(
        val ref:             MantraAudioRef,
        val positionMs:      Int,
        val durationMs:      Int,
        val isListeningMode: Boolean = false
    ) : MantraPlaybackState() {
        val fraction get() =
            if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    }

    /** Audio is paused mid-stream. */
    data class Paused(
        val ref:             MantraAudioRef,
        val positionMs:      Int,
        val durationMs:      Int,
        val isListeningMode: Boolean = false
    ) : MantraPlaybackState() {
        val fraction get() =
            if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    }

    /** This mantra is not covered by any audio release. */
    data object NotAvailable : MantraPlaybackState()

    /**
     * Network or playback error.
     * [message] is user-visible (Bengali).
     */
    data class Error(val message: String) : MantraPlaybackState()
}

// ── Convenience getters ───────────────────────────────────────────────────────

val MantraPlaybackState.isPlaying  get() = this is MantraPlaybackState.Playing
val MantraPlaybackState.isPaused   get() = this is MantraPlaybackState.Paused
val MantraPlaybackState.isActive   get() = isPlaying || isPaused
val MantraPlaybackState.currentRef get() =
    (this as? MantraPlaybackState.Playing)?.ref
        ?: (this as? MantraPlaybackState.Paused)?.ref

val MantraPlaybackState.positionMs get() =
    (this as? MantraPlaybackState.Playing)?.positionMs
        ?: (this as? MantraPlaybackState.Paused)?.positionMs ?: 0

val MantraPlaybackState.durationMs get() =
    (this as? MantraPlaybackState.Playing)?.durationMs
        ?: (this as? MantraPlaybackState.Paused)?.durationMs ?: 0

val MantraPlaybackState.fraction get() =
    (this as? MantraPlaybackState.Playing)?.fraction
        ?: (this as? MantraPlaybackState.Paused)?.fraction ?: 0f

val MantraPlaybackState.isListeningMode get() =
    (this as? MantraPlaybackState.Playing)?.isListeningMode == true
        || (this as? MantraPlaybackState.Paused)?.isListeningMode == true
