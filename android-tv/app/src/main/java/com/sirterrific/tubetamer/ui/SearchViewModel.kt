package com.sirterrific.tubetamer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sirterrific.tubetamer.AppContainer
import com.sirterrific.tubetamer.api.ApiResult
import com.sirterrific.tubetamer.api.VideoCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    /** [fetchFailed]: a pasted link could not be read. */
    data class Results(val videos: List<VideoCard>, val fetchFailed: Boolean = false) : SearchState
    data class Failed(val error: UiError) : SearchState
}

sealed interface RequestsState {
    data object Loading : RequestsState
    data class Ready(val requests: List<VideoCard>) : RequestsState
    data class Failed(val error: UiError) : RequestsState
}

/**
 * Search, video requests and the "My requests" list. While a search or the
 * list is on screen, [startPolling] refreshes the request statuses so a parent's
 * approval shows up without leaving the screen.
 */
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _requests = MutableStateFlow<RequestsState>(RequestsState.Loading)
    val requests: StateFlow<RequestsState> = _requests.asStateFlow()

    /** Error of the last request (Ask button), shown until the next one. */
    private val _requestError = MutableStateFlow<UiError?>(null)
    val requestError: StateFlow<UiError?> = _requestError.asStateFlow()

    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    var query = ""
        private set

    private var searchJob: Job? = null
    private var pollJob: Job? = null
    private var pollers = 0

    fun search(q: String) {
        val text = q.trim()
        query = text
        if (text.isEmpty()) return
        val creds = c.credentials.get() ?: run { _expired.value = true; return }
        searchJob?.cancel()
        _search.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            when (val r = c.api.search(creds.baseUrl, creds.token, text)) {
                is ApiResult.Ok -> _search.value =
                    SearchState.Results(r.value.videos, fetchFailed = r.value.error == "fetch_failed")
                else -> fail(r) { _search.value = SearchState.Failed(it) }
            }
        }
    }

    /** Ask for [v]. The card's status follows the server's answer (pending, or approved by its channel). */
    fun request(v: VideoCard) {
        val creds = c.credentials.get() ?: run { _expired.value = true; return }
        _requestError.value = null
        viewModelScope.launch {
            when (val r = c.api.requestVideo(creds.baseUrl, creds.token, v.videoId)) {
                is ApiResult.Ok -> {
                    setStatus(mapOf(v.videoId to r.value.status))
                    refreshRequests()
                }
                else -> fail(r) { _requestError.value = it }
            }
        }
    }

    fun clearRequestError() {
        _requestError.value = null
    }

    /** Screens call this while visible, and [stopPolling] when they leave. */
    fun startPolling() {
        pollers++
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshRequests()
                delay(POLL_MS)
            }
        }
    }

    fun stopPolling() {
        pollers = (pollers - 1).coerceAtLeast(0)
        if (pollers == 0) {
            pollJob?.cancel()
            pollJob = null
        }
    }

    private suspend fun refreshRequests() {
        val creds = c.credentials.get() ?: run { _expired.value = true; return }
        when (val r = c.api.requests(creds.baseUrl, creds.token)) {
            is ApiResult.Ok -> {
                _requests.value = RequestsState.Ready(r.value.requests)
                setStatus(r.value.requests.associate { it.videoId to it.status })
            }
            // Keep showing the last list on a transient failure.
            else -> fail(r) { e -> if (_requests.value !is RequestsState.Ready) _requests.value = RequestsState.Failed(e) }
        }
    }

    private fun setStatus(statuses: Map<String, String>) {
        _search.update { s ->
            if (s is SearchState.Results) s.copy(videos = s.videos.withStatuses(statuses)) else s
        }
    }

    private fun fail(r: ApiResult<*>, onError: (UiError) -> Unit) {
        when (r) {
            is ApiResult.HttpError -> when (r.code) {
                401 -> _expired.value = true
                else -> onError(UiError.SERVER)
            }
            is ApiResult.NetworkError -> onError(UiError.UNREACHABLE)
            is ApiResult.BadResponse -> onError(UiError.SERVER)
            is ApiResult.Ok -> Unit
        }
    }

    companion object {
        private const val POLL_MS = 10_000L

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(c) }
        }
    }
}

/** Updates the status of the listed videos; videos missing from [statuses] keep theirs. */
internal fun List<VideoCard>.withStatuses(statuses: Map<String, String>): List<VideoCard> =
    map { v -> statuses[v.videoId]?.takeIf { it.isNotEmpty() && it != v.status }?.let { v.copy(status = it) } ?: v }
