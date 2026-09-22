package com.kyronix.swadhyaa.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media.app.NotificationCompat.MediaStyle
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.remote.MantraAudioResolver
import com.kyronix.swadhyaa.domain.model.MantraAudioRef
import com.kyronix.swadhyaa.presentation.audio.MantraPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Foreground service that:
 *  • Streams mantra audio directly from GitHub Releases CDN (no caching)
 *  • Manages Android MediaSession for lock-screen controls
 *  • Publishes a music-player-style notification with prev/play/next/close
 *  • Auto-advances to the next mantra in Listening Mode (even screen-off)
 *
 * Lifecycle
 * ─────────
 * 1. ViewModel calls ContextCompat.startForegroundService(ctx, intent)
 * 2. ViewModel binds with bindService() to get the LocalBinder
 * 3. ViewModel calls service.play(ref) to start streaming
 * 4. Notification action buttons send intents → onStartCommand handles them
 * 5. ViewModel unbinds on destroy; service stays alive as foreground
 * 6. ACTION_STOP (from notification close button) kills the service
 */
class MantraPlayerService : Service() {

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        const val NOTIFICATION_ID   = 1337
        const val CHANNEL_ID        = "mantra_audio_v1"
        const val ACTION_PLAY_PAUSE = "sw.PLAY_PAUSE"
        const val ACTION_NEXT       = "sw.NEXT"
        const val ACTION_PREV       = "sw.PREV"
        const val ACTION_STOP       = "sw.STOP"
        private const val TAG       = "MantraPlayerSvc"

