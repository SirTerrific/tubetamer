package com.sirterrific.tubetamer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sirterrific.tubetamer.AppContainer
import com.sirterrific.tubetamer.api.ApiResult
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.api.SUPPORTED_API_VERSION
import com.sirterrific.tubetamer.api.ServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the last action failed, mapped to a string resource by the UI. */
enum class UiError { BAD_ADDRESS, UNREACHABLE, NOT_TUBETAMER, TOO_OLD, WRONG_PIN, TOO_MANY_TRIES, SERVER }

sealed interface Screen {
    data object Loading : Screen
    data class ServerSetup(val current: String = "", val error: UiError? = null, val busy: Boolean = false) : Screen
    data class Profiles(val profiles: List<Profile>) : Screen
    data class Pin(val profile: Profile, val error: UiError? = null, val busy: Boolean = false) : Screen
    data class Home(val profile: Profile) : Screen

    /** Server unreachable at startup; keep the saved session and let the user retry. */
    data class Offline(val error: UiError) : Screen
}

class AppViewModel(private val c: AppContainer) : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    init {
        start()
    }

    fun start() {
        _screen.value = Screen.Loading
        viewModelScope.launch {
            val s = c.sessions.current()
            val url = s.serverUrl
            if (url == null) {
                _screen.value = Screen.ServerSetup()
                return@launch
            }
            if (s.token == null) {
                loadProfiles(url)
                return@launch
            }
            when (val me = c.api.me(url, s.token)) {
                is ApiResult.Ok -> _screen.value = Screen.Home(me.value.profile)
                is ApiResult.HttpError ->
                    if (me.code == 401) {
                        c.sessions.signOut()
                        loadProfiles(url)
                    } else {
                        _screen.value = Screen.Offline(UiError.SERVER)
                    }
                is ApiResult.NetworkError -> _screen.value = Screen.Offline(UiError.UNREACHABLE)
                is ApiResult.BadResponse -> _screen.value = Screen.Offline(UiError.NOT_TUBETAMER)
            }
        }
    }

    fun editServer() {
        viewModelScope.launch {
            _screen.value = Screen.ServerSetup(current = c.sessions.current().serverUrl.orEmpty())
        }
    }

    fun connect(input: String) {
        val url = ServerUrl.normalize(input)
        if (url == null) {
            _screen.value = Screen.ServerSetup(input, UiError.BAD_ADDRESS)
            return
        }
        _screen.value = Screen.ServerSetup(input, busy = true)
        viewModelScope.launch {
            val error = when (val info = c.api.info(url)) {
                is ApiResult.Ok -> when {
                    info.value.app.lowercase() != "tubetamer" -> UiError.NOT_TUBETAMER
                    info.value.apiVersion < SUPPORTED_API_VERSION -> UiError.TOO_OLD
                    else -> null
                }
                is ApiResult.HttpError -> if (info.code == 404) UiError.TOO_OLD else UiError.NOT_TUBETAMER
                is ApiResult.NetworkError -> UiError.UNREACHABLE
                is ApiResult.BadResponse -> UiError.NOT_TUBETAMER
            }
            if (error != null) {
                _screen.value = Screen.ServerSetup(input, error)
            } else {
                c.sessions.setServer(url)
                loadProfiles(url)
            }
        }
    }

    fun pickProfile(profile: Profile) {
        if (profile.hasPin) {
            _screen.value = Screen.Pin(profile)
        } else {
            submitPin(profile, "")
        }
    }

    fun submitPin(profile: Profile, pin: String) {
        _screen.value = Screen.Pin(profile, busy = true)
        viewModelScope.launch {
            when (val r = c.auth.signIn(profile, pin)) {
                is ApiResult.Ok -> _screen.value = Screen.Home(r.value.profile)
                is ApiResult.HttpError -> _screen.value = Screen.Pin(
                    profile,
                    when (r.code) {
                        401 -> UiError.WRONG_PIN
                        429 -> UiError.TOO_MANY_TRIES
                        else -> UiError.SERVER
                    },
                )
                is ApiResult.NetworkError -> _screen.value = Screen.Pin(profile, UiError.UNREACHABLE)
                is ApiResult.BadResponse -> _screen.value = Screen.Pin(profile, UiError.SERVER)
            }
        }
    }

    fun backToProfiles() {
        viewModelScope.launch { c.sessions.current().serverUrl?.let { loadProfiles(it) } }
    }

    fun signOut() {
        _screen.value = Screen.Loading
        viewModelScope.launch {
            c.auth.signOut()
            c.sessions.current().serverUrl?.let { loadProfiles(it) } ?: run { _screen.value = Screen.ServerSetup() }
        }
    }

    private suspend fun loadProfiles(url: String) {
        when (val r = c.api.profiles(url)) {
            is ApiResult.Ok -> _screen.update { Screen.Profiles(r.value.profiles) }
            is ApiResult.NetworkError -> _screen.value = Screen.Offline(UiError.UNREACHABLE)
            else -> _screen.value = Screen.Offline(UiError.SERVER)
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(c) }
        }
    }
}
