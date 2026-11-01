package com.mrsep.musicrecognizer.core.metadata.spotify

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
internal data class SpotifyStoreData(
    val clientId: String = "",
    val enabled: Boolean = false,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val accessTokenExpiresAtMs: Long = 0,
    val accountId: String? = null,
    val accountName: String? = null,
    val playlistId: String? = null,
    val pendingVerifier: String? = null,
    val pendingState: String? = null,
    /** Spotify track IDs this app already added, oldest first, capped. */
    val addedTrackIds: List<String> = emptyList(),
) {
    val isLoggedIn get() = refreshToken != null

    fun loggedOut() = SpotifyStoreData(clientId = clientId)
}

internal interface SpotifyStore {
    val dataFlow: StateFlow<SpotifyStoreData>
    suspend fun update(transform: (SpotifyStoreData) -> SpotifyStoreData): SpotifyStoreData
}

/** One JSON blob in app-private SharedPreferences; writes are serialized. */
internal class SharedPrefsSpotifyStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) : SpotifyStore {

    private val prefs = context.getSharedPreferences("spotify_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    override val dataFlow = state.asStateFlow()

    override suspend fun update(transform: (SpotifyStoreData) -> SpotifyStoreData) = mutex.withLock {
        val updated = transform(state.value)
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY, json.encodeToString(updated)).commit()
        }
        state.value = updated
        updated
    }

    private fun load(): SpotifyStoreData = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString<SpotifyStoreData>(it) }.getOrNull() }
        ?: SpotifyStoreData()

    private companion object {
        const val KEY = "data"
    }
}
