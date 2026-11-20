package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AudiusHostsResponse(
    val data: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AudiusArtworkDto(
    @Json(name = "150x150") val small: String? = null,
    @Json(name = "480x480") val medium: String? = null,
    @Json(name = "1000x1000") val large: String? = null
) {
    fun bestUrl(): String = large ?: medium ?: small ?: ""
}

@JsonClass(generateAdapter = true)
data class AudiusUserDto(
    val id: String = "",
    val name: String = "Independent Artist",
    val handle: String = "",
    @Json(name = "profile_picture") val profilePicture: AudiusArtworkDto? = null,
    @Json(name = "follower_count") val followerCount: Long = 0L,
    @Json(name = "track_count") val trackCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class AudiusTrackDto(
    val id: String = "",
    val title: String = "",
    val genre: String? = null,
    val mood: String? = null,
    val duration: Int = 0,
    @Json(name = "play_count") val playCount: Long = 0L,
    @Json(name = "is_downloadable") val isDownloadable: Boolean = false,
    @Json(name = "is_streamable") val isStreamable: Boolean = true,
    val artwork: AudiusArtworkDto? = null,
    val user: AudiusUserDto? = null
)

@JsonClass(generateAdapter = true)
data class AudiusTracksResponse(
    val data: List<AudiusTrackDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AudiusUsersResponse(
    val data: List<AudiusUserDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AudiusPlaylistDto(
    val id: String = "",
    @Json(name = "playlist_name") val playlistName: String = "",
    val description: String? = null,
    @Json(name = "is_album") val isAlbum: Boolean = false,
    @Json(name = "track_count") val trackCount: Int = 0,
    val artwork: AudiusArtworkDto? = null,
    val user: AudiusUserDto? = null
)

@JsonClass(generateAdapter = true)
data class AudiusPlaylistsResponse(
    val data: List<AudiusPlaylistDto> = emptyList()
)

// Deezer Public Chart & Album DTOs
@JsonClass(generateAdapter = true)
data class DeezerArtistDto(
    val id: Long = 0L,
    val name: String = "",
    @Json(name = "picture_big") val pictureBig: String? = null,
    @Json(name = "picture_medium") val pictureMedium: String? = null
)

@JsonClass(generateAdapter = true)
data class DeezerAlbumDto(
    val id: Long = 0L,
    val title: String = "",
    @Json(name = "cover_xl") val coverXl: String? = null,
    @Json(name = "cover_big") val coverBig: String? = null,
    @Json(name = "cover_medium") val coverMedium: String? = null,
    val artist: DeezerArtistDto? = null,
    @Json(name = "nb_tracks") val nbTracks: Int = 0
) {
    fun bestCover(): String = coverXl ?: coverBig ?: coverMedium ?: ""
}

@JsonClass(generateAdapter = true)
data class DeezerTrackDto(
    val id: Long = 0L,
    val title: String = "",
    val duration: Int = 30,
    val preview: String? = null,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumDto? = null
)

@JsonClass(generateAdapter = true)
data class DeezerTrackListResponse(
    val data: List<DeezerTrackDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DeezerAlbumListResponse(
    val data: List<DeezerAlbumDto> = emptyList()
)

// LRCLIB Lyrics DTO
@JsonClass(generateAdapter = true)
data class LrclibResponseDto(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)
