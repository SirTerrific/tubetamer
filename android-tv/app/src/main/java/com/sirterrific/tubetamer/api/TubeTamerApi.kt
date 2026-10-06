package com.sirterrific.tubetamer.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Client for the TubeTamer server's /api/v1. Every call takes the base URL
 * (see [ServerUrl.normalize]) so the parent can change server without rebuilding it.
 * The bearer token is sent in a header and never logged.
 */
class TubeTamerApi(
    private val http: OkHttpClient,
    private val json: Json = DefaultJson,
) {
    suspend fun info(baseUrl: String): ApiResult<ServerInfo> =
        execute(request(baseUrl, "api/v1/info", null).get().build()) { json.decodeFromString(ServerInfo.serializer(), it) }

    suspend fun profiles(baseUrl: String): ApiResult<ProfilesResponse> =
        execute(request(baseUrl, "api/v1/profiles", null).get().build()) { json.decodeFromString(ProfilesResponse.serializer(), it) }

    suspend fun login(baseUrl: String, profileId: String, pin: String, deviceName: String): ApiResult<LoginResponse> {
        val body = json.encodeToString(LoginRequest.serializer(), LoginRequest(profileId, pin, deviceName))
        return execute(request(baseUrl, "api/v1/auth/login", null).post(body.toRequestBody(JSON_TYPE)).build()) {
            json.decodeFromString(LoginResponse.serializer(), it)
        }
    }

    suspend fun logout(baseUrl: String, token: String): ApiResult<Unit> =
        execute(request(baseUrl, "api/v1/auth/logout", token).post(EMPTY_JSON.toRequestBody(JSON_TYPE)).build()) { }

    suspend fun me(baseUrl: String, token: String): ApiResult<MeResponse> =
        execute(request(baseUrl, "api/v1/me", token).get().build()) { json.decodeFromString(MeResponse.serializer(), it) }

    suspend fun home(baseUrl: String, token: String, limit: Int = PAGE_SIZE): ApiResult<HomeResponse> =
        execute(request(baseUrl, "api/v1/home?limit=$limit", token).get().build()) {
            json.decodeFromString(HomeResponse.serializer(), it)
        }

    /** Next page of a home row, or of one channel when [channel] is set (with row [RowId.ALL]). */
    suspend fun catalog(
        baseUrl: String,
        token: String,
        row: String,
        offset: Int,
        channel: String = "",
        limit: Int = PAGE_SIZE,
    ): ApiResult<CatalogPage> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/catalog")
            .addQueryParameter("row", row)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .apply { if (channel.isNotEmpty()) addQueryParameter("channel", channel) }
            .build()
        val req = Request.Builder().url(url).header("Accept", "application/json")
            .header("Authorization", "Bearer $token").get().build()
        return execute(req) { json.decodeFromString(CatalogPage.serializer(), it) }
    }

    private fun request(baseUrl: String, path: String, token: String?): Request.Builder {
        val url = baseUrl.toHttpUrl().resolve(path) ?: throw IllegalArgumentException("Bad path $path")
        return Request.Builder().url(url).header("Accept", "application/json").apply {
            if (token != null) header("Authorization", "Bearer $token")
        }
    }

    private suspend fun <T> execute(request: Request, decode: (String) -> T): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { resp ->
                    val text = resp.body.string()
                    if (resp.isSuccessful) {
                        ApiResult.Ok(decode(text))
                    } else {
                        ApiResult.HttpError(resp.code, errorCode(text))
                    }
                }
            } catch (e: IOException) {
                ApiResult.NetworkError(e)
            } catch (e: SerializationException) {
                ApiResult.BadResponse(e)
            } catch (e: IllegalArgumentException) {
                ApiResult.BadResponse(e)
            }
        }

    private fun errorCode(text: String): String =
        runCatching { json.decodeFromString(ErrorBody.serializer(), text).error }.getOrDefault("")

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        const val PAGE_SIZE = 24
        private val JSON_TYPE = "application/json".toMediaType()
        private const val EMPTY_JSON = "{}"
    }
}
