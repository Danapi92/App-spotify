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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MusicPlaylistCollection
import com.example.data.model.MusicTrack
import com.example.data.model.SavedTrackDoc
import com.example.data.model.UiState
import com.example.data.model.UserProfileDoc
import com.example.data.repository.HomeCatalogBundle
import com.example.player.PlaybackUiState
import com.example.ui.components.CollectionCard
import com.example.ui.components.StreamArtwork
import com.example.ui.components.StreamMusicBrandLogo
import com.example.ui.components.TrackRow
import com.example.ui.localization.LocalStrings
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonMint
import java.util.Calendar

private val HomeGenres = listOf(
    "Electronic", "Pop", "Hip-Hop/Rap", "Lo-Fi", "Jazz", "Rock", "Acoustic", "R&B/Soul"
)

@Composable
fun HomeScreen(
    userProfile: UiState<UserProfileDoc?>,
    homeCatalog: UiState<HomeCatalogBundle>,
    historyState: UiState<List<SavedTrackDoc>>,
    historyRecommendations: List<MusicTrack>,
    selectedGenre: String,
    genreTracksState: UiState<List<MusicTrack>>,
    favoriteIds: Set<String>,
    playback: PlaybackUiState,
    onRetryCatalog: () -> Unit,
    onSelectGenre: (String) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onOpenCollection: (MusicPlaylistCollection) -> Unit,
    onPlayCollection: (MusicPlaylistCollection, Boolean) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    onAddToQueue: (MusicTrack) -> Unit,
    onOpenSettings: () -> Unit
) {
    val strings = LocalStrings.current
    val greeting = remember(strings.languageCode) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> strings.greetingMorning
            in 12..14 -> strings.greetingAfternoon
            in 15..18 -> strings.greetingEvening
            else -> strings.greetingNight
        }
    }
    val displayName = (userProfile as? UiState.Success)?.data?.displayName?.split(" ")?.firstOrNull()
        ?: "Listener"
    val recentTracks = remember(historyState) {
        (historyState as? UiState.Success)?.data?.take(6)?.map { it.toMusicTrack() }.orEmpty()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 160.dp)
    ) {
        // Brand Logo + Greeting Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StreamMusicBrandLogo()
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = strings.settingsTitle,
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "$greeting, $displayName",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.legalSourceNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Recently Played Quick Grid
        if (recentTracks.isNotEmpty()) {
            item {
                SectionHeader(title = strings.recentlyPlayed)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentTracks.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { track ->
                                QuickRecentTile(
                                    track = track,
                                    onClick = { onPlayTrack(track, recentTracks) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(22.dp))
            }
        }

        when (homeCatalog) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = strings.loadingCatalog,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = strings.networkError,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = homeCatalog.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = onRetryCatalog) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.retry)
                            }
                        }
                    }
                }
            }

            is UiState.Success -> {
                val bundle = homeCatalog.data

                // Hero Highlight Banner
                if (bundle.trendingTracks.isNotEmpty()) {
                    val spotlight = bundle.trendingTracks.first()
                    item {
                        HeroSpotlightCard(
                            track = spotlight,
                            onPlayAll = { onPlayTrack(spotlight, bundle.trendingTracks) },
                            onShuffleAll = {
                                val shuffled = bundle.trendingTracks.shuffled()
                                onPlayTrack(shuffled.first(), shuffled)
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Recommended Playlists
                if (bundle.recommendedPlaylists.isNotEmpty()) {
                    item {
                        SectionHeader(title = strings.recommendedPlaylists)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(bundle.recommendedPlaylists, key = { it.id }) { pl ->
                                CollectionCard(
                                    collection = pl,
                                    onClick = { onOpenCollection(pl) },
                                    onPlayClick = { onPlayCollection(pl, false) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Trending Music
                if (bundle.trendingTracks.isNotEmpty()) {
                    item {
                        SectionHeader(title = strings.trendingMusic)
                    }
                    items(bundle.trendingTracks.take(6), key = { "trend_${it.id}" }) { track ->
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            TrackRow(
                                track = track,
                                isCurrentTrack = playback.currentTrack?.id == track.id,
                                isPlaying = playback.isPlaying,
                                isFavorite = track.id in favoriteIds,
                                onClick = { onPlayTrack(track, bundle.trendingTracks) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onAddToQueue = { onAddToQueue(track) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }

                // Featured Albums
                if (bundle.featuredAlbums.isNotEmpty()) {
                    item {
                        SectionHeader(title = strings.featuredAlbums)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(bundle.featuredAlbums, key = { it.id }) { album ->
                                CollectionCard(
                                    collection = album,
                                    onClick = { onOpenCollection(album) },
                                    onPlayClick = { onPlayCollection(album, false) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Latest Tracks
                if (bundle.latestTracks.isNotEmpty()) {
                    item {
                        SectionHeader(title = strings.latestTracks)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(bundle.latestTracks, key = { "latest_${it.id}" }) { track ->
                                LatestTrackSquareCard(
                                    track = track,
                                    onClick = { onPlayTrack(track, bundle.latestTracks) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // History-Based Recommendations
                if (historyRecommendations.isNotEmpty()) {
                    item {
                        SectionHeader(title = strings.basedOnHistory)
                    }
                    items(historyRecommendations.take(5), key = { "rec_${it.id}" }) { track ->
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            TrackRow(
                                track = track,
                                isCurrentTrack = playback.currentTrack?.id == track.id,
                                isPlaying = playback.isPlaying,
                                isFavorite = track.id in favoriteIds,
                                onClick = { onPlayTrack(track, historyRecommendations) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onAddToQueue = { onAddToQueue(track) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }

                // Music Genres & Interactive Tracks
                item {
                    SectionHeader(title = strings.musicGenres)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(HomeGenres) { genre ->
                            val selected = genre == selectedGenre
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectGenre(genre) },
                                label = { Text(genre) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                when (genreTracksState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            Text(
                                text = genreTracksState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is UiState.Success -> {
                        items(genreTracksState.data.take(6), key = { "genre_${selectedGenre}_${it.id}" }) { track ->
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
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
    )
}

@Composable
private fun QuickRecentTile(
    track: MusicTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreamArtwork(
                url = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.size(58.dp),
                cornerRadius = 0.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun HeroSpotlightCard(
    track: MusicTrack,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            ElectricViolet.copy(alpha = 0.36f),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            NeonMint.copy(alpha = 0.20f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreamArtwork(
                    url = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.size(96.dp),
                    cornerRadius = 16.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#1 TRENDING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onPlayAll)
                                .testTag("hero_play_all")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.playAll,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onShuffleAll)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = strings.shufflePlay,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestTrackSquareCard(
    track: MusicTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        StreamArtwork(
            url = track.artworkUrl,
            contentDescription = track.title,
            modifier = Modifier.size(136.dp),
            cornerRadius = 14.dp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