        /** Start + bind helper — call from ViewModel. */
        fun buildIntent(ctx: Context) = Intent(ctx, MantraPlayerService::class.java)
    }

    // ── Binder ────────────────────────────────────────────────────────────────
    inner class LocalBinder : Binder() {
        fun getService(): MantraPlayerService = this@MantraPlayerService
    }
    private val binder = LocalBinder()

    // ── State ─────────────────────────────────────────────────────────────────
    private val _state = MutableStateFlow<MantraPlaybackState>(MantraPlaybackState.Idle)
    val state: StateFlow<MantraPlaybackState> = _state.asStateFlow()

    var isListeningMode = false
        private set

    // ── Player ────────────────────────────────────────────────────────────────
    private var player:      MediaPlayer?   = null
    private var currentRef:  MantraAudioRef? = null
    private var artCache:    Bitmap?         = null
    private var progressJob: Job?            = null

    // ── Coroutine scope ───────────────────────────────────────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ── MediaSession ──────────────────────────────────────────────────────────
    private lateinit var session: MediaSessionCompat

    // ── Audio focus ───────────────────────────────────────────────────────────
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var focusRequest: AudioFocusRequest? = null

    // ── DB (for auto-next/prev) ───────────────────────────────────────────────
    private val vedaDao by lazy { CoreDatabase.getInstance(applicationContext).vedaDao() }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        Log.d(TAG, "Service created")
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground promptly when started via startForegroundService
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT       -> scope.launch { skipToNext() }
            ACTION_PREV       -> scope.launch { skipToPrev() }
            ACTION_STOP       -> stopEverything()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
        releasePlayer()
        session.release()
        scope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API (called from AudioPlayerViewModel)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start streaming the given mantra.
     * Safe to call while another mantra is already playing — it stops it first.
     */
    fun play(ref: MantraAudioRef) {
        currentRef = ref
        artCache   = null   // regenerate art for new mantra
        releasePlayer()
        _state.value = MantraPlaybackState.Loading
        updateNotification()

        if (!requestAudioFocus()) {
            _state.value = MantraPlaybackState.Error("অডিও ফোকাস পাওয়া যায়নি।")
            return
        }

        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            // Stream directly — no download, redirect-following enabled by Android
            mp.setDataSource(applicationContext, Uri.parse(ref.audioUrl))
            mp.setWakeMode(applicationContext, android.os.PowerManager.PARTIAL_WAKE_LOCK)

            mp.setOnPreparedListener { prepared ->
                player = prepared
                prepared.start()
                startProgressTick()
                pushState()
                updateSessionMetadata()
                updateSessionPlaybackState(true)
                updateNotification()
                Log.d(TAG, "Playing: ${ref.displayLabel}")
            }

            mp.setOnCompletionListener {
                stopProgressTick()
                updateSessionPlaybackState(false)
                _state.value = MantraPlaybackState.Paused(
                    ref          = currentRef ?: return@setOnCompletionListener,
                    positionMs   = 0,
                    durationMs   = it.duration.coerceAtLeast(0),
                    isListeningMode = isListeningMode
                )
                updateNotification()
                if (isListeningMode) {
                    scope.launch { delay(400); skipToNext() }  // short gap between mantras
                }
            }

            mp.setOnErrorListener { _, what, extra ->
                val msg = when (what) {
                    MediaPlayer.MEDIA_ERROR_IO          ->
                        "ইন্টারনেট সংযোগ ত্রুটি। নেটওয়ার্ক পরীক্ষা করুন।"
                    MediaPlayer.MEDIA_ERROR_TIMED_OUT   ->
                        "সংযোগের সময়সীমা শেষ। পুনরায় চেষ্টা করুন।"
                    MediaPlayer.MEDIA_ERROR_SERVER_DIED ->
                        "সার্ভার সংযোগ বিচ্ছিন্ন।"
                    else ->
                        "অডিও চালাতে সমস্যা হয়েছে। (E$what/$extra)"
                }
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                _state.value = MantraPlaybackState.Error(msg)
                updateNotification()
                true
            }

            player = mp
            mp.prepareAsync()   // non-blocking; buffers in background

        } catch (e: IOException) {
            Log.e(TAG, "setDataSource failed", e)
            _state.value = MantraPlaybackState.Error("ইন্টারনেট সংযোগ নেই।")
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "play() failed", e)
            _state.value = MantraPlaybackState.Error("অডিও চালু করা যায়নি।")
            updateNotification()
        }
    }

    fun togglePlayPause() {
        val mp = player ?: return
        if (mp.isPlaying) {
            mp.pause()
            stopProgressTick()
            updateSessionPlaybackState(false)
        } else {
            mp.start()
            startProgressTick()
            updateSessionPlaybackState(true)
        }
        pushState()
        updateNotification()
    }

    fun seekTo(fraction: Float) {
        val mp  = player ?: return
        val pos = (fraction.coerceIn(0f, 1f) * mp.duration).toInt()
        mp.seekTo(pos)
        pushState()
    }

    fun setListeningMode(enabled: Boolean) {
        isListeningMode = enabled
        pushState()
        updateNotification()
        Log.d(TAG, "Listening mode: $enabled")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auto-next / prev (used by notification buttons & listening mode)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun skipToNext() {
        val ref = currentRef ?: return
        val next = vedaDao.getNextMantra(ref.vedaId, ref.mantraId) ?: run {
            Log.d(TAG, "No next mantra after ${ref.mantraRefId}"); return
        }
        val url = MantraAudioResolver.resolveUrl(ref.vedaCode, next.mantraRefId) ?: run {
            Log.w(TAG, "No audio URL for ${next.mantraRefId}"); return
        }
        val nextRef = MantraAudioRef(
            mantraId       = next.id,
            vedaId         = ref.vedaId,
            vedaCode       = ref.vedaCode,
            mantraRefId    = next.mantraRefId,
            audioUrl       = url,
            // TODO: replace next.mantraRefId with actual Sanskrit text field on MantraEntity
            // e.g. devanagariText = next.devanagari ?: next.mantraRefId
            devanagariText = next.mantraRefId,
            displayLabel   = buildLabel(ref.vedaCode, next)
        )
        play(nextRef)
    }

    private suspend fun skipToPrev() {
        val ref = currentRef ?: return
        val prev = vedaDao.getPrevMantra(ref.vedaId, ref.mantraId) ?: run {
            Log.d(TAG, "No prev mantra before ${ref.mantraRefId}"); return
        }
        val url = MantraAudioResolver.resolveUrl(ref.vedaCode, prev.mantraRefId) ?: run {
            Log.w(TAG, "No audio URL for ${prev.mantraRefId}"); return
        }
        val prevRef = MantraAudioRef(
            mantraId       = prev.id,
            vedaId         = ref.vedaId,
            vedaCode       = ref.vedaCode,
            mantraRefId    = prev.mantraRefId,
            devanagariText = prev.mantraRefId,  // TODO: use actual text field
            audioUrl       = url,
            displayLabel   = buildLabel(ref.vedaCode, prev)
        )
        play(prevRef)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun stopEverything() {
        abandonAudioFocus()
        releasePlayer()
        session.isActive = false
        _state.value = MantraPlaybackState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startProgressTick() {
        stopProgressTick()
        progressJob = scope.launch {
            while (isActive) {
                pushState()
                delay(250L)
            }
        }
    }

    private fun stopProgressTick() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun pushState() {
        val mp  = player ?: return
        val ref = currentRef ?: return
        _state.value = if (mp.isPlaying)
            MantraPlaybackState.Playing(ref, mp.currentPosition, mp.duration.coerceAtLeast(0), isListeningMode)
        else
            MantraPlaybackState.Paused(ref, mp.currentPosition, mp.duration.coerceAtLeast(0), isListeningMode)
    }

    private fun releasePlayer() {
        stopProgressTick()
        player?.run {
            try { if (isPlaying) stop() } catch (_: Exception) {}
            reset(); release()
        }
        player = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio focus
    // ─────────────────────────────────────────────────────────────────────────

    private fun requestAudioFocus(): Boolean {
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    player?.pause(); stopProgressTick(); pushState(); updateNotification()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    player?.start(); startProgressTick(); pushState(); updateNotification()
                }
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setOnAudioFocusChangeListener(listener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MediaSession
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupMediaSession() {
        session = MediaSessionCompat(this, "MantraSession")
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay()           { player?.takeIf { !it.isPlaying }?.let { togglePlayPause() } }
            override fun onPause()          { player?.takeIf {  it.isPlaying }?.let { togglePlayPause() } }
            override fun onSkipToNext()     { scope.launch { skipToNext() } }
            override fun onSkipToPrevious() { scope.launch { skipToPrev() } }
            override fun onStop()           { stopEverything() }
            override fun onSeekTo(pos: Long) {
                player?.let { it.seekTo(pos.toInt()); pushState() }
            }
        })
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.isActive = true
    }

    private fun updateSessionMetadata() {
        val ref = currentRef ?: return
        // Lazy-generate glowing art (cached per mantra)
        if (artCache == null) {
            artCache = MantraArtGenerator.generate(ref.devanagariText, ref.displayLabel)
        }
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, ref.displayLabel)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, vedaDisplayName(ref.vedaCode))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,
                    if (isListeningMode) "শ্রবণ মোড সক্রিয়" else "পঠন মোড")
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artCache)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                    player?.duration?.toLong()?.coerceAtLeast(0) ?: -1L)
                .build()
        )
    }

    private fun updateSessionPlaybackState(isPlaying: Boolean) {
        val mp       = player
        val stateVal = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                       else          PlaybackStateCompat.STATE_PAUSED
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(stateVal, mp?.currentPosition?.toLong() ?: 0L, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP
                )
                .build()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "মন্ত্র উচ্চারণ",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "মন্ত্র অডিও প্লেয়ার নিয়ন্ত্রণ"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val ref       = currentRef
        val isPlaying = player?.isPlaying == true

        // PendingIntent factory
        fun svcIntent(action: String, reqCode: Int) = PendingIntent.getService(
            this, reqCode,
            Intent(this, MantraPlayerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val piPlayPause = svcIntent(ACTION_PLAY_PAUSE, 10)
        val piNext      = svcIntent(ACTION_NEXT,       11)
        val piPrev      = svcIntent(ACTION_PREV,       12)
        val piStop      = svcIntent(ACTION_STOP,       13)

        // Tap notification → open app
        val piOpen = packageManager.getLaunchIntentForPackage(packageName)
            ?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }

        val title = when {
            ref == null         -> "মন্ত্র উচ্চারণ"
            else                -> ref.displayLabel
        }
        val subtitle = buildString {
            append(vedaDisplayName(ref?.vedaCode ?: ""))
            if (isListeningMode) append(" • 🎧 শ্রবণ মোড")
        }

        // Reuse cached art
        if (artCache == null && ref != null) {
            artCache = MantraArtGenerator.generate(ref.devanagariText, ref.displayLabel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setLargeIcon(artCache)
            .setContentIntent(piOpen)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            // Actions: 0=Prev, 1=Play/Pause, 2=Next, 3=Close
            .addAction(android.R.drawable.ic_media_previous, "পূর্ববর্তী", piPrev)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause
                else           android.R.drawable.ic_media_play,
                if (isPlaying) "বিরতি" else "চালু",
                piPlayPause
            )
            .addAction(android.R.drawable.ic_media_next, "পরবর্তী", piNext)
            .addAction(android.R.drawable.ic_delete, "বন্ধ", piStop)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)   // compact: prev, play, next
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(piStop)
            )
            .build()
    }

    private fun updateNotification() {
        updateSessionMetadata()
        val notification = buildNotification()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun vedaDisplayName(code: String) = when (code.lowercase()) {
        "rigveda"     -> "ঋগ্বেদ"
        "samaveda"    -> "সামবেদ"
        "yajurveda"   -> "যজুর্বেদ"
        "atharvaveda" -> "অথর্ববেদ"
        else          -> code
    }

    /** Build a human-readable label from a MantraEntity. */
    private fun buildLabel(vedaCode: String, mantra: Any): String {
        // Use reflection to read common field names safely
        fun field(name: String): Int? = try {
            mantra.javaClass.getDeclaredField(name).also { it.isAccessible = true }
                .getInt(mantra).takeIf { it > 0 }
        } catch (_: Exception) { null }

        fun refId(): String = try {
            (mantra.javaClass.getDeclaredField("mantraRefId").also { it.isAccessible = true }
                .get(mantra) as? String) ?: ""
        } catch (_: Exception) { "" }

        val l1 = field("level1"); val l2 = field("level2"); val mn = field("mantraNo")
        return when (vedaCode.lowercase()) {
            "rigveda"     -> "ঋগ্বেদ ${l1 ?: ""}.${l2 ?: ""}.${mn ?: ""}"
            "samaveda"    -> "সামবেদ ${refId()}"
            "yajurveda"   -> "যজুর্বেদ ${l1 ?: ""}.${mn ?: ""}"
            "atharvaveda" -> "অথর্ববেদ ${l1 ?: ""}.${l2 ?: ""}.${mn ?: ""}"
            else          -> refId()
        }
    }
}
