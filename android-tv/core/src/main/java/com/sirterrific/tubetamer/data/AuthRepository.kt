package com.sirterrific.tubetamer.data

import com.sirterrific.tubetamer.api.ApiResult
import com.sirterrific.tubetamer.api.LoginResponse
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.api.TubeTamerApi
import kotlin.coroutines.cancellation.CancellationException

class AuthRepository(
    private val api: TubeTamerApi,
    private val sessions: SessionStore,
    private val deviceName: String,
) {
    suspend fun signIn(profile: Profile, pin: String): ApiResult<LoginResponse> {
        val url = sessions.current().serverUrl ?: return ApiResult.HttpError(0, "no_server")
        val result = api.login(url, profile.id, pin, deviceName)
        if (result !is ApiResult.Ok) return result
        // The Keystore can fail (damaged key store on some TV boxes). Do not crash: report an error.
        return try {
            sessions.signIn(result.value.token, result.value.profile)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.BadResponse(e)
        }
    }

    /** Revoke the token on the server when reachable, and always forget it locally. */
    suspend fun signOut() {
        val s = sessions.current()
        if (s.serverUrl != null && s.token != null) api.logout(s.serverUrl, s.token)
        sessions.signOut()
    }
}
