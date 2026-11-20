package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudPlaylistDoc
import com.example.data.model.CloudPlaylistTrackDoc
import com.example.data.model.MusicPlaylistCollection
import com.example.data.model.MusicTrack
import com.example.data.model.UiState
import com.example.player.PlaybackUiState
import com.example.ui.components.StreamArtwork
import com.example.ui.components.TrackRow
import com.example.ui.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    currentUserId: String,
    remoteCollection: MusicPlaylistCollection?,
    remoteTracksState: UiState<List<MusicTrack>>,
    cloudPlaylist: CloudPlaylistDoc?,
    cloudTracksState: UiState<List<CloudPlaylistTrackDoc>>,
    favoriteIds: Set<String>,
    playback: PlaybackUiState,
    onBack: () -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    onAddToQueue: (MusicTrack) -> Unit,
    onToggleVisibility: (CloudPlaylistDoc) -> Unit,
    onRemoveCloudTrack: (playlistId: String, trackDocId: String) -> Unit,
    onDeleteCloudPlaylist: (playlistId: String) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalStrings.current

    val title = cloudPlaylist?.name ?: remoteCollection?.title ?: "Playlist"
    val creator = cloudPlaylist?.ownerName ?: remoteCollection?.creatorName ?: ""
    val cover = cloudPlaylist?.coverUrl?.ifBlank { null } ?: remoteCollection?.artworkUrl.orEmpty()
    val isOwner = cloudPlaylist != null && cloudPlaylist.ownerId == currentUserId

    val tracks: List<MusicTrack> = when {
        cloudPlaylist != null -> (cloudTracksState as? UiState.Success)?.data?.map { it.toMusicTrack() }.orEmpty()
        else -> (remoteTracksState as? UiState.Success)?.data.orEmpty()
    }
    val cloudTrackDocs = (cloudTracksState as? UiState.Success)?.data.orEmpty()

    val isLoading = if (cloudPlaylist != null) {
        cloudTracksState is UiState.Loading
    } else {
        remoteTracksState is UiState.Loading
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = {
                    val trackListText = tracks.take(8).joinToString("\n") { "• ${it.title} — ${it.artistName}" }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "$title — STREAM MUSIC")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Listen to \"$title\" by $creator on STREAM MUSIC:\n$trackListText"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, strings.sharePlaylist))
                }) {
                    Icon(Icons.Rounded.Share, contentDescription = strings.sharePlaylist)
                }
                if (isOwner && cloudPlaylist != null) {
                    IconButton(onClick = { onToggleVisibility(cloudPlaylist) }) {
                        Icon(
                            imageVector = if (cloudPlaylist.visibility == "public") Icons.Rounded.Public else Icons.Rounded.Lock,
                            contentDescription = "Toggle visibility"
                        )
                    }
                    IconButton(onClick = { onDeleteCloudPlaylist(cloudPlaylist.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = strings.deletePlaylist,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StreamArtwork(
                        url = cover,
                        contentDescription = title,
                        modifier = Modifier.size(180.dp),
                        cornerRadius = 20.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (creator.isNotBlank()) {
                        Text(
                            text = creator,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (cloudPlaylist != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (cloudPlaylist.visibility == "public") strings.publicPlaylist else strings.privatePlaylist,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { if (tracks.isNotEmpty()) onPlayTrack(tracks.first(), tracks) },
                            enabled = tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.playAll)
                        }
                        FilledTonalButton(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val shuffled = tracks.shuffled()
                                    onPlayTrack(shuffled.first(), shuffled)
                                }
                            },
                            enabled = tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.shufflePlay)
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.noResults,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(tracks.indices.toList(), key = { idx -> "${tracks[idx].id}_$idx" }) { idx ->
                    val track = tracks[idx]
                    val docId = cloudTrackDocs.getOrNull(idx)?.id
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        TrackRow(
                            track = track,
                            isCurrentTrack = playback.currentTrack?.id == track.id,
                            isPlaying = playback.isPlaying,
                            isFavorite = track.id in favoriteIds,
                            onClick = { onPlayTrack(track, tracks) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            extraMenuActionLabel = if (isOwner && docId != null) strings.removeTrack else null,
                            onExtraMenuAction = if (isOwner && docId != null && cloudPlaylist != null) {
                                { onRemoveCloudTrack(cloudPlaylist.id, docId) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}
