package com.mrsep.musicrecognizer.core.domain.spotify

import com.mrsep.musicrecognizer.core.domain.track.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Adds recognized tracks to a private playlist in the user's Spotify account,
 * using the user's own Spotify developer app (Client ID, PKCE login, no secret).
 */
interface SpotifyPlaylistSync {

    val stateFlow: Flow<SpotifySyncState>

    /**
     * Returns the Spotify login page to open in a browser. Uses the built-in Client ID if the app
     * has one, else remembers the pasted [clientId].
     */
    suspend fun startLogin(clientId: String): String

    /** Handles the redirect Spotify sends back after the user agreed (see [REDIRECT_URI]). */
    suspend fun finishLogin(callbackUri: String): SpotifyLoginResult

    suspend fun setEnabled(enabled: Boolean)

    suspend fun logout()

    suspend fun addTrack(track: Track): SpotifyAddResult

    companion object {
        const val REDIRECT_URI = "audile://spotify-callback"
        const val PLAYLIST_NAME = "Audile+"
    }
}

data class SpotifySyncState(
    val clientId: String,
    /** The Client ID ships with the app, so there is nothing to paste. */
    val isClientIdBuiltIn: Boolean,
    /** Null while logged out. */
    val accountName: String?,
    val enabled: Boolean,
    val addedCount: Int,
)

sealed interface SpotifyLoginResult {
    data object Success : SpotifyLoginResult
    data class Failure(val message: String) : SpotifyLoginResult
}

sealed interface SpotifyAddResult {
    /** [spotifyLink] is the song on Spotify, also when it was found by search instead of a link. */
    data class Added(val spotifyLink: String) : SpotifyAddResult
    data class AlreadyAdded(val spotifyLink: String) : SpotifyAddResult
    /** Sync is switched off or the user is logged out. */
    data object Skipped : SpotifyAddResult
    data object NotOnSpotify : SpotifyAddResult
    /** Network trouble, rate limit or a Spotify server error: worth another try. */
    data class RetryLater(val reason: String) : SpotifyAddResult
    data class Failed(val reason: String) : SpotifyAddResult
}
