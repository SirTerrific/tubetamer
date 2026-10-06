package com.sirterrific.tubetamer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sirterrific.tubetamer.AppContainer
import com.sirterrific.tubetamer.api.ApiResult
import com.sirterrific.tubetamer.api.CatalogPage
import com.sirterrific.tubetamer.api.ChannelInfo
import com.sirterrific.tubetamer.api.RowId
import com.sirterrific.tubetamer.api.VideoCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A horizontal list of videos that pages in more as focus nears its end. */
data class VideoRow(
    val id: String,
    val videos: List<VideoCard>,
    val total: Int,
    val hasMore: Boolean,
    val loadingMore: Boolean = false,
)

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val rows: List<VideoRow>, val channels: List<ChannelInfo>) : HomeState
    data class Failed(val error: UiError) : HomeState
}

/** Videos of one channel, opened from the channel row. */
data class ChannelState(val channel: ChannelInfo, val row: VideoRow?, val error: UiError? = null)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val _home = MutableStateFlow<HomeState>(HomeState.Loading)
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _channel = MutableStateFlow<ChannelState?>(null)
    val channel: StateFlow<ChannelState?> = _channel.asStateFlow()

    /** Set when the server answers 401: the token was revoked or expired. */
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val creds = c.credentials.get() ?: run { _expired.value = true; return }
        if (_home.value !is HomeState.Ready) _home.value = HomeState.Loading
        viewModelScope.launch {
            when (val r = c.api.home(creds.baseUrl, creds.token)) {
                is ApiResult.Ok -> _home.value = HomeState.Ready(
                    rows = r.value.rows.map { VideoRow(it.id, it.videos, it.total, it.hasMore) },
                    channels = r.value.channels,
                )
                else -> fail(r) { _home.value = HomeState.Failed(it) }
            }
        }
    }

    /** Called when a card near the end of [rowId] gets focus. */
    fun loadMore(rowId: String) {
        val state = _home.value as? HomeState.Ready ?: return
        val row = state.rows.firstOrNull { it.id == rowId } ?: return
        if (!row.hasMore || row.loadingMore) return
        val creds = c.credentials.get() ?: return
        updateRow(rowId) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val r = c.api.catalog(creds.baseUrl, creds.token, rowId, offset = row.videos.size)
            updateRow(rowId) { it.appended(r) }
            if (r !is ApiResult.Ok) fail(r) { }
        }
    }

    fun openChannel(channel: ChannelInfo) {
        val creds = c.credentials.get() ?: return
        _channel.value = ChannelState(channel, row = null)
        viewModelScope.launch {
            when (val r = c.api.catalog(creds.baseUrl, creds.token, RowId.ALL, offset = 0, channel = channel.id)) {
                is ApiResult.Ok -> _channel.update {
                    it?.copy(row = VideoRow(channel.id, r.value.videos, r.value.total, r.value.hasMore))
                }
                else -> fail(r) { e -> _channel.update { it?.copy(error = e) } }
            }
        }
    }

    fun loadMoreChannel() {
        val state = _channel.value ?: return
        val row = state.row ?: return
        if (!row.hasMore || row.loadingMore) return
        val creds = c.credentials.get() ?: return
        _channel.value = state.copy(row = row.copy(loadingMore = true))
        viewModelScope.launch {
            val r = c.api.catalog(creds.baseUrl, creds.token, RowId.ALL, offset = row.videos.size, channel = state.channel.id)
            _channel.update { s -> s?.copy(row = s.row?.appended(r)) }
            if (r !is ApiResult.Ok) fail(r) { }
        }
    }

    fun closeChannel() {
        _channel.value = null
    }

    /** Keeps progress bars right after watching, without reloading the rows. */
    fun updateProgress(videoId: String, seconds: Int) {
        fun VideoRow.fix() = copy(videos = videos.map { if (it.videoId == videoId) it.copy(progressSeconds = seconds) else it })
        _home.update { s -> if (s is HomeState.Ready) s.copy(rows = s.rows.map { it.fix() }) else s }
        _channel.update { s -> s?.copy(row = s.row?.fix()) }
    }

    private fun updateRow(rowId: String, f: (VideoRow) -> VideoRow) {
        _home.update { s ->
            if (s is HomeState.Ready) s.copy(rows = s.rows.map { if (it.id == rowId) f(it) else it }) else s
        }
    }

    /** Maps a failed call to a UI error, or flags the session as expired on 401. */
    private fun fail(r: ApiResult<*>, onError: (UiError) -> Unit) {
        when (r) {
            is ApiResult.HttpError -> if (r.code == 401) _expired.value = true else onError(UiError.SERVER)
            is ApiResult.NetworkError -> onError(UiError.UNREACHABLE)
            is ApiResult.BadResponse -> onError(UiError.SERVER)
            is ApiResult.Ok -> Unit
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(c) }
        }
    }
}

/** Appends a page, skipping ids already shown; a failed page just stops the spinner. */
internal fun VideoRow.appended(r: ApiResult<CatalogPage>): VideoRow = when (r) {
    is ApiResult.Ok -> {
        val seen = videos.mapTo(HashSet()) { it.videoId }
        copy(
            videos = videos + r.value.videos.filter { seen.add(it.videoId) },
            total = r.value.total,
            hasMore = r.value.hasMore,
            loadingMore = false,
        )
    }
    else -> copy(loadingMore = false)
}
