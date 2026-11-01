package com.mrsep.musicrecognizer.core.metadata.spotify

import io.kotest.matchers.shouldBe
import io.ktor.http.Url
import org.junit.Test

internal class SpotifyAuthTest {

    @Test
    fun pkceChallengeMatchesRfc7636Example() {
        // RFC 7636, appendix B
        SpotifyAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") shouldBe
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    }

    @Test
    fun loginLinkAsksOnlyForPlaylistAccessAndComesBackToTheApp() {
        val url = Url(SpotifyAuth.authorizeUrl("CLIENT", "CHALLENGE", "STATE"))
        url.host shouldBe "accounts.spotify.com"
        url.parameters["client_id"] shouldBe "CLIENT"
        url.parameters["redirect_uri"] shouldBe "audile://spotify-callback"
        url.parameters["code_challenge_method"] shouldBe "S256"
        url.parameters["code_challenge"] shouldBe "CHALLENGE"
        url.parameters["state"] shouldBe "STATE"
        url.parameters["scope"]!!.split(" ").toSet() shouldBe
            setOf("playlist-read-private", "playlist-modify-private", "playlist-modify-public")
    }

    @Test
    fun readsCodeAndStateFromTheRedirect() {
        SpotifyAuth.parseCallback("audile://spotify-callback?code=abc%2Fd&state=xyz") shouldBe
            mapOf("code" to "abc/d", "state" to "xyz")
        SpotifyAuth.parseCallback("audile://spotify-callback?error=access_denied&state=xyz")["error"] shouldBe
            "access_denied"
        SpotifyAuth.parseCallback("audile://spotify-callback") shouldBe emptyMap()
    }

    @Test
    fun searchTriesIsrcFirstThenTitleAndArtistWithoutQuotes() {
        SpotifyAuth.searchQueries(track(title = "Say \"Hi\"", artist = "Band", isrc = "GBAYE0601498")) shouldBe
            listOf("isrc:GBAYE0601498", "track:\"Say Hi\" artist:\"Band\"")
        SpotifyAuth.searchQueries(track(title = "Song", artist = "Band", isrc = " ")) shouldBe
            listOf("track:\"Song\" artist:\"Band\"")
    }

    @Test
    fun takesTheTrackIdFromAudilesSpotifyLink() {
        SpotifyAuth.trackIdFromLinks(track(spotifyLink = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b?si=x")) shouldBe
            "0VjIjW4GlUZAMYd2vXMi3b"
        SpotifyAuth.trackIdFromLinks(track()) shouldBe null
    }
}
