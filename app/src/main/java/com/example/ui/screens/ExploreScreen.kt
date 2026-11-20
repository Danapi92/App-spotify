package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudPlaylistDoc
import com.example.data.model.MusicPlaylistCollection
import com.example.data.model.MusicTrack
import com.example.data.model.UiState
import com.example.player.PlaybackUiState
import com.example.ui.components.CollectionCard
import com.example.ui.components.TrackRow
import com.example.ui.localization.LocalStrings

private data class GenreCardSpec(val name: String, val startColor: Color, val endColor: Color)

private val ExploreGenres = listOf(
    GenreCardSpec("Electronic", Color(0xFF7E22CE), Color(0xFF06B6D4)),
    GenreCardSpec("Hip-Hop/Rap", Color(0xFFEA580C), Color(0xFFF59E0B)),
    GenreCardSpec("Pop", Color(0xFFDB2777), Color(0xFFA855F7)),
    GenreCardSpec("Lo-Fi", Color(0xFF0D9488), Color(0xFF3B82F6)),
    GenreCardSpec("Jazz", Color(0xFF4F46E5), Color(0xFFEC4899)),
    GenreCardSpec("Rock", Color(0xFFDC2626), Color(0xFF7C2D12)),
    GenreCardSpec("Acoustic", Color(0xFF059669), Color(0xFF10B981)),
    GenreCardSpec("R&B/Soul", Color(0xFF9333EA), Color(0xFF312E81))
)

@Composable
fun ExploreScreen(
    selectedGenre: String,
    genreTracksState: UiState<List<MusicTrack>>,
    publicPlaylistsState: UiState<List<CloudPlaylistDoc>>,
    favoriteIds: Set<String>,
    playback: PlaybackUiState,
    onSelectGenre: (String) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onOpenCloudPlaylist: (CloudPlaylistDoc) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    onAddToQueue: (MusicTrack) -> Unit
) {
    val strings = LocalStrings.current
    val publicPlaylists = (publicPlaylistsState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("explore_screen"),
        contentPadding = PaddingValues(bottom = 160.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = strings.exploreTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.exploreSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Legal Source Transparency Banner
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings.legalSourceNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Public Community Playlists from Firestore
        if (publicPlaylists.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.publicCommunityPlaylists,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(publicPlaylists, key = { it.id }) { pl ->
                        CollectionCard(
                            collection = MusicPlaylistCollection(
                                id = pl.id,
                                title = pl.name,
                                creatorName = pl.ownerName,
                                artworkUrl = pl.coverUrl,
                                trackCount = pl.trackCount.toInt()
                            ),
                            onClick = { onOpenCloudPlaylist(pl) },
                            onPlayClick = { onOpenCloudPlaylist(pl) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }

        // Genre Grid
        item {
            Text(
                text = strings.musicGenres,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExploreGenres.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { genre ->
                            val isSelected = genre.name == selectedGenre
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(genre.startColor, genre.endColor)))
                                    .clickable { onSelectGenre(genre.name) }
                                    .padding(14.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Text(
                                    text = if (isSelected) "▶ ${genre.name}" else genre.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Active Genre Tracks
        item {
            Text(
                text = selectedGenre,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        when (genreTracksState) {
            is UiState.Loading -> {
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
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = genreTracksState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is UiState.Success -> {
                items(genreTracksState.data, key = { "exp_${selectedGenre}_${it.id}" }) { track ->
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        TrackRow(
                            track = track,
                            isCurrentTrack = playback.currentTrack?.id == track.id,
                            isPlaying = playback.isPlaying,
                            isFavorite = track.id in favoriteIds,
                            onClick = { onPlayTrack(track, genreTracksState.data) },
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
