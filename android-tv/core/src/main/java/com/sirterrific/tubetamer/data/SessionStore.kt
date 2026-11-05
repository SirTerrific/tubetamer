package com.sirterrific.tubetamer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.api.TubeTamerApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tubetamer")

/** What the app remembers between launches: server address, token, signed-in profile. */
data class Session(
    val serverUrl: String? = null,
    val token: String? = null,
    val profile: Profile? = null,
    /** UI language reported by the server ("en", "fr", "nb"...); null until first contact. */
    val locale: String? = null,
)

class SessionStore(context: Context, private val cipher: TokenCipher) {
    private val store = context.applicationContext.dataStore
    private val json = TubeTamerApi.DefaultJson

    val session: Flow<Session> = store.data.map { p ->
        Session(
            serverUrl = p[SERVER_URL],
            token = p[TOKEN]?.let(cipher::decrypt),
            profile = p[PROFILE]?.let { runCatching { json.decodeFromString(Profile.serializer(), it) }.getOrNull() },
            locale = p[LOCALE],
        )
    }

    suspend fun current(): Session = session.first()

    /** A new server invalidates any token issued by the old one. */
    suspend fun setServer(url: String) {
        store.edit {
            if (it[SERVER_URL] != url) {
                it.remove(TOKEN)
                it.remove(PROFILE)
            }
            it[SERVER_URL] = url
        }
    }

    suspend fun signIn(token: String, profile: Profile) {
        store.edit {
            it[TOKEN] = cipher.encrypt(token)
            it[PROFILE] = json.encodeToString(Profile.serializer(), profile)
        }
    }

    suspend fun setLocale(locale: String) {
        store.edit { it[LOCALE] = locale }
    }

    suspend fun signOut() {
        store.edit {
            it.remove(TOKEN)
            it.remove(PROFILE)
        }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token_enc")
        val PROFILE = stringPreferencesKey("profile_json")
        val LOCALE = stringPreferencesKey("locale")
    }
}
