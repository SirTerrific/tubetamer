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

/** One video as the TV shows it. [thumbnail] is a server path, resolve it against the base URL. */
@Serializable
data class VideoCard(
    @SerialName("video_id") val videoId: String,
    val title: String = "",
    @SerialName("channel_name") val channelName: String = "",
    @SerialName("channel_id") val channelId: String = "",
    val duration: Int = 0,
    val category: String = "fun",
    @SerialName("is_short") val isShort: Boolean = false,
    @SerialName("progress_seconds") val progressSeconds: Int = 0,
    val thumbnail: String = "",
    /** Search results and requests only: the profile's [VideoStatus], "" when never requested. */
    val status: String = "",
    @SerialName("requested_at") val requestedAt: String = "",
)

/** Request status of a video for the signed-in profile. */
object VideoStatus {
    const val APPROVED = "approved"
    const val PENDING = "pending"
    const val DENIED = "denied"
}

@Serializable
data class SearchResponse(
    val videos: List<VideoCard> = emptyList(),
    /** "fetch_failed" when a pasted link could not be read. */
    val error: String = "",
)

@Serializable
data class RequestBody(@SerialName("video_id") val videoId: String)

@Serializable
data class RequestResponse(val status: String = "", val video: VideoCard? = null)

@Serializable
data class RequestsResponse(val requests: List<VideoCard> = emptyList())

/** Row ids sent by the server, in display order. Unknown ids are shown with a generic title. */
object RowId {
    const val ACTIVE = "active"
    const val EDU = "edu"
    const val FUN = "fun"
    const val SHORTS = "shorts"
    const val ALL = "all"
}

@Serializable
data class HomeRow(
    val id: String,
    val videos: List<VideoCard> = emptyList(),
    val total: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class ChannelInfo(
    val id: String,
    val name: String,
    @SerialName("video_count") val videoCount: Int = 0,
)

@Serializable
data class HomeResponse(
    val rows: List<HomeRow> = emptyList(),
    val channels: List<ChannelInfo> = emptyList(),
    @SerialName("shorts_enabled") val shortsEnabled: Boolean = false,
)

@Serializable
data class CatalogPage(
    val videos: List<VideoCard> = emptyList(),
    val total: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class SubtitleTrack(val lang: String, val label: String = "", val url: String)

/**
 * Answer to `POST /api/v1/videos/{id}/play`. One shape for every outcome:
 * [status] "ready" (200), "pending"/"downloading" (202), or [error] on 403/404/409
 * ("not_approved", "time_up", "outside_schedule", "not_found", "local_playback_disabled").
 */
@Serializable
data class PlayResponse(
    val status: String = "",
    val error: String = "",
    val video: VideoCard? = null,
    val stream: String = "",
    val subtitles: List<SubtitleTrack> = emptyList(),
    @SerialName("resume_seconds") val resumeSeconds: Int = 0,
    @SerialName("remaining_sec") val remainingSec: Int = -1,
    @SerialName("unlock_time") val unlockTime: String = "",
    @SerialName("next_start") val nextStart: String? = null,
    val category: String = "",
    /** On time_up: other categories that still have time today. */
    val available: List<AvailableCategory> = emptyList(),
)

@Serializable
data class AvailableCategory(val category: String, @SerialName("remaining_min") val remainingMin: Double = 0.0)

@Serializable
data class DownloadStatus(val status: String = "", val percent: Double = 0.0)

@Serializable
data class HeartbeatRequest(
    @SerialName("video_id") val videoId: String,
    val seconds: Int,
    @SerialName("position_seconds") val positionSeconds: Int,
)

/** [remaining] is -1 when no limit applies. [error] is set on 403 ("outside_schedule"). */
@Serializable
data class HeartbeatResponse(
    val remaining: Int = -1,
    @SerialName("time_up") val timeUp: Boolean = false,
    val error: String = "",
)
