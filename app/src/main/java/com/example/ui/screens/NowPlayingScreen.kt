package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AudioQuality
import com.example.data.model.MusicTrack
import com.example.data.model.RepeatModeOption
import com.example.data.model.TrackLyrics
import com.example.data.repository.DownloadStatus
import com.example.player.AudioOutputRoute
import com.example.player.PlaybackUiState
import com.example.ui.components.StreamArtwork
import com.example.ui.components.formatMs
import com.example.ui.localization.LocalStrings
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonMint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playback: PlaybackUiState,
    isFavorite: Boolean,
    downloadStatus: DownloadStatus,
    lyrics: TrackLyrics?,
    lyricsLoading: Boolean,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDownloadClick: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    onOpenEqualizer: () -> Unit,
    onCycleQuality: () -> Unit,
    onJumpQueue: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onDismissError: () -> Unit
) {
    BackHandler { onCollapse() }

    val strings = LocalStrings.current
    val track = playback.currentTrack ?: return
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }

    val artworkScale by animateFloatAsState(
        targetValue = if (playback.isPlaying) 1.0f else 0.90f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "artwork_scale"
    )

    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderValue by remember { mutableFloatStateOf(0f) }
    val duration = playback.durationMs.coerceAtLeast(1L)
    val currentFraction = if (isSeeking) {
        seekSliderValue
    } else {
        (playback.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    val routeLabel = when (playback.audioRoute) {
        AudioOutputRoute.BLUETOOTH -> strings.bluetoothConnected
        AudioOutputRoute.WIRED_HEADSET -> strings.wiredHeadsetConnected
        AudioOutputRoute.SPEAKER -> strings.phoneSpeaker
    }
    val routeIcon = when (playback.audioRoute) {
        AudioOutputRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
        AudioOutputRoute.WIRED_HEADSET -> Icons.Rounded.Headphones
        AudioOutputRoute.SPEAKER -> Icons.Rounded.Speaker
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        ElectricViolet.copy(alpha = 0.42f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("now_playing_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("now_playing_collapse")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                        modifier = Modifier.size(32.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = routeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = routeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(onClick = onOpenEqualizer) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = strings.equalizer
                    )
                }
            }

            if (playback.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = playback.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissError, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Album Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .aspectRatio(1f)
                    .scale(artworkScale)
                    .shadow(
                        elevation = if (playback.isPlaying) 24.dp else 8.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = ElectricViolet,
                        spotColor = NeonMint
                    )
            ) {
                StreamArtwork(
                    url = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 28.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title, Artist & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${track.source.displayName} • ${track.genre}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp)
                ) {
                    val tint by animateColorAsState(
                        if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "fav_tint"
                    )
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = tint,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seekbar
            Slider(
                value = currentFraction,
                onValueChange = {
                    isSeeking = true
                    seekSliderValue = it
                },
                onValueChangeFinished = {
                    isSeeking = false
                    onSeekTo((seekSliderValue * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(if (isSeeking) (seekSliderValue * duration).toLong() else playback.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatMs(playback.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playback.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(38.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("now_playing_play_pause")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playback.isBuffering) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onSkipNext, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(onClick = onCycleRepeat) {
                    val icon = when (playback.repeatMode) {
                        RepeatModeOption.ONE -> Icons.Rounded.RepeatOne
                        else -> Icons.Rounded.Repeat
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat mode",
                        tint = if (playback.repeatMode != RepeatModeOption.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Volume Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = strings.volumeControl,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = playback.volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Chips Row: Lyrics, Queue, Download, Quality, Add to Playlist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionIconPill(
                    icon = Icons.Rounded.Lyrics,
                    label = strings.lyricsTitle,
                    onClick = { showLyricsSheet = true }
                )
                ActionIconPill(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "${strings.queueTitle} (${playback.queue.size})",
                    onClick = { showQueueSheet = true }
                )
                ActionIconPill(
                    icon = Icons.Rounded.HighQuality,
                    label = playback.audioQuality.bitrateLabel,
                    onClick = onCycleQuality
                )
                ActionIconPill(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = "Playlist",
                    onClick = { onAddToPlaylist(track) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Offline Download Pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = track.downloadable && downloadStatus !is DownloadStatus.Downloading) {
                        onDownloadClick(track)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val icon = when (downloadStatus) {
                        is DownloadStatus.Downloaded -> Icons.Rounded.DownloadDone
                        else -> Icons.Rounded.Download
                    }
                    val label = when {
                        downloadStatus is DownloadStatus.Downloaded -> strings.downloadedLabel
                        downloadStatus is DownloadStatus.Downloading -> "${strings.downloading} ${(downloadStatus.progress * 100).toInt()}%"
                        track.downloadable -> strings.offlineDownload
                        else -> strings.downloadNotPermitted
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (track.downloadable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (track.downloadable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Queue Modal Sheet
    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = strings.queueTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(playback.queue) { idx, item ->
                        val active = idx == playback.currentIndex
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onJumpQueue(idx) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StreamArtwork(
                                    url = item.artworkUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(44.dp),
                                    cornerRadius = 8.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.artistName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (!active && playback.queue.size > 1) {
                                    IconButton(onClick = { onRemoveFromQueue(idx) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove from queue")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Lyrics Modal Sheet
    if (showLyricsSheet) {
        ModalBottomSheet(onDismissRequest = { showLyricsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "${strings.lyricsTitle} — ${track.title}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    lyricsLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(strings.lyricsLoading)
                            }
                        }
                    }
                    lyrics == null || !lyrics.available -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.lyricsUnavailable,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    lyrics.syncedLines.isNotEmpty() -> {
                        val activeIdx = lyrics.syncedLines.indexOfLast { it.timeMs <= playback.positionMs }
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 36.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(lyrics.syncedLines) { idx, line ->
                                val isCurrent = idx == activeIdx
                                Text(
                                    text = line.text,
                                    style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSeekTo(line.timeMs) }
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(contentPadding = PaddingValues(bottom = 36.dp)) {
                            item {
                                Text(
                                    text = lyrics.plainLyrics,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
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
private fun ActionIconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}
