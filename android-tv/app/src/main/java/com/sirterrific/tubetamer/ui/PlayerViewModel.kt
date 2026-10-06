package com.sirterrific.tubetamer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sirterrific.tubetamer.AppContainer
import com.sirterrific.tubetamer.Credentials
import com.sirterrific.tubetamer.api.ApiResult
import com.sirterrific.tubetamer.api.HeartbeatRequest
import com.sirterrific.tubetamer.api.PlayResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why a video cannot be played right now. */
enum class Block { TIME_UP, OUTSIDE_SCHEDULE, NOT_APPROVED, NO_LOCAL_PLAYBACK, NOT_FOUND, DOWNLOAD_FAILED }

data class Subtitle(val url: String, val lang: String, val label: String)

sealed interface PlayerState {
    data object Starting : PlayerState

    /** The server is downloading the file. [percent] is null until it reports progress. */
    data class Preparing(val percent: Int?) : PlayerState

    data class Ready(
        val videoId: String,
        val streamUrl: String,
        val subtitles: List<Subtitle>,
        val startMs: Long,
        val headers: Map<String, String>,
    ) : PlayerState

    /** [detail] is the server's unlock or next start time, already formatted, or "". */
    data class Blocked(val block: Block, val detail: String = "", val otherCategories: Boolean = false) : PlayerState

    data class Failed(val error: UiError) : PlayerState
}

/**
 * One video at a time: asks the server to play it, waits for the download,
 * then reports watch time every [HEARTBEAT_EVERY] seconds of actual playback.
 * The server decides budgets and schedule; the player stops when it says so.
 */
class PlayerViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Starting)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Budget seconds left, -1 when unlimited. Counted down locally between heartbeats. */
    private val _remaining = MutableStateFlow(-1)
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    private var videoId = ""
    private var job: Job? = null
    private var played = 0
    private var reporting = false

    fun start(id: String) {
        videoId = id
        played = 0
        reporting = false
        _remaining.value = -1
        _expired.value = false
        job?.cancel()
        _state.value = PlayerState.Starting
        job = viewModelScope.launch { open() }
    }

    fun retry() = start(videoId)

    /** Leaving the player: drop the old state so the next video never shows it, even for a frame. */
    fun stop() {
        job?.cancel()
        job = null
        _state.value = PlayerState.Starting
        _remaining.value = -1
    }

    private suspend fun open() {
        val creds = c.credentials.get() ?: run { _expired.value = true; return }
        while (true) {
            val p = when (val r = c.api.play(creds.baseUrl, creds.token, videoId)) {
                is ApiResult.Ok -> r.value
                else -> { fail(r); return }
            }
            when (p.status) {
                "ready" -> {
                    _remaining.value = p.remainingSec
                    _state.value = ready(creds, p)
                    return
                }
                "pending", "downloading" -> if (!waitForDownload(creds)) return
                else -> { _state.value = blocked(p); return }
            }
        }
    }

    /** Polls the download until the file is ready (true), or sets a final state (false). */
    private suspend fun waitForDownload(creds: Credentials.Value): Boolean {
        _state.value = PlayerState.Preparing(null)
        while (true) {
            delay(POLL_MS)
            when (val r = c.api.downloadStatus(creds.baseUrl, creds.token, videoId)) {
                is ApiResult.Ok -> when (r.value.status) {
                    "ready" -> return true
                    "failed" -> { _state.value = PlayerState.Blocked(Block.DOWNLOAD_FAILED); return false }
                    "disabled" -> { _state.value = PlayerState.Blocked(Block.NO_LOCAL_PLAYBACK); return false }
                    "downloading" -> _state.value = PlayerState.Preparing(r.value.percent.toInt().coerceIn(0, 100))
                    else -> Unit // queued, or status not written yet
                }
                is ApiResult.HttpError -> if (r.code == 401) { _expired.value = true; return false }
                else -> Unit // transient network trouble: keep waiting
            }
        }
    }

    /** One second of actual playback, at [positionSec]. */
    fun tick(positionSec: Int) {
        played++
        if (_remaining.value > 0) _remaining.value -= 1
        if (played >= HEARTBEAT_EVERY || _remaining.value == 0) report(positionSec)
    }

    /** Sends the seconds played since the last report. [final] = leaving the player. */
    fun report(positionSec: Int, final: Boolean = false, id: String = videoId) {
        if (reporting && !final) return
        val creds = c.credentials.get() ?: return
        val secs = played
        if (secs == 0 && !final) return
        played = 0
        reporting = true
        viewModelScope.launch {
            val r = c.api.heartbeat(creds.baseUrl, creds.token, HeartbeatRequest(id, secs, positionSec))
            reporting = false
            when (r) {
                is ApiResult.Ok -> if (!final) {
                    val h = r.value
                    when {
                        h.error == "outside_schedule" -> _state.value = PlayerState.Blocked(Block.OUTSIDE_SCHEDULE)
                        h.timeUp -> _state.value = PlayerState.Blocked(Block.TIME_UP)
                        else -> _remaining.value = h.remaining
                    }
                }
                is ApiResult.HttpError -> when (r.code) {
                    401 -> if (!final) _expired.value = true
                    // The server forgot what we watch (restart): open the video again, keep the seconds.
                    409 -> if (!final) { played += secs; rearm(creds) }
                }
                else -> if (!final) played += secs // retry with the next report
            }
        }
    }

    private suspend fun rearm(creds: Credentials.Value) {
        val r = c.api.play(creds.baseUrl, creds.token, videoId)
        if (r is ApiResult.Ok && r.value.status != "ready" && r.value.error.isNotEmpty()) {
            _state.value = blocked(r.value)
        }
    }

    private fun ready(creds: Credentials.Value, p: PlayResponse): PlayerState {
        val stream = resolve(creds.baseUrl, p.stream) ?: return PlayerState.Failed(UiError.SERVER)
        val duration = p.video?.duration ?: 0
        return PlayerState.Ready(
            videoId = videoId,
            streamUrl = stream,
            subtitles = p.subtitles.mapNotNull { s ->
                resolve(creds.baseUrl, s.url)?.let { Subtitle(it, s.lang, s.label.ifEmpty { s.lang }) }
            },
            startMs = resumeMs(p.resumeSeconds, duration),
            headers = mapOf("Authorization" to "Bearer ${creds.token}"),
        )
    }

    private fun blocked(p: PlayResponse): PlayerState = when (p.error) {
        "time_up" -> PlayerState.Blocked(Block.TIME_UP, p.nextStart.orEmpty(), p.available.isNotEmpty())
        "outside_schedule" -> PlayerState.Blocked(Block.OUTSIDE_SCHEDULE, p.unlockTime)
        "not_approved" -> PlayerState.Blocked(Block.NOT_APPROVED)
        "local_playback_disabled" -> PlayerState.Blocked(Block.NO_LOCAL_PLAYBACK)
        "not_found" -> PlayerState.Blocked(Block.NOT_FOUND)
        else -> PlayerState.Failed(UiError.SERVER)
    }

    private fun fail(r: ApiResult<*>) {
        when {
            r is ApiResult.HttpError && r.code == 401 -> _expired.value = true
            r is ApiResult.NetworkError -> _state.value = PlayerState.Failed(UiError.UNREACHABLE)
            else -> _state.value = PlayerState.Failed(UiError.SERVER)
        }
    }

    companion object {
        const val HEARTBEAT_EVERY = 30
        private const val POLL_MS = 3_000L

        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlayerViewModel(c) }
        }
    }
}

/** Resume where the child stopped, unless that was the very end. */
internal fun resumeMs(resumeSeconds: Int, durationSeconds: Int): Long =
    if (resumeSeconds <= 0 || (durationSeconds > 0 && resumeSeconds >= durationSeconds - END_MARGIN_SEC)) 0L
    else resumeSeconds * 1000L

private const val END_MARGIN_SEC = 10
