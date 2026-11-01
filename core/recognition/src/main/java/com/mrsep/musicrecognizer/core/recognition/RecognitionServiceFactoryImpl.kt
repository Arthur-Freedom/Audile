package com.mrsep.musicrecognizer.core.recognition

import com.mrsep.musicrecognizer.core.domain.preferences.AcrCloudConfig
import com.mrsep.musicrecognizer.core.domain.preferences.AuddConfig
import com.mrsep.musicrecognizer.core.domain.preferences.RecognitionServiceConfig
import com.mrsep.musicrecognizer.core.domain.preferences.ShazamConfig
import com.mrsep.musicrecognizer.core.domain.recognition.RecognitionServiceFactory
import com.mrsep.musicrecognizer.core.domain.recognition.RemoteRecognitionService
import com.mrsep.musicrecognizer.core.recognition.acrcloud.AcrCloudRecognitionService
import com.mrsep.musicrecognizer.core.recognition.audd.AuddRecognitionService
import com.mrsep.musicrecognizer.core.recognition.shazam.ShazamRecognitionService
import javax.inject.Inject

internal class RecognitionServiceFactoryImpl @Inject constructor(
    private val acrCloudRecognitionServiceFactory: AcrCloudRecognitionService.Factory,
    private val auddRecognitionServiceFactory: AuddRecognitionService.Factory,
    private val shazamRecognitionService: ShazamRecognitionService,
) : RecognitionServiceFactory {

    override fun getService(config: RecognitionServiceConfig): RemoteRecognitionService {
        return when (config) {
            is AuddConfig -> auddRecognitionServiceFactory.create(config)
            is AcrCloudConfig -> acrCloudRecognitionServiceFactory.create(config)
            ShazamConfig -> shazamRecognitionService
        }
    }

    override fun getServiceWithFallbacks(
        primary: RecognitionServiceConfig,
        audd: AuddConfig,
        acrCloud: AcrCloudConfig,
    ): RemoteRecognitionService {
        val fallbacks = fallbackConfigs(primary, audd, acrCloud).map(::getService)
        return if (fallbacks.isEmpty()) getService(primary) else FallbackRecognitionService(getService(primary), fallbacks)
    }
}

/**
 * Audile+: Shazam, AudD, ACRCloud in that order, without [primary] and without services that
 * have no keys. A service left blank in the settings uses the keys built into the app.
 */
internal fun fallbackConfigs(
    primary: RecognitionServiceConfig,
    audd: AuddConfig,
    acrCloud: AcrCloudConfig,
    builtInAudd: AuddConfig = AuddConfig(BuildConfig.AUDD_API_TOKEN),
    builtInAcrCloud: AcrCloudConfig = AcrCloudConfig(BuildConfig.ACR_HOST, BuildConfig.ACR_ACCESS_KEY, BuildConfig.ACR_ACCESS_SECRET),
): List<RecognitionServiceConfig> = listOf(
    ShazamConfig,
    audd.takeIf { it.hasKeys() } ?: builtInAudd,
    acrCloud.takeIf { it.hasKeys() } ?: builtInAcrCloud,
).filter { it::class != primary::class && it.hasKeys() }

private fun RecognitionServiceConfig.hasKeys() = when (this) {
    is AuddConfig -> apiToken.isNotBlank()
    is AcrCloudConfig -> host.isNotBlank() && accessKey.isNotBlank() && accessSecret.isNotBlank()
    ShazamConfig -> true
}
