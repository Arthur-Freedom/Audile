package com.mrsep.musicrecognizer.core.metadata.spotify

import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyPlaylistSync
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.metadata.MusicServiceUtils
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Login (OAuth authorization code + PKCE, no client secret) and track lookup helpers. */
internal object SpotifyAuth {

    const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
    const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    val SCOPES = listOf("playlist-read-private", "playlist-modify-private", "playlist-modify-public")

    private val random = SecureRandom()

    fun newVerifier(): String = base64Url(ByteArray(64).also(random::nextBytes))

    fun newState(): String = base64Url(ByteArray(16).also(random::nextBytes))

    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun authorizeUrl(clientId: String, challenge: String, state: String): String =
        URLBuilder(AUTHORIZE_URL).apply {
            parameters.append("client_id", clientId)
            parameters.append("response_type", "code")
            parameters.append("redirect_uri", SpotifyPlaylistSync.REDIRECT_URI)
            parameters.append("scope", SCOPES.joinToString(" "))
            parameters.append("code_challenge_method", "S256")
            parameters.append("code_challenge", challenge)
            parameters.append("state", state)
        }.buildString()

    /** Query parameters of the redirect, e.g. audile://spotify-callback?code=...&state=... */
    fun parseCallback(uri: String): Map<String, String> {
        val query = uri.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        return parseQueryString(query).entries().associate { (key, values) -> key to values.first() }
    }

    /** The exact track when Audile already found its Spotify link. */
    fun trackIdFromLinks(track: Track): String? =
        track.trackLinks[MusicService.Spotify]?.let { MusicServiceUtils.parseTrackId(it, MusicService.Spotify) }

    /** Search queries to try in order: ISRC is exact, title + artist is the fallback. */
    fun searchQueries(track: Track): List<String> = listOfNotNull(
        track.isrc?.trim()?.takeIf { it.isNotEmpty() }?.let { "isrc:$it" },
        "track:\"${track.title.withoutQuotes()}\" artist:\"${track.artist.withoutQuotes()}\"",
    )

    private fun String.withoutQuotes() = replace("\"", "").trim()

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
