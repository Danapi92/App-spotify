package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CloudPlaylistDoc
import com.example.data.model.CloudPlaylistTrackDoc
import com.example.data.model.MusicPlaylistCollection
import com.example.data.model.MusicTrack
import com.example.data.model.UiState
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.MusicCatalogRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val currentUserId: String,
    private val catalogRepo: MusicCatalogRepository,
    private val userRepo: FirestoreUserRepository
) : ViewModel() {

    private val _remoteCollection = MutableStateFlow<MusicPlaylistCollection?>(null)
    val remoteCollection: StateFlow<MusicPlaylistCollection?> = _remoteCollection.asStateFlow()

    private val _remoteTracks = MutableStateFlow<UiState<List<MusicTrack>>>(UiState.Loading)
    val remoteTracks: StateFlow<UiState<List<MusicTrack>>> = _remoteTracks.asStateFlow()

    private val _cloudPlaylist = MutableStateFlow<CloudPlaylistDoc?>(null)
    val cloudPlaylist: StateFlow<CloudPlaylistDoc?> = _cloudPlaylist.asStateFlow()

    private val _cloudTracks = MutableStateFlow<UiState<List<CloudPlaylistTrackDoc>>>(UiState.Loading)
    val cloudTracks: StateFlow<UiState<List<CloudPlaylistTrackDoc>>> = _cloudTracks.asStateFlow()

    fun loadRemoteCollection(collection: MusicPlaylistCollection) {
        _cloudPlaylist.value = null
        _remoteCollection.value = collection
        viewModelScope.launch {
            _remoteTracks.value = UiState.Loading
            if (collection.tracks.isNotEmpty()) {
                _remoteTracks.value = UiState.Success(collection.tracks)
            } else {
                catalogRepo.getCollectionTracks(collection)
                    .onSuccess { _remoteTracks.value = UiState.Success(it) }
                    .onFailure { _remoteTracks.value = UiState.Error(it.localizedMessage ?: "Failed to load tracks") }
            }
        }
    }

    fun observeCloudPlaylist(playlist: CloudPlaylistDoc) {
        _remoteCollection.value = null
        _cloudPlaylist.value = playlist
        viewModelScope.launch {
            _cloudTracks.value = UiState.Loading
            userRepo.observePlaylistTracks(playlist.id, playlist.ownerId, currentUserId)
                .catch { err ->
                    val firestoreError = generateSequence(err) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && err is CancellationException) throw err
                    Log.w("PlaylistDetailVM", "Error observing playlist tracks", firestoreError ?: err)
                    _cloudTracks.value = UiState.Error((firestoreError ?: err).message ?: "Failed to load tracks")
                }
                .collect { tracks ->
                    _cloudTracks.value = UiState.Success(tracks)
                }
        }
    }

    fun toggleCloudVisibility(playlist: CloudPlaylistDoc, onComplete: (String) -> Unit) {
        val nextPublic = playlist.visibility != "public"
        viewModelScope.launch {
            userRepo.updatePlaylistVisibility(playlist.id, nextPublic)
                .onSuccess {
                    _cloudPlaylist.value = playlist.copy(visibility = if (nextPublic) "public" else "private")
                    onComplete(if (nextPublic) "Public" else "Private")
                }
        }
    }

    fun removeCloudTrack(playlistId: String, trackDocId: String) {
        viewModelScope.launch {
            userRepo.removeTrackFromPlaylist(playlistId, trackDocId)
        }
    }

    fun deleteCloudPlaylist(playlistId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            userRepo.deletePlaylist(playlistId).onSuccess { onDeleted() }
        }
    }
}
