package com.sirterrific.tubetamer.data

import com.sirterrific.tubetamer.api.ApiResult
import com.sirterrific.tubetamer.api.LoginResponse
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.api.TubeTamerApi

class AuthRepository(
    private val api: TubeTamerApi,
    private val sessions: SessionStore,
    private val deviceName: String,
) {
    suspend fun signIn(profile: Profile, pin: String): ApiResult<LoginResponse> {
        val url = sessions.current().serverUrl ?: return ApiResult.HttpError(0, "no_server")
        val result = api.login(url, profile.id, pin, deviceName)
        if (result is ApiResult.Ok) sessions.signIn(result.value.token, result.value.profile)
        return result
    }

    /** Revoke the token on the server when reachable, and always forget it locally. */
    suspend fun signOut() {
        val s = sessions.current()
        if (s.serverUrl != null && s.token != null) api.logout(s.serverUrl, s.token)
        sessions.signOut()
    }
}
