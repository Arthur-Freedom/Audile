package com.mrsep.musicrecognizer.feature.preferences.presentation.spotify

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyPlaylistSync
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyPlaylistSync.Companion.PLAYLIST_NAME
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifyPlaylistSync.Companion.REDIRECT_URI
import com.mrsep.musicrecognizer.core.domain.spotify.SpotifySyncState
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceClickableItem
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@HiltViewModel
internal class SpotifyPlaylistViewModel @Inject constructor(
    private val spotifyPlaylistSync: SpotifyPlaylistSync,
) : ViewModel() {

    val stateFlow = spotifyPlaylistSync.stateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun loginUrl(clientId: String): String = spotifyPlaylistSync.startLogin(clientId)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { spotifyPlaylistSync.setEnabled(enabled) }
    }

    fun logout() {
        viewModelScope.launch { spotifyPlaylistSync.logout() }
    }
}

@Composable
internal fun SpotifyPlaylistPreference(viewModel: SpotifyPlaylistViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    var showDialog by rememberSaveable { mutableStateOf(false) }

    PreferenceGroup(title = "Spotify") {
        PreferenceClickableItem(
            title = "Spotify playlist",
            subtitle = state?.subtitle(),
            onItemClick = { showDialog = true },
        )
    }
    val current = state
    if (showDialog && current != null) {
        SpotifyPlaylistDialog(
            state = current,
            onLoginClick = { clientId ->
                scope.launch {
                    val url = viewModel.loginUrl(clientId)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                        .onFailure { Toast.makeText(context, "No browser found to open Spotify", Toast.LENGTH_LONG).show() }
                }
            },
            onEnabledChange = viewModel::setEnabled,
            onLogoutClick = viewModel::logout,
            onDismiss = { showDialog = false },
        )
    }
}

private fun SpotifySyncState.subtitle(): String = when {
    accountName == null -> "Off. Log in to add every recognized song to a playlist"
    !enabled -> "Paused, logged in as $accountName"
    else -> "Adds recognized songs to “$PLAYLIST_NAME” ($addedCount added)"
}

@Composable
private fun SpotifyPlaylistDialog(
    state: SpotifySyncState,
    onLoginClick: (clientId: String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    var clientId by rememberSaveable(state.clientId) { mutableStateOf(state.clientId) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        title = { Text(text = "Spotify playlist") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(StringsR.string.close)) }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(text = "Every song Audile+ recognizes is added to your Spotify playlist “$PLAYLIST_NAME”.")
                if (state.accountName == null) {
                    if (!state.isClientIdBuiltIn) {
                        Text(text = "This uses your own Spotify developer app (Spotify requires Premium for those).")
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it; error = null },
                            label = { Text(text = "Client ID") },
                            singleLine = true,
                            isError = error != null,
                            supportingText = {
                                Text(text = error ?: "developer.spotify.com, your app, Settings. Redirect URI: $REDIRECT_URI")
                            },
                        )
                    }
                    Button(onClick = {
                        if (clientId.isBlank()) error = "Paste your Client ID first" else onLoginClick(clientId)
                    }) {
                        Text(text = "Log in to Spotify")
                    }
                } else {
                    Text(text = "Logged in as ${state.accountName}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Add recognized songs", modifier = Modifier.weight(1f))
                        Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
                    }
                    Text(text = "${state.addedCount} songs added so far")
                    TextButton(onClick = onLogoutClick) { Text(text = "Log out of Spotify") }
                }
            }
        },
        onDismissRequest = onDismiss,
    )
}
