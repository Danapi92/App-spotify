package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AudioQuality
import com.example.data.model.CloudPlaylistDoc
import com.example.data.model.MusicPlaylistCollection
import com.example.data.model.MusicTrack
import com.example.data.model.SavedTrackDoc
import com.example.data.model.TrackLyrics
import com.example.data.model.UiState
import com.example.data.model.UserDeviceDoc
import com.example.data.model.UserProfileDoc
import com.example.data.repository.AppPreferences
import com.example.data.repository.DownloadStatus
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.HomeCatalogBundle
import com.example.data.repository.MusicCatalogRepository
import com.example.data.repository.OfflineDownloadManager
import com.example.data.repository.SettingsRepository
import com.example.player.PlaybackController
import com.example.player.PlaybackUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StreamMainViewModel(
    private val currentUserId: String,
    private val userRepo: FirestoreUserRepository,
    private val catalogRepo: MusicCatalogRepository,
    private val settingsRepo: SettingsRepository,
    private val downloadManager: OfflineDownloadManager,
    val playbackController: PlaybackController
) : ViewModel() {

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    val preferences: StateFlow<AppPreferences> = settingsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppPreferences())

    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState

    val downloadedTracks: StateFlow<List<MusicTrack>> = downloadManager.downloadedTracks
    val downloadStates: StateFlow<Map<String, DownloadStatus>> = downloadManager.downloadStates

    val userProfile: StateFlow<UiState<UserProfileDoc?>> = userRepo.observeUserProfile(currentUserId)
        .map<UserProfileDoc?, UiState<UserProfileDoc?>> { UiState.Success(it) }
        .catch { err -> emitUnwrappedError(err, "profile") { emit(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val favorites: StateFlow<UiState<List<SavedTrackDoc>>> = userRepo.observeFavorites(currentUserId)
        .map<List<SavedTrackDoc>, UiState<List<SavedTrackDoc>>> { UiState.Success(it) }
        .catch { err -> emitUnwrappedError(err, "favorites") { emit(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val history: StateFlow<UiState<List<SavedTrackDoc>>> = userRepo.observeHistory(currentUserId)
        .map<List<SavedTrackDoc>, UiState<List<SavedTrackDoc>>> { UiState.Success(it) }
        .catch { err -> emitUnwrappedError(err, "history") { emit(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myPlaylists: StateFlow<UiState<List<CloudPlaylistDoc>>> = userRepo.observeMyPlaylists(currentUserId)
        .map<List<CloudPlaylistDoc>, UiState<List<CloudPlaylistDoc>>> { UiState.Success(it) }
        .catch { err -> emitUnwrappedError(err, "myPlaylists") { emit(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val publicPlaylists: StateFlow<UiState<List<CloudPlaylistDoc>>> = userRepo.observePublicPlaylists()
        .map<List<CloudPlaylistDoc>, UiState<List<CloudPlaylistDoc>>> { UiState.Success(it) }
        .catch { err -> emitUnwrappedError(err, "publicPlaylists") { emit(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val devices: StateFlow<UiState<List<UserDeviceDoc>>> = userRepo.observeDevices(currentUserId)
        .map<List<UserDeviceDoc>, UiState<List<UserDeviceDoc>>> { UiState.Success(it) }
        .catch { err -> emitUnwrappedError(err, "devices") { emit(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _homeCatalog = MutableStateFlow<UiState<HomeCatalogBundle>>(UiState.Loading)
    val homeCatalog: StateFlow<UiState<HomeCatalogBundle>> = _homeCatalog.asStateFlow()

    private val _selectedGenre = MutableStateFlow("Electronic")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _genreTracks = MutableStateFlow<UiState<List<MusicTrack>>>(UiState.Loading)
    val genreTracks: StateFlow<UiState<List<MusicTrack>>> = _genreTracks.asStateFlow()

    private val _historyRecommendations = MutableStateFlow<List<MusicTrack>>(emptyList())
    val historyRecommendations: StateFlow<List<MusicTrack>> = _historyRecommendations.asStateFlow()

    private val _activeLyrics = MutableStateFlow<TrackLyrics?>(null)
    val activeLyrics: StateFlow<TrackLyrics?> = _activeLyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                userRepo.ensureUserProfile(user)
                userRepo.registerCurrentDevice(currentUserId)
            }
        }
        refreshHomeCatalog()
        selectGenre("Electronic")
        viewModelScope.launch {
            history.collect { state ->
                if (state is UiState.Success && state.data.isNotEmpty()) {
                    val tracks = state.data.map { it.toMusicTrack() }
                    catalogRepo.getRecommendationsFromHistory(tracks).onSuccess { recs ->
                        _historyRecommendations.value = recs
                    }
                }
            }
        }
    }

    fun refreshHomeCatalog() {
        viewModelScope.launch {
            _homeCatalog.value = UiState.Loading
            catalogRepo.loadHomeCatalog()
                .onSuccess { bundle -> _homeCatalog.value = UiState.Success(bundle) }
                .onFailure { err ->
                    _homeCatalog.value = UiState.Error(err.localizedMessage ?: "Failed to load catalog")
                }
        }
    }

    fun selectGenre(genre: String) {
        _selectedGenre.value = genre
        viewModelScope.launch {
            _genreTracks.value = UiState.Loading
            catalogRepo.getTracksByGenre(genre)
                .onSuccess { list -> _genreTracks.value = UiState.Success(list) }
                .onFailure { err ->
                    _genreTracks.value = UiState.Error(err.localizedMessage ?: "Failed to load genre")
                }
        }
    }

    fun playTrack(track: MusicTrack, queue: List<MusicTrack> = listOf(track)) {
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackController.playTracks(queue, index)
    }

    fun playPlaylistCollection(collection: MusicPlaylistCollection, shuffle: Boolean = false) {
        viewModelScope.launch {
            val loaded = if (collection.tracks.isNotEmpty()) {
                Result.success(collection.tracks)
            } else {
                catalogRepo.getCollectionTracks(collection)
            }
            loaded.onSuccess { tracks ->
                if (tracks.isEmpty()) {
                    _toastEvents.tryEmit("No playable tracks found in ${collection.title}")
                } else {
                    val finalQueue = if (shuffle) tracks.shuffled() else tracks
                    playbackController.playTracks(finalQueue, 0)
                }
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to load playlist")
            }
        }
    }

    fun onActiveTrackChanged(track: MusicTrack) {
        viewModelScope.launch {
            userRepo.recordPlayHistory(currentUserId, track)
        }
        loadLyricsForTrack(track)
    }

    fun loadLyricsForTrack(track: MusicTrack) {
        viewModelScope.launch {
            _lyricsLoading.value = true
            _activeLyrics.value = null
            val lyrics = catalogRepo.getLyrics(track.artistName, track.title)
            _activeLyrics.value = lyrics
            _lyricsLoading.value = false
        }
    }

    fun toggleFavorite(track: MusicTrack) {
        val favList = (favorites.value as? UiState.Success)?.data.orEmpty()
        val isFav = favList.any { it.trackId == track.id }
        viewModelScope.launch {
            userRepo.toggleFavorite(currentUserId, track, isFav)
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to update favorites") }
        }
    }

    fun createPlaylist(name: String, description: String, isPublic: Boolean) {
        val ownerName = (userProfile.value as? UiState.Success)?.data?.displayName
            ?: Firebase.auth.currentUser?.displayName
            ?: "Stream Listener"
        viewModelScope.launch {
            userRepo.createPlaylist(currentUserId, ownerName, name, description, isPublic)
                .onSuccess { _toastEvents.tryEmit("Playlist \"$name\" created") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to create playlist") }
        }
    }

    fun addTrackToPlaylist(playlist: CloudPlaylistDoc, track: MusicTrack) {
        viewModelScope.launch {
            userRepo.addTrackToPlaylist(playlist, track)
                .onSuccess { _toastEvents.tryEmit("Added to ${playlist.name}") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to add track") }
        }
    }

    fun downloadTrack(track: MusicTrack) {
        viewModelScope.launch {
            downloadManager.downloadTrack(track)
                .onSuccess { _toastEvents.tryEmit("Downloaded ${track.title}") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Download unavailable") }
        }
    }

    fun deleteDownload(trackId: String) {
        downloadManager.deleteDownload(trackId)
        _toastEvents.tryEmit("Removed offline file")
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setDarkTheme(enabled)
            userRepo.updateCloudPreferences(currentUserId, theme = if (enabled) "dark" else "light")
        }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch {
            settingsRepo.setLanguage(code)
            userRepo.updateCloudPreferences(currentUserId, language = code)
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch {
            settingsRepo.setAudioQuality(quality)
            playbackController.setAudioQuality(quality)
            userRepo.updateCloudPreferences(currentUserId, audioQuality = quality)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setNotificationsEnabled(enabled)
            userRepo.updateCloudPreferences(currentUserId, notificationsEnabled = enabled)
        }
    }

    fun togglePremiumTier(enablePremium: Boolean) {
        viewModelScope.launch {
            val tier = if (enablePremium) "premium" else "free"
            userRepo.updateCloudPreferences(currentUserId, subscriptionTier = tier)
                .onSuccess {
                    _toastEvents.tryEmit(
                        if (enablePremium) "STREAM PREMIUM active" else "Switched to Free plan"
                    )
                }
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            userRepo.updateDisplayName(currentUserId, newName)
                .onSuccess { _toastEvents.tryEmit("Profile name updated") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Update failed") }
        }
    }

    fun updateProfilePhoto(photoUriString: String) {
        viewModelScope.launch {
            userRepo.updateCloudPreferences(currentUserId, photoUrl = photoUriString)
                .onSuccess { _toastEvents.tryEmit("Profile photo updated") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Photo update failed") }
        }
    }

    fun registerCurrentDevice() {
        viewModelScope.launch {
            userRepo.registerCurrentDevice(currentUserId)
                .onSuccess { _toastEvents.tryEmit("Device synced") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Device sync failed") }
        }
    }

    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            userRepo.removeDevice(currentUserId, deviceId)
                .onSuccess { _toastEvents.tryEmit("Device removed") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to remove device") }
        }
    }

    private suspend inline fun <T> emitUnwrappedError(
        error: Throwable,
        tagSuffix: String,
        emitFn: (UiState<T>) -> Unit
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w("StreamMainVM_$tagSuffix", "Error observing stream", rootError)
        emitFn(UiState.Error(rootError.message ?: "Failed to sync $tagSuffix"))
    }

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }
}
