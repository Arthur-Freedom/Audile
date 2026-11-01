package com.mrsep.musicrecognizer.feature.preferences.presentation.spotify

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyLoginResult
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyPlaylistSync
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives Spotify's redirect after the user agreed, finishes the login, returns to Audile. */
@AndroidEntryPoint
class SpotifyAuthCallbackActivity : ComponentActivity() {

    @Inject
    lateinit var spotifyPlaylistSync: SpotifyPlaylistSync

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callback = intent?.data?.toString()
        if (callback == null || savedInstanceState != null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val message = when (val result = spotifyPlaylistSync.finishLogin(callback)) {
                SpotifyLoginResult.Success ->
                    "Spotify connected: recognized songs now go to your “${SpotifyPlaylistSync.PLAYLIST_NAME}” playlist"
                is SpotifyLoginResult.Failure -> "Spotify login failed: ${result.message}"
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
            finish()
        }
    }
}
