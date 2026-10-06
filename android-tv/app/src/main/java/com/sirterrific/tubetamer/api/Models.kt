package com.sirterrific.tubetamer.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** API version this app speaks. The server reports its own in [ServerInfo.apiVersion]. */
const val SUPPORTED_API_VERSION = 1

@Serializable
data class ServerInfo(
    val app: String = "",
    val version: String = "",
    @SerialName("api_version") val apiVersion: Int = 0,
    @SerialName("local_playback") val localPlayback: Boolean = false,
    val locale: String = "en",
)

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_icon") val avatarIcon: String = "",
    @SerialName("avatar_color") val avatarColor: String = "",
    @SerialName("has_pin") val hasPin: Boolean = false,
)

@Serializable
data class ProfilesResponse(val profiles: List<Profile> = emptyList())

@Serializable
data class LoginRequest(
    @SerialName("profile_id") val profileId: String,
    val pin: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: String = "",
    val profile: Profile,
)

@Serializable
data class MeResponse(val profile: Profile)

@Serializable
internal data class ErrorBody(val error: String = "")
