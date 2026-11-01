package com.mrsep.musicrecognizer.core.metadata.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TokenJson(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresInSeconds: Long = 3600,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
internal data class MeJson(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
internal data class PlaylistJson(
    val id: String,
    val name: String? = null,
    val owner: OwnerJson? = null,
)

@Serializable
internal data class OwnerJson(val id: String? = null)

@Serializable
internal data class PlaylistPageJson(
    val items: List<PlaylistJson?> = emptyList(),
    val next: String? = null,
)

@Serializable
internal data class CreatePlaylistJson(
    val name: String,
    @SerialName("public") val isPublic: Boolean,
    val description: String,
)

@Serializable
internal data class AddItemsJson(val uris: List<String>)

@Serializable
internal data class SearchJson(val tracks: SearchTracksJson? = null)

@Serializable
internal data class SearchTracksJson(val items: List<SearchTrackJson?> = emptyList())

@Serializable
internal data class SearchTrackJson(val id: String)
