package com.example.data.repository

import android.content.Context
import com.example.data.model.MusicSource
import com.example.data.model.MusicTrack
import com.example.data.remote.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

sealed interface DownloadStatus {
    data object NotDownloaded : DownloadStatus
    data class Downloading(val progress: Float) : DownloadStatus
    data class Downloaded(val filePath: String) : DownloadStatus
    data class Failed(val reason: String) : DownloadStatus
}

class OfflineDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val client = NetworkModule.getOkHttpClient(appContext)
    private val offlineDir = File(appContext.filesDir, "offline_tracks").apply { mkdirs() }

    private val _downloadedTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val downloadedTracks: StateFlow<List<MusicTrack>> = _downloadedTracks.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadStatus>> = _downloadStates.asStateFlow()

    init {
        refreshDownloads()
    }

    fun refreshDownloads() {
        val files = offlineDir.listFiles { file -> file.extension == "json" } ?: emptyArray()
        val tracks = files.mapNotNull { metaFile ->
            runCatching {
                val json = JSONObject(metaFile.readText())
                val audioPath = json.getString("localFilePath")
                if (!File(audioPath).exists()) return@mapNotNull null
                MusicTrack(
                    id = json.getString("id"),
                    title = json.getString("title"),
                    artistName = json.getString("artistName"),
                    albumTitle = json.optString("albumTitle", ""),
                    artworkUrl = json.optString("artworkUrl", ""),
                    streamUrl = audioPath,
                    downloadUrl = json.optString("downloadUrl", ""),
                    durationSeconds = json.optInt("durationSeconds", 0),
                    genre = json.optString("genre", "Music"),
                    downloadable = true,
                    source = MusicSource.fromKey(json.optString("source", "audius")),
                    licenseNote = "Downloaded Offline (Licensed)",
                    localFilePath = audioPath
                )
            }.getOrNull()
        }
        _downloadedTracks.value = tracks
        val statusMap = _downloadStates.value.toMutableMap()
        tracks.forEach { t ->
            t.localFilePath?.let { statusMap[t.id] = DownloadStatus.Downloaded(it) }
        }
        _downloadStates.value = statusMap
    }

    fun getLocalFileForTrack(trackId: String): File? {
        val safeId = sanitizeId(trackId)
        val audioFile = File(offlineDir, "$safeId.mp3")
        return if (audioFile.exists() && audioFile.length() > 0L) audioFile else null
    }

    suspend fun downloadTrack(track: MusicTrack): Result<File> = withContext(Dispatchers.IO) {
        if (!track.downloadable) {
            return@withContext Result.failure(IllegalStateException("Track is not licensed for offline download."))
        }
        val targetUrl = track.downloadUrl.ifBlank { track.streamUrl }
        if (targetUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Missing legal download URL."))
        }

        val safeId = sanitizeId(track.id)
        val tempFile = File(offlineDir, "$safeId.tmp")
        val finalFile = File(offlineDir, "$safeId.mp3")
        val metaFile = File(offlineDir, "$safeId.json")

        try {
            updateState(track.id, DownloadStatus.Downloading(0.05f))
            val request = Request.Builder().url(targetUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed with HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty download body")
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalBytes > 0L) {
                                updateState(track.id, DownloadStatus.Downloading((downloaded.toFloat() / totalBytes).coerceIn(0.05f, 0.99f)))
                            }
                        }
                    }
                }
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            val metaJson = JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artistName", track.artistName)
                put("albumTitle", track.albumTitle)
                put("artworkUrl", track.artworkUrl)
                put("downloadUrl", targetUrl)
                put("durationSeconds", track.durationSeconds)
                put("genre", track.genre)
                put("source", track.source.key)
                put("localFilePath", finalFile.absolutePath)
            }
            metaFile.writeText(metaJson.toString())
            updateState(track.id, DownloadStatus.Downloaded(finalFile.absolutePath))
            refreshDownloads()
            Result.success(finalFile)
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            updateState(track.id, DownloadStatus.Failed(e.localizedMessage ?: "Download error"))
            Result.failure(e)
        }
    }

    fun deleteDownload(trackId: String) {
        val safeId = sanitizeId(trackId)
        File(offlineDir, "$safeId.mp3").delete()
        File(offlineDir, "$safeId.json").delete()
        val updated = _downloadStates.value.toMutableMap()
        updated[trackId] = DownloadStatus.NotDownloaded
        _downloadStates.value = updated
        refreshDownloads()
    }

    private fun updateState(trackId: String, status: DownloadStatus) {
        val map = _downloadStates.value.toMutableMap()
        map[trackId] = status
        _downloadStates.value = map
    }

    private fun sanitizeId(raw: String): String = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
}
