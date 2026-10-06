package com.sirterrific.tubetamer

import android.content.Context
import android.os.Build
import com.sirterrific.tubetamer.api.TubeTamerApi
import com.sirterrific.tubetamer.data.AuthRepository
import com.sirterrific.tubetamer.data.SessionStore
import com.sirterrific.tubetamer.data.TokenCipher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency wiring. One instance per process, owned by [TubeTamerApp]. */
class AppContainer(context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val api = TubeTamerApi(http)
    val sessions = SessionStore(context, TokenCipher())
    val auth = AuthRepository(api, sessions, deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim())
}
