package com.example.data.repository

import android.util.Log
import com.example.data.model.CatalogTrackDoc
import com.example.data.model.LyricsLine
import com.example.data.model.MusicArtist
import com.example.data.model.MusicPlaylistCollection
import com.example.data.model.MusicSource
import com.example.data.model.MusicTrack
import com.example.data.model.TrackLyrics
import com.example.data.remote.AudiusApiService
import com.example.data.remote.AudiusPlaylistDto
import com.example.data.remote.AudiusTrackDto
import com.example.data.remote.DeezerAlbumDto
import com.example.data.remote.DeezerApiService
import com.example.data.remote.DeezerTrackDto
import com.example.data.remote.LrclibApiService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class HomeCatalogBundle(
    val recommendedPlaylists: List<MusicPlaylistCollection>,
    val trendingTracks: List<MusicTrack>,
    val latestTracks: List<MusicTrack>,
    val featuredAlbums: List<MusicPlaylistCollection>,
    val curatedCatalogTracks: List<MusicTrack>
)

data class SearchResultsBundle(
    val songs: List<MusicTrack>,
    val artists: List<MusicArtist>,
    val albums: List<MusicPlaylistCollection>,
    val playlists: List<MusicPlaylistCollection>
)

class MusicCatalogRepository(
    private val audiusApi: AudiusApiService,
    private val deezerApi: DeezerApiService,
    private val lrclibApi: LrclibApiService,
    private val db: FirebaseFirestore
) {
    private val hostMutex = Mutex()
    @Volatile
    private var cachedAudiusHost: String? = null

    private suspend fun resolveAudiusHost(): String {
        cachedAudiusHost?.let { return it }
        return hostMutex.withLock {
            cachedAudiusHost?.let { return@withLock it }
            val host = try {
                val resp = audiusApi.getDiscoveryNodes()
                resp.data.firstOrNull { it.startsWith("https://") }
                    ?: "https://discoveryprovider.audius.co"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Discovery node lookup failed, using primary gateway", e)
                "https://discoveryprovider.audius.co"
            }
            cachedAudiusHost = host.trimEnd('/')
            cachedAudiusHost!!
        }
    }

    suspend fun loadHomeCatalog(): Result<HomeCatalogBundle> = try {
        val host = resolveAudiusHost()
        coroutineScope {
            val trendingDeferred = async {
                runCatching {
                    audiusApi.getTrendingTracks("$host/v1/tracks/trending", limit = 20)
                        .data.filter { it.isStreamable }.map { it.toMusicTrack(host) }
                }.getOrElse { emptyList() }
            }
            val latestDeferred = async {
                runCatching {
                    val audiusFresh = audiusApi.getTrendingTracks(
                        url = "$host/v1/tracks/trending",
                        time = "week",
                        limit = 15
                    ).data.filter { it.isStreamable }.map { it.toMusicTrack(host) }
                    val deezerCharts = deezerApi.getChartTracks(limit = 15)
                        .data.mapNotNull { it.toMusicTrackOrNull() }
                    (audiusFresh + deezerCharts).distinctBy { it.id }
                }.getOrElse { emptyList() }
            }
            val playlistsDeferred = async {
                runCatching {
                    audiusApi.getTrendingPlaylists("$host/v1/playlists/trending", limit = 14)
                        .data.map { it.toPlaylistCollection() }
                }.getOrElse { emptyList() }
            }
            val albumsDeferred = async {
                runCatching {
                    deezerApi.getChartAlbums(limit = 14)
                        .data.map { it.toPlaylistCollection() }
                }.getOrElse { emptyList() }
            }
            val cloudCatalogDeferred = async {
                runCatching {
                    db.collection("catalog_tracks")
                        .whereEqualTo("published", true)
                        .limit(20)
                        .get()
                        .await()
                        .toObjects(CatalogTrackDoc::class.java)
                        .map { it.toMusicTrack() }
                }.getOrElse { emptyList() }
            }

            val trending = trendingDeferred.await()
            val latest = latestDeferred.await()
            val playlists = playlistsDeferred.await()
            val albums = albumsDeferred.await()
            val cloudTracks = cloudCatalogDeferred.await()

            val mergedTrending = (cloudTracks + trending).distinctBy { it.id }
            if (mergedTrending.isEmpty() && latest.isEmpty() && albums.isEmpty()) {
                Result.failure(IllegalStateException("Unable to load music catalog. Check your network connection."))
            } else {
                Result.success(
                    HomeCatalogBundle(
                        recommendedPlaylists = playlists,
                        trendingTracks = mergedTrending,
                        latestTracks = latest.ifEmpty { mergedTrending.reversed() },
                        featuredAlbums = albums,
                        curatedCatalogTracks = cloudTracks
                    )
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getTracksByGenre(genre: String): Result<List<MusicTrack>> = try {
        val host = resolveAudiusHost()
        val tracks = audiusApi.getTrendingTracks(
            url = "$host/v1/tracks/trending",
            genre = genre,
            limit = 25
        ).data.filter { it.isStreamable }.map { it.toMusicTrack(host) }
        if (tracks.isEmpty()) {
            val fallback = audiusApi.searchTracks(
                url = "$host/v1/tracks/search",
                query = genre,
                limit = 20
            ).data.filter { it.isStreamable }.map { it.toMusicTrack(host) }
            Result.success(fallback)
        } else {
            Result.success(tracks)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getRecommendationsFromHistory(history: List<MusicTrack>): Result<List<MusicTrack>> = try {
        val host = resolveAudiusHost()
        val topGenre = history.mapNotNull { it.genre.takeIf { g -> g.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val topArtist = history.firstOrNull()?.artistName

        val tracks = when {
            !topGenre.isNullOrBlank() -> {
                audiusApi.getTrendingTracks(
                    url = "$host/v1/tracks/trending",
                    genre = topGenre,
                    limit = 15
                ).data.filter { it.isStreamable }.map { it.toMusicTrack(host) }
            }
            !topArtist.isNullOrBlank() -> {
                audiusApi.searchTracks(
                    url = "$host/v1/tracks/search",
                    query = topArtist,
                    limit = 15
                ).data.filter { it.isStreamable }.map { it.toMusicTrack(host) }
            }
            else -> emptyList()
        }
        val historyIds = history.map { it.id }.toSet()
        val filtered = tracks.filterNot { it.id in historyIds }
        Result.success(filtered.ifEmpty { tracks })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun searchAll(query: String): Result<SearchResultsBundle> = try {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            Result.success(SearchResultsBundle(emptyList(), emptyList(), emptyList(), emptyList()))
        } else {
            val host = resolveAudiusHost()
            coroutineScope {
                val audiusTracks = async {
                    runCatching {
                        audiusApi.searchTracks("$host/v1/tracks/search", trimmed, limit = 20)
                            .data.filter { it.isStreamable }.map { it.toMusicTrack(host) }
                    }.getOrElse { emptyList() }
                }
                val deezerTracks = async {
                    runCatching {
                        deezerApi.searchTracks(trimmed, limit = 12)
                            .data.mapNotNull { it.toMusicTrackOrNull() }
                    }.getOrElse { emptyList() }
                }
                val artists = async {
                    runCatching {
                        audiusApi.searchUsers("$host/v1/users/search", trimmed, limit = 12)
                            .data.map {
                                MusicArtist(
                                    id = it.id,
                                    name = it.name,
                                    handle = it.handle,
                                    avatarUrl = it.profilePicture?.bestUrl().orEmpty(),
                                    followerCount = it.followerCount,
                                    trackCount = it.trackCount
                                )
                            }
                    }.getOrElse { emptyList() }
                }
                val albums = async {
                    runCatching {
                        deezerApi.searchAlbums(trimmed, limit = 12)
                            .data.map { it.toPlaylistCollection() }
                    }.getOrElse { emptyList() }
                }
                val playlists = async {
                    runCatching {
                        audiusApi.searchPlaylists("$host/v1/playlists/search", trimmed, limit = 12)
                            .data.map { it.toPlaylistCollection() }
                    }.getOrElse { emptyList() }
                }

                Result.success(
                    SearchResultsBundle(
                        songs = (audiusTracks.await() + deezerTracks.await()).distinctBy { it.id },
                        artists = artists.await(),
                        albums = albums.await(),
                        playlists = playlists.await()
                    )
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getCollectionTracks(collection: MusicPlaylistCollection): Result<List<MusicTrack>> = try {
        when (collection.source) {
            MusicSource.DEEZER -> {
                val albumId = collection.id.removePrefix("dz_album_").toLongOrNull()
                    ?: return Result.success(emptyList())
                val tracks = deezerApi.getAlbumTracks(albumId).data.mapNotNull {
                    it.toMusicTrackOrNull(
                        fallbackAlbum = collection.title,
                        fallbackCover = collection.artworkUrl,
                        fallbackArtist = collection.creatorName
                    )
                }
                Result.success(tracks)
            }
            else -> {
                val host = resolveAudiusHost()
                val rawId = collection.id.removePrefix("au_pl_")
                val tracks = audiusApi.getPlaylistTracks("$host/v1/playlists/$rawId/tracks")
                    .data.filter { it.isStreamable }.map { it.toMusicTrack(host) }
                Result.success(tracks)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getLyrics(artistName: String, trackName: String): TrackLyrics {
        return try {
            val resp = lrclibApi.getLyrics(artistName.trim(), trackName.trim())
            val synced = parseLrcLyrics(resp.syncedLyrics.orEmpty())
            val plain = resp.plainLyrics.orEmpty().trim()
            TrackLyrics(
                plainLyrics = plain,
                syncedLines = synced,
                available = synced.isNotEmpty() || plain.isNotBlank()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TrackLyrics(available = false)
        }
    }

    private fun parseLrcLyrics(lrc: String): List<LyricsLine> {
        if (lrc.isBlank()) return emptyList()
        val regex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]\s*(.*)""")
        return lrc.lineSequence().mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val mins = match.groupValues[1].toLongOrNull() ?: 0L
            val secs = match.groupValues[2].toLongOrNull() ?: 0L
            val frac = match.groupValues[3]
            val millis = when (frac.length) {
                1 -> (frac.toLongOrNull() ?: 0L) * 100L
                2 -> (frac.toLongOrNull() ?: 0L) * 10L
                3 -> frac.toLongOrNull() ?: 0L
                else -> 0L
            }
            val text = match.groupValues[4].trim()
            if (text.isEmpty()) null else LyricsLine((mins * 60_000L) + (secs * 1_000L) + millis, text)
        }.toList()
    }

    private fun AudiusTrackDto.toMusicTrack(host: String): MusicTrack {
        val stream = "$host/v1/tracks/$id/stream?app_name=StreamMusic"
        val download = if (isDownloadable) "$host/v1/tracks/$id/download?app_name=StreamMusic" else ""
        return MusicTrack(
            id = "au_$id",
            title = title.ifBlank { "Untitled Track" },
            artistName = user?.name?.ifBlank { "Independent Artist" } ?: "Independent Artist",
            artistId = user?.id.orEmpty(),
            albumTitle = mood?.takeIf { it.isNotBlank() } ?: "Audius Single",
            artworkUrl = artwork?.bestUrl().orEmpty(),
            streamUrl = stream,
            downloadUrl = download,
            durationSeconds = duration.coerceAtLeast(0),
            genre = genre?.ifBlank { "Electronic" } ?: "Electronic",
            playCount = playCount,
            downloadable = isDownloadable,
            source = MusicSource.AUDIUS,
            licenseNote = if (isDownloadable) "Audius Licensed (Stream & Download)" else "Audius Licensed Stream"
        )
    }

    private fun AudiusPlaylistDto.toPlaylistCollection(): MusicPlaylistCollection {
        return MusicPlaylistCollection(
            id = "au_pl_$id",
            title = playlistName.ifBlank { "Curated Mix" },
            creatorName = user?.name?.ifBlank { "Audius Curator" } ?: "Audius Curator",
            description = description.orEmpty(),
            artworkUrl = artwork?.bestUrl().orEmpty(),
            trackCount = trackCount,
            isAlbum = isAlbum,
            source = MusicSource.AUDIUS
        )
    }

    private fun DeezerTrackDto.toMusicTrackOrNull(
        fallbackAlbum: String = "",
        fallbackCover: String = "",
        fallbackArtist: String = ""
    ): MusicTrack? {
        val previewUrl = preview?.takeIf { it.startsWith("https://") } ?: return null
        return MusicTrack(
            id = "dz_$id",
            title = title.ifBlank { "Untitled" },
            artistName = artist?.name?.ifBlank { fallbackArtist } ?: fallbackArtist.ifBlank { "Artist" },
            artistId = artist?.id?.toString().orEmpty(),
            albumTitle = album?.title?.ifBlank { fallbackAlbum } ?: fallbackAlbum,
            artworkUrl = album?.bestCover()?.ifBlank { fallbackCover } ?: fallbackCover,
            streamUrl = previewUrl,
            downloadUrl = "",
            durationSeconds = duration.coerceAtLeast(30),
            genre = "Chart Hits",
            downloadable = false,
            source = MusicSource.DEEZER,
            licenseNote = "Deezer Licensed 30s Preview"
        )
    }

    private fun DeezerAlbumDto.toPlaylistCollection(): MusicPlaylistCollection {
        return MusicPlaylistCollection(
            id = "dz_album_$id",
            title = title.ifBlank { "Featured Album" },
            creatorName = artist?.name?.ifBlank { "Artist" } ?: "Artist",
            artworkUrl = bestCover(),
            trackCount = nbTracks,
            isAlbum = true,
            source = MusicSource.DEEZER
        )
    }

    private companion object {
        const val TAG = "MusicCatalogRepo"
    }
}
