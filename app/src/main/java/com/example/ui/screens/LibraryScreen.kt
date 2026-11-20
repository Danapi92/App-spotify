package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudPlaylistDoc
import com.example.data.model.MusicTrack
import com.example.data.model.SavedTrackDoc
import com.example.data.model.UiState
import com.example.player.PlaybackUiState
import com.example.ui.components.StreamArtwork
import com.example.ui.components.TrackRow
import com.example.ui.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    favoritesState: UiState<List<SavedTrackDoc>>,
    playlistsState: UiState<List<CloudPlaylistDoc>>,
    downloadedTracks: List<MusicTrack>,
    historyState: UiState<List<SavedTrackDoc>>,
    favoriteIds: Set<String>,
    playback: PlaybackUiState,
    onCreatePlaylistClick: () -> Unit,
    onOpenCloudPlaylist: (CloudPlaylistDoc) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    onAddToQueue: (MusicTrack) -> Unit,
    onDeleteDownload: (String) -> Unit
) {
    val strings = LocalStrings.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(strings.favoritesTab, strings.playlistsTab, strings.downloadsTab, strings.historyTab)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.libraryTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(
                onClick = onCreatePlaylistClick,
                modifier = Modifier.testTag("library_create_playlist_btn")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.createPlaylist)
            }
        }

        SecondaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // Favorites
                when (favoritesState) {
                    is UiState.Loading -> LoadingBox()
                    is UiState.Error -> ErrorBox(favoritesState.message)
                    is UiState.Success -> {
                        val tracks = favoritesState.data.map { it.toMusicTrack() }
                        if (tracks.isEmpty()) {
                            EmptyLibraryState(
                                icon = Icons.Rounded.Favorite,
                                message = strings.noFavoritesYet
                            )
                        } else {
                            LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(onClick = { onPlayTrack(tracks.first(), tracks) }) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(strings.playAll)
                                        }
                                        FilledTonalButton(onClick = {
                                            val shuffled = tracks.shuffled()
                                            onPlayTrack(shuffled.first(), shuffled)
                                        }) {
                                            Text(strings.shufflePlay)
                                        }
                                    }
                                }
                                items(tracks, key = { "fav_${it.id}" }) { track ->
                                    TrackRow(
                                        track = track,
                                        isCurrentTrack = playback.currentTrack?.id == track.id,
                                        isPlaying = playback.isPlaying,
                                        isFavorite = true,
                                        onClick = { onPlayTrack(track, tracks) },
                                        onToggleFavorite = { onToggleFavorite(track) },
                                        onAddToPlaylist = { onAddToPlaylist(track) },
                                        onAddToQueue = { onAddToQueue(track) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Cloud Playlists
                when (playlistsState) {
                    is UiState.Loading -> LoadingBox()
                    is UiState.Error -> ErrorBox(playlistsState.message)
                    is UiState.Success -> {
                        if (playlistsState.data.isEmpty()) {
                            EmptyLibraryState(
                                icon = Icons.Rounded.QueueMusic,
                                message = strings.noPlaylistsYet
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 160.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(playlistsState.data, key = { it.id }) { pl ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onOpenCloudPlaylist(pl) }
                                            .testTag("playlist_card_${pl.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            StreamArtwork(
                                                url = pl.coverUrl,
                                                contentDescription = pl.name,
                                                modifier = Modifier.size(60.dp),
                                                cornerRadius = 12.dp
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(pl.name, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    text = "${pl.trackCount} ${strings.songsCountSuffix}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = if (pl.visibility == "public") Icons.Rounded.Public else Icons.Rounded.Lock,
                                                contentDescription = pl.visibility,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Offline Downloads
                if (downloadedTracks.isEmpty()) {
                    EmptyLibraryState(
                        icon = Icons.Rounded.DownloadDone,
                        message = strings.noDownloadsYet
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                        items(downloadedTracks, key = { "dl_${it.id}" }) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TrackRow(
                                        track = track,
                                        isCurrentTrack = playback.currentTrack?.id == track.id,
                                        isPlaying = playback.isPlaying,
                                        isFavorite = track.id in favoriteIds,
                                        onClick = { onPlayTrack(track, downloadedTracks) },
                                        onToggleFavorite = { onToggleFavorite(track) },
                                        onAddToPlaylist = { onAddToPlaylist(track) },
                                        onAddToQueue = { onAddToQueue(track) }
                                    )
                                }
                                IconButton(onClick = { onDeleteDownload(track.id) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete offline download",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // Listening History
                when (historyState) {
                    is UiState.Loading -> LoadingBox()
                    is UiState.Error -> ErrorBox(historyState.message)
                    is UiState.Success -> {
                        val tracks = historyState.data.map { it.toMusicTrack() }
                        if (tracks.isEmpty()) {
                            EmptyLibraryState(
                                icon = Icons.Rounded.History,
                                message = strings.noHistoryYet
                            )
                        } else {
                            LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                                items(tracks, key = { "hist_${it.id}" }) { track ->
                                    TrackRow(
                                        track = track,
                                        isCurrentTrack = playback.currentTrack?.id == track.id,
                                        isPlaying = playback.isPlaying,
                                        isFavorite = track.id in favoriteIds,
                                        onClick = { onPlayTrack(track, tracks) },
                                        onToggleFavorite = { onToggleFavorite(track) },
                                        onAddToPlaylist = { onAddToPlaylist(track) },
                                        onAddToQueue = { onAddToQueue(track) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyLibraryState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
