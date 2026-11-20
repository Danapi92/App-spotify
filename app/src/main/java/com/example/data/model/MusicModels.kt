package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AudioQuality(val key: String, val bitrateLabel: String) {
    AUTO("auto", "Adaptive"),
    STANDARD("standard", "128 kbps"),
    HIGH("high", "320 kbps");

    companion object {
        fun fromKey(raw: String?): AudioQuality =
            entries.firstOrNull { it.key == raw } ?: AUTO
    }
}

enum class MusicSource(val key: String, val displayName: String) {
    AUDIUS("audius", "Audius Legal Stream"),
    DEEZER("deezer", "Deezer Legal Preview"),
    CATALOG("catalog", "Stream Music Catalog");

    companion object {
        fun fromKey(raw: String?): MusicSource =
            entries.firstOrNull { it.key == raw } ?: AUDIUS
    }
}

enum class RepeatModeOption {
    OFF, ALL, ONE
}

data class MusicTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val artistId: String = "",
    val albumTitle: String = "",
    val artworkUrl: String = "",
    val streamUrl: String,
    val downloadUrl: String = "",
    val durationSeconds: Int = 0,
    val genre: String = "Electronic",
    val playCount: Long = 0L,
    val downloadable: Boolean = false,
    val source: MusicSource = MusicSource.AUDIUS,
    val licenseNote: String = "Licensed Streaming",
    val localFilePath: String? = null
)

data class MusicPlaylistCollection(
    val id: String,
    val title: String,
    val creatorName: String,
    val description: String = "",
    val artworkUrl: String = "",
    val trackCount: Int = 0,
    val isAlbum: Boolean = false,
    val source: MusicSource = MusicSource.AUDIUS,
    val tracks: List<MusicTrack> = emptyList()
)

data class MusicArtist(
    val id: String,
    val name: String,
    val handle: String = "",
    val avatarUrl: String = "",
    val followerCount: Long = 0L,
    val trackCount: Int = 0
)

data class LyricsLine(
    val timeMs: Long,
    val text: String
)

data class TrackLyrics(
    val plainLyrics: String = "",
    val syncedLines: List<LyricsLine> = emptyList(),
    val available: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val subscriptionTier: String = "free",
    val preferredLanguage: String = "id",
    val preferredTheme: String = "dark",
    val audioQuality: String = "auto",
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavedTrackDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val trackId: String = "",
    val title: String = "",
    val artistName: String = "",
    val albumTitle: String = "",
    val artworkUrl: String = "",
    val streamUrl: String = "",
    val downloadUrl: String = "",
    val durationSeconds: Long = 0L,
    val genre: String = "",
    val downloadable: Boolean = false,
    val source: String = "audius",
    val playCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMusicTrack(): MusicTrack = MusicTrack(
        id = trackId.ifBlank { id },
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        downloadUrl = downloadUrl,
        durationSeconds = durationSeconds.toInt(),
        genre = genre.ifBlank { "Music" },
        playCount = playCount,
        downloadable = downloadable,
        source = MusicSource.fromKey(source)
    )
}

@Keep
@IgnoreExtraProperties
data class SearchHistoryDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val query: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserDeviceDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val platform: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudPlaylistDoc(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val name: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val visibility: String = "private",
    val trackCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudPlaylistTrackDoc(
    @DocumentId val id: String = "",
    val playlistId: String = "",
    val ownerId: String = "",
    val visibility: String = "private",
    val trackId: String = "",
    val title: String = "",
    val artistName: String = "",
    val albumTitle: String = "",
    val artworkUrl: String = "",
    val streamUrl: String = "",
    val downloadUrl: String = "",
    val durationSeconds: Long = 0L,
    val genre: String = "",
    val downloadable: Boolean = false,
    val source: String = "audius",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toMusicTrack(): MusicTrack = MusicTrack(
        id = trackId.ifBlank { id },
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        downloadUrl = downloadUrl,
        durationSeconds = durationSeconds.toInt(),
        genre = genre.ifBlank { "Music" },
        downloadable = downloadable,
        source = MusicSource.fromKey(source)
    )
}

@Keep
@IgnoreExtraProperties
data class CatalogTrackDoc(
    @DocumentId val id: String = "",
    val trackId: String = "",
    val title: String = "",
    val artistName: String = "",
    val albumTitle: String = "",
    val artworkUrl: String = "",
    val streamUrl: String = "",
    val durationSeconds: Long = 0L,
    val genre: String = "",
    val downloadable: Boolean = false,
    val published: Boolean = true,
    val licenseNote: String = ""
) {
    fun toMusicTrack(): MusicTrack = MusicTrack(
        id = trackId.ifBlank { id },
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationSeconds = durationSeconds.toInt(),
        genre = genre.ifBlank { "Curated" },
        downloadable = downloadable,
        source = MusicSource.CATALOG,
        licenseNote = licenseNote.ifBlank { "Stream Music Licensed Catalog" }
    )
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
