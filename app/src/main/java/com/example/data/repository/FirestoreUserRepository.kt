package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AudioQuality
import com.example.data.model.CloudPlaylistDoc
import com.example.data.model.CloudPlaylistTrackDoc
import com.example.data.model.MusicTrack
import com.example.data.model.SavedTrackDoc
import com.example.data.model.SearchHistoryDoc
import com.example.data.model.UserDeviceDoc
import com.example.data.model.UserProfileDoc
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreUserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return cleaned.take(120).ifBlank { "item_${UUID.randomUUID().toString().take(8)}" }
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            require(uid == requireUserId()) { "Session mismatch." }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = (user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")
                    ?: "Stream Listener").take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to user.email?.take(120),
                    "photoUrl" to user.photoUrl?.toString()?.take(600),
                    "subscriptionTier" to "free",
                    "preferredLanguage" to "id",
                    "preferredTheme" to "dark",
                    "audioQuality" to "auto",
                    "notificationsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfileDoc?> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Refusing cross-user profile observation." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    unwrapAndThrow(error, OperationType.GET, path, uid)
                }
        )
    }

    suspend fun updateDisplayName(userId: String, newName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Cross-user write blocked." }
            val trimmed = newName.trim().take(80)
            require(trimmed.isNotEmpty()) { "Name cannot be empty." }
            db.collection("users").document(uid)
                .update(mapOf("displayName" to trimmed, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCloudPreferences(
        userId: String,
        theme: String? = null,
        language: String? = null,
        audioQuality: AudioQuality? = null,
        notificationsEnabled: Boolean? = null,
        subscriptionTier: String? = null,
        photoUrl: String? = null
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Cross-user write blocked." }
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            theme?.let { if (it in listOf("dark", "light")) updates["preferredTheme"] = it }
            language?.let { if (it in listOf("id", "en")) updates["preferredLanguage"] = it }
            audioQuality?.let { updates["audioQuality"] = it.key }
            notificationsEnabled?.let { updates["notificationsEnabled"] = it }
            subscriptionTier?.let { if (it in listOf("free", "premium")) updates["subscriptionTier"] = it }
            photoUrl?.let { if (it.length <= 600) updates["photoUrl"] = it }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Favorites ---
    fun observeFavorites(userId: String): Flow<List<SavedTrackDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/favorites"
        emitAll(
            db.collection("users").document(uid).collection("favorites")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(SavedTrackDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun toggleFavorite(userId: String, track: MusicTrack, currentlyFavorite: Boolean): Result<Unit> {
        val docId = sanitizeDocId(track.id)
        val path = "users/$userId/favorites/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = db.collection("users").document(uid).collection("favorites").document(docId)
            if (currentlyFavorite) {
                docRef.delete().await()
            } else {
                docRef.set(buildSavedTrackPayload(uid, track, 1L)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- Listening History ---
    fun observeHistory(userId: String): Flow<List<SavedTrackDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/history"
        emitAll(
            db.collection("users").document(uid).collection("history")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snap -> snap.toObjects(SavedTrackDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun recordPlayHistory(userId: String, track: MusicTrack): Result<Unit> {
        val docId = sanitizeDocId(track.id)
        val path = "users/$userId/history/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = db.collection("users").document(uid).collection("history").document(docId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val prevCount = (existing.getLong("playCount") ?: 1L).coerceAtMost(999_999L)
                docRef.update(
                    mapOf(
                        "title" to track.title.take(200).ifBlank { "Track" },
                        "artistName" to track.artistName.take(150).ifBlank { "Artist" },
                        "streamUrl" to track.streamUrl.take(800),
                        "artworkUrl" to track.artworkUrl.take(600),
                        "playCount" to prevCount + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(buildSavedTrackPayload(uid, track, 1L)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- Search History ---
    fun observeSearchHistory(userId: String): Flow<List<SearchHistoryDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/search_history"
        emitAll(
            db.collection("users").document(uid).collection("search_history")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(15)
                .snapshots()
                .map { snap -> snap.toObjects(SearchHistoryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun saveSearchQuery(userId: String, query: String): Result<Unit> {
        val trimmed = query.trim().take(120)
        if (trimmed.isEmpty()) return Result.success(Unit)
        val docId = sanitizeDocId("q_${trimmed.lowercase()}")
        val path = "users/$userId/search_history/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = db.collection("users").document(uid).collection("search_history").document(docId)
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "query" to trimmed,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSearchQuery(userId: String, entryId: String): Result<Unit> {
        val path = "users/$userId/search_history/$entryId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("users").document(uid).collection("search_history").document(entryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearSearchHistory(userId: String): Result<Unit> {
        val path = "users/$userId/search_history"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val snap = db.collection("users").document(uid).collection("search_history").get().await()
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Device Management ---
    fun observeDevices(userId: String): Flow<List<UserDeviceDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "users/$uid/devices"
        emitAll(
            db.collection("users").document(uid).collection("devices")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(UserDeviceDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun registerCurrentDevice(userId: String): Result<Unit> {
        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".take(100)
        val docId = sanitizeDocId("dev_${Build.MANUFACTURER}_${Build.MODEL}")
        val path = "users/$userId/devices/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = db.collection("users").document(uid).collection("devices").document(docId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "deviceName" to deviceName,
                        "platform" to "Android ${Build.VERSION.RELEASE}".take(50),
                        "active" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "deviceName" to deviceName,
                        "platform" to "Android ${Build.VERSION.RELEASE}".take(50),
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun removeDevice(userId: String, deviceId: String): Result<Unit> {
        val path = "users/$userId/devices/$deviceId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("users").document(uid).collection("devices").document(deviceId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Cloud Playlists (Public & Private) ---
    fun observeMyPlaylists(userId: String): Flow<List<CloudPlaylistDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "playlists"
        emitAll(
            db.collection("playlists")
                .whereEqualTo("ownerId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CloudPlaylistDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, uid) }
        )
    }

    fun observePublicPlaylists(): Flow<List<CloudPlaylistDoc>> = flow {
        val uid = requireUserId()
        val path = "playlists"
        emitAll(
            db.collection("playlists")
                .whereEqualTo("visibility", "public")
                .limit(30)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CloudPlaylistDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, uid) }
        )
    }

    fun observePlaylistTracks(
        playlistId: String,
        ownerId: String,
        currentUserId: String
    ): Flow<List<CloudPlaylistTrackDoc>> = flow {
        val uid = requireUserId()
        check(uid == currentUserId)
        val path = "playlists/$playlistId/tracks"
        val baseQuery = db.collection("playlists").document(playlistId).collection("tracks")
        val scopedQuery = if (ownerId == uid) {
            baseQuery.whereEqualTo("ownerId", uid)
        } else {
            baseQuery.whereEqualTo("visibility", "public")
        }
        emitAll(
            scopedQuery.snapshots()
                .map { snap ->
                    snap.toObjects(CloudPlaylistTrackDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun createPlaylist(
        userId: String,
        ownerName: String,
        name: String,
        description: String,
        isPublic: Boolean
    ): Result<String> {
        val safeId = "pl_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "playlists/$safeId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanName = name.trim().take(100)
            require(cleanName.isNotEmpty()) { "Playlist name is required." }
            val payload = mapOf(
                "ownerId" to uid,
                "ownerName" to ownerName.trim().take(80).ifBlank { "Stream User" },
                "name" to cleanName,
                "description" to description.trim().take(300).takeIf { it.isNotEmpty() },
                "visibility" to if (isPublic) "public" else "private",
                "trackCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("playlists").document(safeId).set(payload).await()
            Result.success(safeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePlaylistVisibility(playlistId: String, isPublic: Boolean): Result<Unit> {
        val path = "playlists/$playlistId"
        return try {
            val uid = requireUserId()
            val visibility = if (isPublic) "public" else "private"
            val playlistRef = db.collection("playlists").document(playlistId)
            playlistRef.update(
                mapOf(
                    "visibility" to visibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            // Sync denormalized visibility onto owned tracks
            val tracksSnap = playlistRef.collection("tracks")
                .whereEqualTo("ownerId", uid)
                .get()
                .await()
            if (!tracksSnap.isEmpty) {
                val batch = db.batch()
                tracksSnap.documents.forEach { batch.update(it.reference, "visibility", visibility) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        val path = "playlists/$playlistId"
        return try {
            val uid = requireUserId()
            val playlistRef = db.collection("playlists").document(playlistId)
            val tracksSnap = playlistRef.collection("tracks")
                .whereEqualTo("ownerId", uid)
                .get()
                .await()
            val batch = db.batch()
            tracksSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(playlistRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addTrackToPlaylist(playlist: CloudPlaylistDoc, track: MusicTrack): Result<Unit> {
        val trackDocId = sanitizeDocId(track.id)
        val path = "playlists/${playlist.id}/tracks/$trackDocId"
        return try {
            val uid = requireUserId()
            check(playlist.ownerId == uid) { "Only playlist owner can add tracks." }
            val playlistRef = db.collection("playlists").document(playlist.id)
            val trackRef = playlistRef.collection("tracks").document(trackDocId)
            val payload = mapOf(
                "playlistId" to playlist.id,
                "ownerId" to uid,
                "visibility" to playlist.visibility,
                "trackId" to track.id.take(128),
                "title" to track.title.take(200).ifBlank { "Track" },
                "artistName" to track.artistName.take(150).ifBlank { "Artist" },
                "albumTitle" to track.albumTitle.take(200).takeIf { it.isNotBlank() },
                "artworkUrl" to track.artworkUrl.take(600).takeIf { it.isNotBlank() },
                "streamUrl" to track.streamUrl.take(800),
                "downloadUrl" to track.downloadUrl.take(800).takeIf { it.isNotBlank() },
                "durationSeconds" to track.durationSeconds.toLong().coerceIn(0L, 86400L),
                "genre" to track.genre.take(80).takeIf { it.isNotBlank() },
                "downloadable" to track.downloadable,
                "source" to track.source.key,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            trackRef.set(payload).await()
            val playlistUpdates = mutableMapOf<String, Any>(
                "trackCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (playlist.coverUrl.isBlank() && track.artworkUrl.isNotBlank()) {
                playlistUpdates["coverUrl"] = track.artworkUrl.take(600)
            }
            playlistRef.update(playlistUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackDocId: String): Result<Unit> {
        val path = "playlists/$playlistId/tracks/$trackDocId"
        return try {
            requireUserId()
            val playlistRef = db.collection("playlists").document(playlistId)
            playlistRef.collection("tracks").document(trackDocId).delete().await()
            playlistRef.update(
                mapOf(
                    "trackCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun buildSavedTrackPayload(uid: String, track: MusicTrack, playCount: Long): Map<String, Any> {
        return mapOf(
            "userId" to uid,
            "trackId" to track.id.take(128),
            "title" to track.title.take(200).ifBlank { "Track" },
            "artistName" to track.artistName.take(150).ifBlank { "Artist" },
            "albumTitle" to track.albumTitle.take(200).takeIf { it.isNotBlank() },
            "artworkUrl" to track.artworkUrl.take(600).takeIf { it.isNotBlank() },
            "streamUrl" to track.streamUrl.take(800),
            "downloadUrl" to track.downloadUrl.take(800).takeIf { it.isNotBlank() },
            "durationSeconds" to track.durationSeconds.toLong().coerceIn(0L, 86400L),
            "genre" to track.genre.take(80).takeIf { it.isNotBlank() },
            "downloadable" to track.downloadable,
            "source" to track.source.key,
            "playCount" to playCount.coerceIn(1L, 1_000_000L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    private fun unwrapAndThrow(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }
}
