package com.mrsep.musicrecognizer.feature.recognition.scheduler

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyAddResult
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyPlaylistSync
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.feature.recognition.scheduler.TrackMetadataFetchManagerImpl.Companion.buildWorkTagForTrack
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Adds a recognized track to the user's Spotify playlist; runs after [TrackLinksFetchWorker]. */
@HiltWorker
internal class SpotifyPlaylistAddWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackRepository: TrackRepository,
    private val spotifyPlaylistSync: SpotifyPlaylistSync,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val trackId = inputData.getString(INPUT_KEY_TRACK_ID) ?: return Result.failure()
        val track = trackRepository.getTrackFlow(trackId).first() ?: return Result.success()
        return when (val result = spotifyPlaylistSync.addTrack(track)) {
            is SpotifyAddResult.RetryLater -> {
                Log.w(TAG, "Attempt #$runAttemptCount: ${result.reason}")
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
            }
            is SpotifyAddResult.Failed -> {
                Log.w(TAG, result.reason)
                Result.failure()
            }
            else -> Result.success()
        }
    }

    companion object {
        const val TAG = "SpotifyPlaylistAddWorker"
        private const val MAX_ATTEMPTS = 6
        private const val INPUT_KEY_TRACK_ID = "TRACK_ID"

        fun enqueue(context: Context, trackId: String) {
            val request = OneTimeWorkRequestBuilder<SpotifyPlaylistAddWorker>()
                .addTag(TAG)
                .addTag(buildWorkTagForTrack(trackId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(Data.Builder().putString(INPUT_KEY_TRACK_ID, trackId).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("SPOTIFY_PLAYLIST_ADD#$trackId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
