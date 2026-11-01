package com.mrsep.musicrecognizer.core.metadata.spotify

import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyAddResult
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyLoginResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/** Runs SpotifyPlaylistSyncImpl against a fake Spotify that records every request. */
internal class SpotifyPlaylistSyncImplTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun sync(
        store: FakeSpotifyStore,
        builtInClientId: String = "",
        spotify: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): SpotifyPlaylistSyncImpl {
        val client = HttpClient(MockEngine { request -> requests += request; spotify(request) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
        return SpotifyPlaylistSyncImpl(store, client, builtInClientId)
    }

    @Test
    fun loginUsesTheBuiltInClientIdSoNothingNeedsPasting() = runTest {
        val store = FakeSpotifyStore(SpotifyStoreData(clientId = "OLD_TYPO"))
        val sync = sync(store, builtInClientId = "BUILT_IN") { error("no request expected") }

        sync.stateFlow.first().let { it.clientId to it.isClientIdBuiltIn } shouldBe ("BUILT_IN" to true)
        sync.startLogin("") shouldContain "client_id=BUILT_IN"
        store.dataFlow.value.clientId shouldBe "BUILT_IN"
    }

    @Test
    fun withoutABuiltInClientIdThePastedOneIsUsed() = runTest {
        val store = FakeSpotifyStore(SpotifyStoreData())
        val sync = sync(store) { error("no request expected") }

        sync.stateFlow.first().isClientIdBuiltIn shouldBe false
        sync.startLogin(" PASTED ") shouldContain "client_id=PASTED"
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.bodyText() = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun HttpRequestData.call() = "${method.value} ${url.encodedPath}"

    @Test
    fun addsTheExactLinkedTrackAndCreatesThePrivatePlaylistOnce() = runTest {
        val store = FakeSpotifyStore(loggedIn())
        val sync = sync(store) { request ->
            when (request.call()) {
                "GET /v1/me/playlists" -> json("""{"items":[{"id":"x","name":"Liked","owner":{"id":"me"}}],"next":null}""")
                "POST /v1/me/playlists" -> json("""{"id":"pl1","name":"Audile"}""", HttpStatusCode.Created)
                "POST /v1/playlists/pl1/items" -> json("""{"snapshot_id":"s"}""", HttpStatusCode.Created)
                else -> error("unexpected ${request.call()}")
            }
        }
        val linked = track(spotifyLink = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b")

        sync.addTrack(linked) shouldBe SpotifyAddResult.Added
        requests.map { it.call() } shouldBe listOf("GET /v1/me/playlists", "POST /v1/me/playlists", "POST /v1/playlists/pl1/items")
        requests[1].bodyText() shouldContain """"public":false"""
        requests[2].bodyText() shouldBe """{"uris":["spotify:track:0VjIjW4GlUZAMYd2vXMi3b"]}"""
        requests.forEach { it.headers[HttpHeaders.Authorization] shouldBe "Bearer old" }

        // Same song recognized again: nothing sent. Another song: the playlist is reused.
        requests.clear()
        sync.addTrack(linked) shouldBe SpotifyAddResult.AlreadyAdded
        requests shouldBe emptyList()
        sync.addTrack(track(spotifyLink = "https://open.spotify.com/track/1111111111111111111111")) shouldBe SpotifyAddResult.Added
        requests.map { it.call() } shouldBe listOf("POST /v1/playlists/pl1/items")
        sync.stateFlow.first().addedCount shouldBe 2
    }

    @Test
    fun usesAnExistingAudilePlaylistOwnedByTheUser() = runTest {
        val store = FakeSpotifyStore(loggedIn())
        val sync = sync(store) { request ->
            when (request.call()) {
                "GET /v1/me/playlists" -> json(
                    """{"items":[{"id":"theirs","name":"Audile","owner":{"id":"someone"}},{"id":"mine","name":"Audile","owner":{"id":"me"}}]}"""
                )
                "POST /v1/playlists/mine/items" -> json("{}", HttpStatusCode.Created)
                else -> error("unexpected ${request.call()}")
            }
        }
        sync.addTrack(track(spotifyLink = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b")) shouldBe SpotifyAddResult.Added
        store.dataFlow.value.playlistId shouldBe "mine"
    }

    @Test
    fun searchesByIsrcWhenAudileHasNoSpotifyLink() = runTest {
        val store = FakeSpotifyStore(loggedIn().copy(playlistId = "pl1"))
        val sync = sync(store) { request ->
            when (request.call()) {
                "GET /v1/search" -> json("""{"tracks":{"items":[{"id":"FROMISRC0000000000000000"}]}}""")
                "POST /v1/playlists/pl1/items" -> json("{}", HttpStatusCode.Created)
                else -> error("unexpected ${request.call()}")
            }
        }
        sync.addTrack(track(isrc = "GBAYE0601498")) shouldBe SpotifyAddResult.Added
        requests[0].url.parameters["q"] shouldBe "isrc:GBAYE0601498"
        requests[1].bodyText() shouldContain "spotify:track:FROMISRC0000000000000000"
    }

    @Test
    fun songNotOnSpotifyIsReportedNotAdded() = runTest {
        val store = FakeSpotifyStore(loggedIn().copy(playlistId = "pl1"))
        val sync = sync(store) { json("""{"tracks":{"items":[]}}""") }
        sync.addTrack(track(isrc = "XX0000000000")) shouldBe SpotifyAddResult.NotOnSpotify
        requests.map { it.url.parameters["q"] } shouldBe
            listOf("isrc:XX0000000000", "track:\"Love so Fleeting (Nick Monaco Remix)\" artist:\"Life on Planets\"")
    }

    @Test
    fun expiredLoginIsRefreshedAndTheRequestRepeated() = runTest {
        val store = FakeSpotifyStore(loggedIn(accessToken = "old").copy(playlistId = "pl1"))
        val sync = sync(store) { request ->
            when {
                request.url.host == "accounts.spotify.com" -> json("""{"access_token":"new","expires_in":3600}""")
                request.headers[HttpHeaders.Authorization] == "Bearer old" -> json("{}", HttpStatusCode.Unauthorized)
                else -> json("{}", HttpStatusCode.Created)
            }
        }
        sync.addTrack(track(spotifyLink = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b")) shouldBe SpotifyAddResult.Added
        requests.map { it.url.host to it.headers[HttpHeaders.Authorization] } shouldBe listOf(
            "api.spotify.com" to "Bearer old",
            "accounts.spotify.com" to null,
            "api.spotify.com" to "Bearer new",
        )
        requests[1].bodyText() shouldContain "grant_type=refresh_token"
        store.dataFlow.value.accessToken shouldBe "new"
        store.dataFlow.value.refreshToken shouldBe "refresh-1" // Spotify may omit a new one
    }

    @Test
    fun revokedAccessLogsOutInsteadOfRetryingForever() = runTest {
        val store = FakeSpotifyStore(loggedIn(expiresAtMs = 0))
        val sync = sync(store) { json("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) }
        sync.addTrack(track(spotifyLink = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b"))
            .shouldBeInstanceOf<SpotifyAddResult.Failed>()
        store.dataFlow.value shouldBe SpotifyStoreData(clientId = "CLIENT")
        sync.stateFlow.first().accountName shouldBe null
    }

    @Test
    fun rateLimitAndServerErrorsMeanTryAgainLater() = runTest {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.BadGateway)) {
            val store = FakeSpotifyStore(loggedIn().copy(playlistId = "pl1"))
            sync(store) { json("{}", status) }
                .addTrack(track(spotifyLink = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b"))
                .shouldBeInstanceOf<SpotifyAddResult.RetryLater>()
            store.dataFlow.value.addedTrackIds shouldBe emptyList()
        }
    }

    @Test
    fun switchedOffSendsNothing() = runTest {
        val sync = sync(FakeSpotifyStore(loggedIn(enabled = false))) { error("must not call Spotify") }
        sync.addTrack(track(spotifyLink = "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b")) shouldBe SpotifyAddResult.Skipped
        requests shouldBe emptyList()
    }

    @Test
    fun loginExchangesTheCodeWithTheMatchingVerifierAndSwitchesSyncOn() = runTest {
        val store = FakeSpotifyStore(SpotifyStoreData())
        val sync = sync(store) { request ->
            when (request.url.host) {
                "accounts.spotify.com" -> json("""{"access_token":"a","expires_in":3600,"refresh_token":"r"}""")
                else -> json("""{"id":"me","display_name":"Test User"}""")
            }
        }
        val loginUrl = Url(sync.startLogin("  CLIENT  "))
        loginUrl.parameters["client_id"] shouldBe "CLIENT"
        val verifier = store.dataFlow.value.pendingVerifier!!
        loginUrl.parameters["code_challenge"] shouldBe SpotifyAuth.challenge(verifier)

        sync.finishLogin("audile://spotify-callback?code=CODE&state=${loginUrl.parameters["state"]}") shouldBe
            SpotifyLoginResult.Success
        val tokenRequest = requests.first()
        tokenRequest.method shouldBe HttpMethod.Post
        tokenRequest.bodyText() shouldContain "code_verifier=$verifier"
        tokenRequest.bodyText() shouldContain "redirect_uri=audile%3A%2F%2Fspotify-callback"
        sync.stateFlow.first().let { it.accountName to it.enabled } shouldBe ("Test User" to true)
        store.dataFlow.value.pendingVerifier shouldBe null
    }

    @Test
    fun loginAnswerFromAnotherAttemptIsRejectedWithoutCallingSpotify() = runTest {
        val store = FakeSpotifyStore(SpotifyStoreData())
        val sync = sync(store) { error("must not call Spotify") }
        sync.startLogin("CLIENT")
        sync.finishLogin("audile://spotify-callback?code=CODE&state=forged").shouldBeInstanceOf<SpotifyLoginResult.Failure>()
        sync.finishLogin("audile://spotify-callback?error=access_denied&state=x").shouldBeInstanceOf<SpotifyLoginResult.Failure>()
        requests shouldBe emptyList()
    }
}
