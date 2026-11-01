package com.mrsep.musicrecognizer.core.recognition

import com.mrsep.musicrecognizer.core.domain.recognition.AudioSample
import com.mrsep.musicrecognizer.core.domain.recognition.RemoteRecognitionService
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Audile+: when [primary] finds nothing, sends the longest sample once to each of [fallbacks]
 * and returns the first match. Some songs are known to only one of Shazam, AudD and ACRCloud.
 * The extra requests only happen on a miss, so a match costs no AudD or ACRCloud credits.
 */
internal class FallbackRecognitionService(
    private val primary: RemoteRecognitionService,
    private val fallbacks: List<RemoteRecognitionService>,
) : RemoteRecognitionService {

    override val recordingScheme get() = primary.recordingScheme

    override suspend fun recognize(sample: AudioSample) = askOthersIfMissed(primary.recognize(sample), sample)

    override suspend fun recognizeUntilFirstMatch(samples: Flow<AudioSample>): RemoteRecognitionResult {
        var longest: AudioSample? = null
        val result = primary.recognizeUntilFirstMatch(
            samples.onEach { sample -> if (longest.let { it == null || sample.duration >= it.duration }) longest = sample }
        )
        return askOthersIfMissed(result, longest)
    }

    private suspend fun askOthersIfMissed(result: RemoteRecognitionResult, sample: AudioSample?): RemoteRecognitionResult {
        if (sample == null || !result.isMiss()) return result
        for (service in fallbacks) {
            val next = service.recognize(sample)
            if (next is RemoteRecognitionResult.Success) return next
        }
        return result
    }

    /** No match, or the service itself failed. Not for no internet or an unusable recording. */
    private fun RemoteRecognitionResult.isMiss() = when (this) {
        RemoteRecognitionResult.NoMatches,
        RemoteRecognitionResult.Error.AuthError,
        RemoteRecognitionResult.Error.ApiUsageLimited,
        is RemoteRecognitionResult.Error.HttpError,
        is RemoteRecognitionResult.Error.UnhandledError -> true
        else -> false
    }
}
