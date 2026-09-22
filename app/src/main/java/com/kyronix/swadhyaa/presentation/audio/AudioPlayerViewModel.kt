package com.kyronix.swadhyaa.presentation.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.remote.MantraAudioResolver
import com.kyronix.swadhyaa.domain.model.MantraAudioRef
import com.kyronix.swadhyaa.service.MantraPlayerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioPlayerViewModel(
    private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "AudioPlayerVM"
    }

    private val _state = MutableStateFlow<MantraPlaybackState>(MantraPlaybackState.Idle)
    val state: StateFlow<MantraPlaybackState> = _state.asStateFlow()

    private var service: MantraPlayerService? = null
    private var bound   = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as MantraPlayerService.LocalBinder).getService()
            service = svc
            bound   = true
            Log.d(TAG, "Service connected")
            viewModelScope.launch {
                svc.state.collect { _state.value = it }
            }
            pendingRef?.let { svc.play(it); pendingRef = null }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null; bound = false
            Log.d(TAG, "Service disconnected")
        }
    }

    private var pendingRef: MantraAudioRef? = null

    fun bindService(context: Context) {
        if (bound) return
        val intent = MantraPlayerService.buildIntent(context)
        ContextCompat.startForegroundService(context, intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (bound) {
            context.unbindService(connection)
            bound = false
        }
    }

    fun loadMantra(
        mantraId:       Int,
        vedaId:         Int,
        vedaCode:       String,
        mantraRefId:    String,
        devanagariText: String = "",
        displayLabel:   String = mantraRefId
    ) {
        val url = MantraAudioResolver.resolveUrl(vedaCode, mantraRefId)
        if (url == null) {
            _state.value = MantraPlaybackState.NotAvailable
            return
        }
        val ref = MantraAudioRef(
            mantraId       = mantraId,
            vedaId         = vedaId,
            vedaCode       = vedaCode,
            mantraRefId    = mantraRefId,
            audioUrl       = url,
            devanagariText = devanagariText,
            displayLabel   = displayLabel
        )
        service?.play(ref) ?: run { pendingRef = ref }
    }

    fun togglePlayPause() { service?.togglePlayPause() }

    fun seekTo(fraction: Float) { service?.seekTo(fraction) }

    fun setListeningMode(enabled: Boolean) { service?.setListeningMode(enabled) }

    override fun onCleared() {
        super.onCleared()
        // Service stays alive for background playback — caller unbinds in onStop().
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AudioPlayerViewModel(context.applicationContext) as T
    }
}
