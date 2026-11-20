package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface AudiusApiService {
    @GET("https://api.audius.co")
    suspend fun getDiscoveryNodes(): AudiusHostsResponse

    @GET
    suspend fun getTrendingTracks(
        @Url url: String,
        @Query("genre") genre: String? = null,
        @Query("time") time: String? = null,
        @Query("limit") limit: Int = 25,
        @Query("app_name") appName: String = "StreamMusic"
    ): AudiusTracksResponse

    @GET
    suspend fun searchTracks(
        @Url url: String,
        @Query("query") query: String,
        @Query("limit") limit: Int = 25,
        @Query("app_name") appName: String = "StreamMusic"
    ): AudiusTracksResponse

    @GET
    suspend fun searchUsers(
        @Url url: String,
        @Query("query") query: String,
        @Query("limit") limit: Int = 15,
        @Query("app_name") appName: String = "StreamMusic"
    ): AudiusUsersResponse

    @GET
    suspend fun getTrendingPlaylists(
        @Url url: String,
        @Query("limit") limit: Int = 15,
        @Query("app_name") appName: String = "StreamMusic"
    ): AudiusPlaylistsResponse

    @GET
    suspend fun searchPlaylists(
        @Url url: String,
        @Query("query") query: String,
        @Query("limit") limit: Int = 15,
        @Query("app_name") appName: String = "StreamMusic"
    ): AudiusPlaylistsResponse

    @GET
    suspend fun getPlaylistTracks(
        @Url url: String,
        @Query("app_name") appName: String = "StreamMusic"
    ): AudiusTracksResponse
}

interface DeezerApiService {
    @GET("chart/0/tracks")
    suspend fun getChartTracks(@Query("limit") limit: Int = 20): DeezerTrackListResponse

    @GET("chart/0/albums")
    suspend fun getChartAlbums(@Query("limit") limit: Int = 15): DeezerAlbumListResponse

    @GET("search/track")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): DeezerTrackListResponse

    @GET("search/album")
    suspend fun searchAlbums(
        @Query("q") query: String,
        @Query("limit") limit: Int = 15
    ): DeezerAlbumListResponse

    @GET("album/{albumId}/tracks")
    suspend fun getAlbumTracks(@Path("albumId") albumId: Long): DeezerTrackListResponse
}

interface LrclibApiService {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("artist_name") artistName: String,
        @Query("track_name") trackName: String
    ): LrclibResponseDto
}
