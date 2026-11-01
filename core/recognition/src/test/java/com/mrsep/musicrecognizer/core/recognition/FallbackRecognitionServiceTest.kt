package com.mrsep.musicrecognizer.core.recognition

import com.mrsep.musicrecognizer.core.domain.preferences.AcrCloudConfig
import com.mrsep.musicrecognizer.core.domain.preferences.AuddConfig
import com.mrsep.musicrecognizer.core.domain.preferences.ShazamConfig
import com.mrsep.musicrecognizer.core.domain.recognition.AudioSample
import com.mrsep.musicrecognizer.core.domain.recognition.RemoteRecognitionService
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecordingScheme
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** Audile+: when the chosen service finds nothing, each other service with keys gets one try. */
class FallbackRecognitionServiceTest {

    private class FakeService(private val result: RemoteRecognitionResult) : RemoteRecognitionService {
        val received = mutableListOf<AudioSample>()
        override val recordingScheme = RecordingScheme(listOf(RecordingScheme.Step(listOf(3.seconds))), encodeSteps = false)
        override suspend fun recognize(sample: AudioSample) = result.also { received += sample }
        override suspend fun recognizeUntilFirstMatch(samples: Flow<AudioSample>): RemoteRecognitionResult {
            samples.collect { received += it }
            return result
        }
    }

    /** Shazam's recording plan: 3 s and 6 s, then 3, 6 and 9 s from the 6 s mark. */
    private val shazamSamples = listOf(3, 6, 3, 6, 9).mapIndexed { i, s ->
        AudioSample(File("sample$i.wav"), Instant.EPOCH, s.seconds, 16_000, "audio/x-wav")
    }
    private val longest = shazamSamples.last()

    private val success = RemoteRecognitionResult.Success(fakeTrack)

    @Test
    fun whenShazamFindsNothingAuddGetsTheLongestSample() = runTest {
        val audd = FakeService(success)
        val acrCloud = FakeService(success)
        val service = FallbackRecognitionService(FakeService(RemoteRecognitionResult.NoMatches), listOf(audd, acrCloud))

        service.recognizeUntilFirstMatch(shazamSamples.asFlow()) shouldBe success
        audd.received shouldBe listOf(longest)
        acrCloud.received shouldBe emptyList()
    }

    @Test
    fun aMatchFromTheChosenServiceCostsNoExtraRequests() = runTest {
        val audd = FakeService(success)
        val service = FallbackRecognitionService(FakeService(success), listOf(audd))

        service.recognizeUntilFirstMatch(shazamSamples.asFlow()) shouldBe success
        audd.received shouldBe emptyList()
    }

    @Test
    fun nothingFoundAnywhereKeepsTheChosenServicesResult() = runTest {
        val audd = FakeService(RemoteRecognitionResult.NoMatches)
        val acrCloud = FakeService(RemoteRecognitionResult.Error.ApiUsageLimited)
        val service = FallbackRecognitionService(FakeService(RemoteRecognitionResult.NoMatches), listOf(audd, acrCloud))

        service.recognizeUntilFirstMatch(shazamSamples.asFlow()) shouldBe RemoteRecognitionResult.NoMatches
        (audd.received + acrCloud.received) shouldBe listOf(longest, longest)
    }

    @Test
    fun noInternetIsNotPassedOnToTheOtherServices() = runTest {
        val audd = FakeService(success)
        val service = FallbackRecognitionService(FakeService(RemoteRecognitionResult.Error.BadConnection), listOf(audd))

        service.recognizeUntilFirstMatch(shazamSamples.asFlow()) shouldBe RemoteRecognitionResult.Error.BadConnection
        audd.received shouldBe emptyList()
    }

    @Test
    fun aQueuedRecordingAlsoGetsTheOtherServices() = runTest {
        val audd = FakeService(success)
        val service = FallbackRecognitionService(FakeService(RemoteRecognitionResult.NoMatches), listOf(audd))

        service.recognize(longest) shouldBe success
        audd.received shouldBe listOf(longest)
    }

    @Test
    fun fallbackOrderSkipsTheChosenServiceAndServicesWithoutKeys() {
        val acrCloud = AcrCloudConfig("identify-eu-west-1.acrcloud.com", "key", "secret")
        val noAcrCloud = AcrCloudConfig("", "", "")

        fallbackConfigs(ShazamConfig, AuddConfig(""), acrCloud, AuddConfig(""), noAcrCloud) shouldBe listOf(acrCloud)
        fallbackConfigs(ShazamConfig, AuddConfig(""), noAcrCloud, AuddConfig("built-in"), acrCloud) shouldBe
            listOf(AuddConfig("built-in"), acrCloud)
        fallbackConfigs(AuddConfig("mine"), AuddConfig("mine"), noAcrCloud, AuddConfig("built-in"), noAcrCloud) shouldBe
            listOf(ShazamConfig)
    }
}
