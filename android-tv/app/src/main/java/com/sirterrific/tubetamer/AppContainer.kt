package com.sirterrific.tubetamer

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.sirterrific.tubetamer.api.TubeTamerApi
import com.sirterrific.tubetamer.data.AuthRepository
import com.sirterrific.tubetamer.data.SessionStore
import com.sirterrific.tubetamer.data.TokenCipher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Server address + token for the signed-in profile, kept in memory so the
 * image loader can authenticate thumbnail requests without touching DataStore.
 */
class Credentials {
    data class Value(val baseUrl: String, val token: String, internal val url: HttpUrl)

    @Volatile private var current: Value? = null

    fun get(): Value? = current

    fun set(baseUrl: String, token: String) {
        current = baseUrl.toHttpUrlOrNull()?.let { Value(baseUrl, token, it) }
    }

    fun clear() {
        current = null
    }

    /** Adds the bearer only to requests aimed at our own server, never elsewhere. */
    val interceptor = Interceptor { chain ->
        val req = chain.request()
        val c = current
        if (c != null && req.url.host == c.url.host && req.url.port == c.url.port) {
            chain.proceed(req.newBuilder().header("Authorization", "Bearer ${c.token}").build())
        } else {
            chain.proceed(req)
        }
    }
}

/** Manual dependency wiring. One instance per process, owned by [TubeTamerApp]. */
class AppContainer(context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val credentials = Credentials()

    /** Same pool as [http], plus the bearer for server thumbnails (/thumb/... needs auth). */
    private val imageHttp: OkHttpClient = http.newBuilder()
        .addInterceptor(credentials.interceptor)
        .build()

    val api = TubeTamerApi(http)
    val sessions = SessionStore(context, TokenCipher())
    val auth = AuthRepository(api, sessions, deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim())

    fun imageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttp })) }
        .crossfade(true)
        .build()
}
