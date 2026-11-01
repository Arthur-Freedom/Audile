package com.mrsep.musicrecognizer.core.metadata.spotify

import android.util.Log
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyAddResult
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyLoginResult
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyPlaylistSync
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyPlaylistSync.Companion.PLAYLIST_NAME
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyPlaylistSync.Companion.REDIRECT_URI
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifySyncState
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.metadata.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

internal class SpotifyPlaylistSyncImpl(
    private val store: SpotifyStore,
    private val httpClient: HttpClient,
    /** Client ID of the Spotify app this APK was built with; empty means the user pastes their own. */
    private val builtInClientId: String,
) : SpotifyPlaylistSync {

    @Inject
    constructor(store: SpotifyStore, httpClient: HttpClient) :
        this(store, httpClient, BuildConfig.SPOTIFY_CLIENT_ID)

    /** One add at a time, so two quick recognitions can't both create the playlist. */
    private val addMutex = Mutex()

    override val stateFlow: Flow<SpotifySyncState> = store.dataFlow.map { data ->
        SpotifySyncState(
            clientId = builtInClientId.ifEmpty { data.clientId },
            isClientIdBuiltIn = builtInClientId.isNotEmpty(),
            accountName = if (data.isLoggedIn) data.accountName ?: "Spotify" else null,
            enabled = data.enabled && data.isLoggedIn,
            addedCount = data.addedTrackIds.size,
        )
    }

    override suspend fun startLogin(clientId: String): String {
        val verifier = SpotifyAuth.newVerifier()
        val state = SpotifyAuth.newState()
        val id = builtInClientId.ifEmpty { clientId.trim() }
        store.update { it.copy(clientId = id, pendingVerifier = verifier, pendingState = state) }
        return SpotifyAuth.authorizeUrl(id, SpotifyAuth.challenge(verifier), state)
    }

    override suspend fun finishLogin(callbackUri: String): SpotifyLoginResult {
        val params = SpotifyAuth.parseCallback(callbackUri)
        val data = store.dataFlow.value
        params["error"]?.let { error ->
            return SpotifyLoginResult.Failure(
                if (error == "access_denied") "you didn't agree on the Spotify page" else error
            )
        }
        val code = params["code"] ?: return SpotifyLoginResult.Failure("no login code in Spotify's answer")
        val verifier = data.pendingVerifier
        if (verifier == null || params["state"] != data.pendingState) {
            return SpotifyLoginResult.Failure("this login link is outdated, start the login again")
        }
        return try {
            val response = httpClient.submitForm(
                url = SpotifyAuth.TOKEN_URL,
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", REDIRECT_URI)
                    append("client_id", data.clientId)
                    append("code_verifier", verifier)
                }
            )
            if (!response.status.isSuccess()) {
                return SpotifyLoginResult.Failure(
                    "Spotify refused the login (HTTP ${response.status.value}): ${response.bodyAsText().take(200)}"
                )
            }
            val token = response.body<TokenJson>()
            val meResponse = httpClient.get("$API/me") { bearerAuth(token.accessToken) }
            val me = if (meResponse.status.isSuccess()) meResponse.body<MeJson>() else null
            store.update {
                SpotifyStoreData(
                    clientId = it.clientId,
                    enabled = true,
                    refreshToken = token.refreshToken,
                    accessToken = token.accessToken,
                    accessTokenExpiresAtMs = expiresAt(token),
                    accountId = me?.id,
                    accountName = me?.displayName ?: me?.id,
                )
            }
            SpotifyLoginResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Spotify login failed", e)
            SpotifyLoginResult.Failure("no connection to Spotify (${e.message})")
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        store.update { it.copy(enabled = enabled) }
    }

    override suspend fun logout() {
        store.update { it.loggedOut() }
    }

    override suspend fun addTrack(track: Track): SpotifyAddResult = addMutex.withLock {
        val data = store.dataFlow.value
        if (!data.enabled || !data.isLoggedIn) return SpotifyAddResult.Skipped
        try {
            addTrackOrThrow(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LoggedOutException) {
            SpotifyAddResult.Failed("Spotify login expired, log in again in Preferences")
        } catch (e: SpotifyHttpException) {
            if (e.status == 429 || e.status >= 500) {
                SpotifyAddResult.RetryLater("Spotify busy (HTTP ${e.status})")
            } else {
                SpotifyAddResult.Failed("Spotify refused (HTTP ${e.status}): ${e.body}")
            }
        } catch (e: IOException) {
            SpotifyAddResult.RetryLater("no connection to Spotify (${e.message})")
        }
    }

    private suspend fun addTrackOrThrow(track: Track): SpotifyAddResult {
        val spotifyId = SpotifyAuth.trackIdFromLinks(track) ?: search(track) ?: return SpotifyAddResult.NotOnSpotify
        if (spotifyId in store.dataFlow.value.addedTrackIds) return SpotifyAddResult.AlreadyAdded

        var playlistId = ensurePlaylist()
        var response = postItems(playlistId, spotifyId)
        if (response.status == HttpStatusCode.NotFound) {
            // The user deleted the playlist: make a new one and try once more.
            store.update { it.copy(playlistId = null) }
            playlistId = ensurePlaylist()
            response = postItems(playlistId, spotifyId)
        }
        response.throwIfFailed()
        store.update { it.copy(addedTrackIds = (it.addedTrackIds + spotifyId).takeLast(MAX_REMEMBERED_TRACKS)) }
        return SpotifyAddResult.Added
    }

    private suspend fun postItems(playlistId: String, spotifyId: String) = authorized { token ->
        httpClient.post("$API/playlists/$playlistId/items") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(AddItemsJson(listOf("spotify:track:$spotifyId")))
        }
    }

    private suspend fun search(track: Track): String? {
        for (query in SpotifyAuth.searchQueries(track)) {
            val response = authorized { token ->
                httpClient.get("$API/search") {
                    bearerAuth(token)
                    parameter("type", "track")
                    parameter("limit", 5)
                    parameter("q", query)
                }
            }
            response.throwIfFailed()
            response.body<SearchJson>().tracks?.items?.firstNotNullOfOrNull { it?.id }?.let { return it }
        }
        return null
    }

    private suspend fun ensurePlaylist(): String {
        store.dataFlow.value.playlistId?.let { return it }
        val accountId = store.dataFlow.value.accountId
        var pageUrl: String? = "$API/me/playlists?limit=50"
        var pages = 0
        while (pageUrl != null && pages++ < MAX_PLAYLIST_PAGES) {
            val url: String = pageUrl
            val response = authorized { token -> httpClient.get(url) { bearerAuth(token) } }
            response.throwIfFailed()
            val page = response.body<PlaylistPageJson>()
            val existing = page.items.firstOrNull { playlist ->
                playlist?.name == PLAYLIST_NAME && (accountId == null || playlist.owner?.id == accountId)
            }
            if (existing != null) {
                store.update { it.copy(playlistId = existing.id) }
                return existing.id
            }
            pageUrl = page.next
        }
        val response = authorized { token ->
            httpClient.post("$API/me/playlists") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(CreatePlaylistJson(PLAYLIST_NAME, isPublic = false, description = "Songs recognized by Audile+"))
            }
        }
        response.throwIfFailed()
        val created = response.body<PlaylistJson>()
        store.update { it.copy(playlistId = created.id) }
        return created.id
    }

    /** Runs [request] with a valid access token, refreshing it once if Spotify says it expired. */
    private suspend fun authorized(request: suspend (token: String) -> HttpResponse): HttpResponse {
        val data = store.dataFlow.value
        val token = data.accessToken?.takeIf { System.currentTimeMillis() < data.accessTokenExpiresAtMs }
            ?: refreshAccessToken()
        val response = request(token)
        return if (response.status == HttpStatusCode.Unauthorized) request(refreshAccessToken()) else response
    }

    private suspend fun refreshAccessToken(): String {
        val data = store.dataFlow.value
        val refreshToken = data.refreshToken ?: throw LoggedOutException()
        val response = httpClient.submitForm(
            url = SpotifyAuth.TOKEN_URL,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", data.clientId)
            }
        )
        if (response.status == HttpStatusCode.BadRequest) {
            // invalid_grant: access was revoked or the Client ID changed.
            store.update { it.loggedOut() }
            throw LoggedOutException()
        }
        response.throwIfFailed()
        val token = response.body<TokenJson>()
        store.update {
            it.copy(
                accessToken = token.accessToken,
                accessTokenExpiresAtMs = expiresAt(token),
                refreshToken = token.refreshToken ?: it.refreshToken,
            )
        }
        return token.accessToken
    }

    private fun expiresAt(token: TokenJson) =
        System.currentTimeMillis() + token.expiresInSeconds * 1000 - EXPIRY_MARGIN_MS

    private suspend fun HttpResponse.throwIfFailed() {
        if (!status.isSuccess()) throw SpotifyHttpException(status.value, bodyAsText().take(300))
    }

    private class SpotifyHttpException(val status: Int, val body: String) : Exception("HTTP $status: $body")

    private class LoggedOutException : Exception()

    private companion object {
        const val TAG = "SpotifyPlaylistSync"
        const val API = "https://api.spotify.com/v1"
        const val MAX_REMEMBERED_TRACKS = 2000
        const val MAX_PLAYLIST_PAGES = 20
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
