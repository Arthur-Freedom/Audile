package com.mrsep.musicrecognizer.core.metadata.spotify

import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionProvider
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

internal fun track(
    title: String = "Love so Fleeting (Nick Monaco Remix)",
    artist: String = "Life on Planets",
    isrc: String? = null,
    spotifyLink: String? = null,
) = Track(
    id = "local-1",
    title = title,
    artist = artist,
    album = null,
    releaseDate = null,
    duration = null,
    recognizedAt = null,
    recognizedBy = RecognitionProvider.Shazam,
    recognitionDate = Instant.EPOCH,
    lyrics = null,
    isrc = isrc,
    artworkThumbUrl = null,
    artworkUrl = null,
    trackLinks = spotifyLink?.let { mapOf(MusicService.Spotify to it) } ?: emptyMap(),
    properties = Track.Properties(isFavorite = false, isViewed = false, themeSeedColor = null),
)

internal class FakeSpotifyStore(initial: SpotifyStoreData) : SpotifyStore {
    override val dataFlow = MutableStateFlow(initial)
    override suspend fun update(transform: (SpotifyStoreData) -> SpotifyStoreData) =
        transform(dataFlow.value).also { dataFlow.value = it }
}

internal fun loggedIn(
    accessToken: String = "old",
    expiresAtMs: Long = Long.MAX_VALUE,
    enabled: Boolean = true,
) = SpotifyStoreData(
    clientId = "CLIENT",
    enabled = enabled,
    refreshToken = "refresh-1",
    accessToken = accessToken,
    accessTokenExpiresAtMs = expiresAtMs,
    accountId = "me",
    accountName = "Me",
)
