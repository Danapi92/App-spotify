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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudPlaylistDoc
import com.example.data.model.MusicPlaylistCollection
import com.example.data.model.MusicTrack
import com.example.data.model.SearchHistoryDoc
import com.example.data.model.UiState
import com.example.data.repository.SearchResultsBundle
import com.example.player.PlaybackUiState
import com.example.ui.components.ArtistBubbleCard
import com.example.ui.components.CollectionCard
import com.example.ui.components.TrackRow
import com.example.ui.localization.LocalStrings
import com.example.ui.viewmodel.SearchFilter

@Composable
fun SearchScreen(
    query: String,
    selectedFilter: SearchFilter,
    resultsState: UiState<SearchResultsBundle>,
    searchHistoryState: UiState<List<SearchHistoryDoc>>,
    publicPlaylists: List<CloudPlaylistDoc>,
    favoriteIds: Set<String>,
    playback: PlaybackUiState,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onSelectHistory: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onOpenCollection: (MusicPlaylistCollection) -> Unit,
    onPlayCollection: (MusicPlaylistCollection) -> Unit,
    onOpenCloudPlaylist: (CloudPlaylistDoc) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    onAddToQueue: (MusicTrack) -> Unit
) {
    val strings = LocalStrings.current
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = strings.navSearch,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(strings.searchPlaceholder) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSearchSubmit()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input")
            )
        }

        // Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                SearchFilter.ALL to strings.filterAll,
                SearchFilter.SONGS to strings.filterSongs,
                SearchFilter.ARTISTS to strings.filterArtists,
                SearchFilter.ALBUMS to strings.filterAlbums,
                SearchFilter.PLAYLISTS to strings.filterPlaylists
            )
            items(filters) { (filter, label) ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (query.trim().isEmpty()) {
            val historyList = (searchHistoryState as? UiState.Success)?.data.orEmpty()
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (historyList.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.searchHistory,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onClearHistory) {
                                Text(strings.clearAll)
                            }
                        }
                    }
                    items(historyList, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectHistory(item.query) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item.query,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteHistory(item.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Delete history item",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.searchPlaceholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            when (resultsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Text(
                        text = resultsState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                is UiState.Success -> {
                    val bundle = resultsState.data
                    val matchingCloudPlaylists = publicPlaylists.filter {
                        it.name.contains(query.trim(), ignoreCase = true) ||
                            it.ownerName.contains(query.trim(), ignoreCase = true)
                    }
                    val showSongs = selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.SONGS
                    val showArtists = selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.ARTISTS
                    val showAlbums = selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.ALBUMS
                    val showPlaylists = selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.PLAYLISTS

                    val hasAny = (showSongs && bundle.songs.isNotEmpty()) ||
                        (showArtists && bundle.artists.isNotEmpty()) ||
                        (showAlbums && bundle.albums.isNotEmpty()) ||
                        (showPlaylists && (bundle.playlists.isNotEmpty() || matchingCloudPlaylists.isNotEmpty()))

                    if (!hasAny) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.noResults,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (showArtists && bundle.artists.isNotEmpty()) {
                                item {
                                    Text(
                                        text = strings.filterArtists,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(bundle.artists, key = { it.id }) { artist ->
                                            ArtistBubbleCard(
                                                artist = artist,
                                                onClick = { onSelectHistory(artist.name) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (showAlbums && bundle.albums.isNotEmpty()) {
                                item {
                                    Text(
                                        text = strings.filterAlbums,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(bundle.albums, key = { it.id }) { album ->
                                            CollectionCard(
                                                collection = album,
                                                onClick = { onOpenCollection(album) },
                                                onPlayClick = { onPlayCollection(album) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (showPlaylists && (bundle.playlists.isNotEmpty() || matchingCloudPlaylists.isNotEmpty())) {
                                item {
                                    Text(
                                        text = strings.filterPlaylists,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(matchingCloudPlaylists, key = { "cloud_${it.id}" }) { cpl ->
                                            CollectionCard(
                                                collection = MusicPlaylistCollection(
                                                    id = cpl.id,
                                                    title = cpl.name,
                                                    creatorName = cpl.ownerName,
                                                    artworkUrl = cpl.coverUrl,
                                                    trackCount = cpl.trackCount.toInt()
                                                ),
                                                onClick = { onOpenCloudPlaylist(cpl) },
                                                onPlayClick = { onOpenCloudPlaylist(cpl) }
                                            )
                                        }
                                        items(bundle.playlists, key = { it.id }) { pl ->
                                            CollectionCard(
                                                collection = pl,
                                                onClick = { onOpenCollection(pl) },
                                                onPlayClick = { onPlayCollection(pl) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (showSongs && bundle.songs.isNotEmpty()) {
                                item {
                                    Text(
                                        text = strings.filterSongs,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                                items(bundle.songs, key = { "search_song_${it.id}" }) { track ->
                                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                        TrackRow(
                                            track = track,
                                            isCurrentTrack = playback.currentTrack?.id == track.id,
                                            isPlaying = playback.isPlaying,
                                            isFavorite = track.id in favoriteIds,
                                            onClick = { onPlayTrack(track, bundle.songs) },
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
}
