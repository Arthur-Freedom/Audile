package com.mrsep.musicrecognizer.core.domain.recognition

import com.mrsep.musicrecognizer.core.domain.preferences.AcrCloudConfig
import com.mrsep.musicrecognizer.core.domain.preferences.AuddConfig
import com.mrsep.musicrecognizer.core.domain.preferences.RecognitionServiceConfig

interface RecognitionServiceFactory {

    fun getService(config: RecognitionServiceConfig): RemoteRecognitionService

    /**
     * Audile+: the [primary] service, which asks the other services that have keys when it finds
     * nothing ([audd] and [acrCloud] are the user's settings; blank ones use the built-in keys).
     */
    fun getServiceWithFallbacks(
        primary: RecognitionServiceConfig,
        audd: AuddConfig,
        acrCloud: AcrCloudConfig,
    ): RemoteRecognitionService
}
