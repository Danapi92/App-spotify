package com.example.player

import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.AudioQuality
import com.example.data.model.MusicTrack
import com.example.data.model.RepeatModeOption
import com.example.data.repository.OfflineDownloadManager
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class AudioOutputRoute {
    SPEAKER, BLUETOOTH, WIRED_HEADSET
}

data class EqualizerBandUi(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Int
)

data class EqualizerUiState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val presets: List<String> = emptyList(),
    val selectedPreset: Int = 0,
    val minBandLevelMb: Int = -1500,
    val maxBandLevelMb: Int = 1500,
    val bands: List<EqualizerBandUi> = emptyList()
)

data class PlaybackUiState(
    val currentTrack: MusicTrack? = null,
    val queue: List<MusicTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val errorMessage: String? = null,
    val volume: Float = 0.7f,
    val audioRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    val audioQuality: AudioQuality = AudioQuality.AUTO,
    val equalizer: EqualizerUiState = EqualizerUiState()
)

class PlaybackController(
    context: Context,
    private val downloadManager: OfflineDownloadManager,
    private val onTrackChanged: (MusicTrack) -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionJob: Job? = null
    private var equalizer: Equalizer? = null
    private var activeQueueTracks: List<MusicTrack> = emptyList()
    private var lastReportedTrackId: String? = null

    private val _uiState = MutableStateFlow(
        PlaybackUiState(volume = readCurrentSystemVolume())
    )
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioOutputRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioOutputRoute()
        }
    }

    init {
        connectToService()
        refreshAudioOutputRoute()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        scope.launch {
            PlaybackSessionBridge.audioSessionId.collect { sessionId ->
                if (sessionId > 0) {
                    initEqualizer(sessionId)
                }
            }
        }
    }

    private fun connectToService() {
        val token = SessionToken(appContext, ComponentName(appContext, StreamPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    attachPlayerListener(controller)
                    syncFromController(controller)
                    startPositionTicker()
                }.onFailure { err ->
                    Log.e(TAG, "Failed to connect MediaController", err)
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                val duration = controller.duration.takeIf { it > 0L }
                    ?: (_uiState.value.currentTrack?.durationSeconds?.times(1000L) ?: 0L)
                _uiState.update {
                    it.copy(
                        isBuffering = buffering,
                        durationMs = duration,
                        errorMessage = if (playbackState == Player.STATE_READY) null else it.errorMessage
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncFromController(controller)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val mapped = when (repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
                    Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
                    else -> RepeatModeOption.OFF
                }
                _uiState.update { it.copy(repeatMode = mapped) }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer playback error: ${error.errorCodeName}", error)
                controller.stop()
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = error.localizedMessage ?: "Audio stream failed to load."
                    )
                }
            }
        })
    }

    private fun syncFromController(controller: MediaController) {
        val index = controller.currentMediaItemIndex
        val track = activeQueueTracks.getOrNull(index)
        val duration = controller.duration.takeIf { it > 0L }
            ?: (track?.durationSeconds?.times(1000L) ?: 0L)
        _uiState.update {
            it.copy(
                currentTrack = track ?: it.currentTrack,
                queue = activeQueueTracks,
                currentIndex = index,
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = duration
            )
        }
        if (track != null && track.id != lastReportedTrackId) {
            lastReportedTrackId = track.id
            onTrackChanged(track)
        }
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val ctrl = mediaController
                if (ctrl != null) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.takeIf { it > 0L }
                        ?: (_uiState.value.currentTrack?.durationSeconds?.times(1000L) ?: 0L)
                    if (_uiState.value.positionMs != pos || _uiState.value.durationMs != dur) {
                        _uiState.update { it.copy(positionMs = pos, durationMs = dur) }
                    }
                }
                delay(400L)
            }
        }
    }

    fun playTracks(tracks: List<MusicTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val controller = mediaController
        if (controller == null) {
            _uiState.update { it.copy(errorMessage = "Audio engine initializing, please retry.") }
            return
        }
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val validQueue = tracks.filter { it.streamUrl.isNotBlank() || it.localFilePath != null }
        if (validQueue.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Selected track has no valid stream URL.") }
            return
        }
        activeQueueTracks = validQueue
        lastReportedTrackId = null
        val items = validQueue.map { toMediaItem(it) }
        _uiState.update {
            it.copy(
                currentTrack = validQueue.getOrNull(safeIndex),
                queue = validQueue,
                currentIndex = safeIndex,
                isBuffering = true,
                errorMessage = null
            )
        }
        controller.setMediaItems(items, safeIndex, 0L)
        controller.prepare()
        controller.play()
        val chosen = validQueue.getOrNull(safeIndex)
        if (chosen != null) {
            lastReportedTrackId = chosen.id
            onTrackChanged(chosen)
        }
    }

    fun playSingle(track: MusicTrack) {
        playTracks(listOf(track), 0)
    }

    fun addToQueue(track: MusicTrack) {
        val controller = mediaController ?: return
        if (activeQueueTracks.isEmpty()) {
            playSingle(track)
            return
        }
        activeQueueTracks = activeQueueTracks + track
        controller.addMediaItem(toMediaItem(track))
        _uiState.update { it.copy(queue = activeQueueTracks) }
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index !in activeQueueTracks.indices || activeQueueTracks.size <= 1) return
        activeQueueTracks = activeQueueTracks.toMutableList().apply { removeAt(index) }
        controller.removeMediaItem(index)
        _uiState.update { it.copy(queue = activeQueueTracks, currentIndex = controller.currentMediaItemIndex) }
    }

    fun jumpToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index !in activeQueueTracks.indices) return
        controller.seekToDefaultPosition(index)
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && _uiState.value.currentTrack != null) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3500L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setVolume(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (clamped * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        mediaController?.volume = 1f
        _uiState.update { it.copy(volume = clamped) }
    }

    fun setAudioQuality(quality: AudioQuality) {
        _uiState.update { it.copy(audioQuality = quality) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun readCurrentSystemVolume(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curr = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (curr.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    private fun refreshAudioOutputRoute() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val route = when {
            devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            } -> AudioOutputRoute.BLUETOOTH
            devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            } -> AudioOutputRoute.WIRED_HEADSET
            else -> AudioOutputRoute.SPEAKER
        }
        _uiState.update { it.copy(audioRoute = route) }
    }

    private fun initEqualizer(audioSessionId: Int) {
        runCatching {
            equalizer?.release()
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            val bands = (0 until numBands).map { idx ->
                EqualizerBandUi(
                    index = idx,
                    centerFreqHz = eq.getCenterFreq(idx.toShort()) / 1000,
                    levelMb = eq.getBandLevel(idx.toShort()).toInt()
                )
            }
            _uiState.update {
                it.copy(
                    equalizer = EqualizerUiState(
                        supported = true,
                        enabled = eq.enabled,
                        presets = presets,
                        selectedPreset = eq.currentPreset.toInt().coerceAtLeast(0),
                        minBandLevelMb = range[0].toInt(),
                        maxBandLevelMb = range[1].toInt(),
                        bands = bands
                    )
                )
            }
        }.onFailure { err ->
            Log.w(TAG, "Device equalizer unavailable: ${err.message}")
            _uiState.update { it.copy(equalizer = EqualizerUiState(supported = false)) }
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = enabled
            _uiState.update { it.copy(equalizer = it.equalizer.copy(enabled = enabled)) }
        }
    }

    fun selectEqualizerPreset(index: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.usePreset(index.toShort())
            val updatedBands = (0 until eq.numberOfBands).map { idx ->
                EqualizerBandUi(
                    index = idx,
                    centerFreqHz = eq.getCenterFreq(idx.toShort()) / 1000,
                    levelMb = eq.getBandLevel(idx.toShort()).toInt()
                )
            }
            _uiState.update {
                it.copy(
                    equalizer = it.equalizer.copy(
                        selectedPreset = index,
                        bands = updatedBands
                    )
                )
            }
        }
    }

    fun setEqualizerBand(bandIndex: Int, levelMb: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.setBandLevel(bandIndex.toShort(), levelMb.toShort())
            val updated = _uiState.value.equalizer.bands.map {
                if (it.index == bandIndex) it.copy(levelMb = levelMb) else it
            }
            _uiState.update { it.copy(equalizer = it.equalizer.copy(bands = updated)) }
        }
    }

    private fun toMediaItem(track: MusicTrack): MediaItem {
        val localFile: File? = track.localFilePath?.let { File(it) }?.takeIf { it.exists() }
            ?: downloadManager.getLocalFileForTrack(track.id)
        val uri = if (localFile != null) Uri.fromFile(localFile) else Uri.parse(track.streamUrl)
        val extras = Bundle().apply {
            putString("source", track.source.key)
            putString("genre", track.genre)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumTitle.ifBlank { track.genre })
            .apply {
                if (track.artworkUrl.isNotBlank()) {
                    setArtworkUri(Uri.parse(track.artworkUrl))
                }
            }
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        positionJob?.cancel()
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        runCatching { equalizer?.release() }
        equalizer = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    private companion object {
        const val TAG = "PlaybackController"
    }
}
