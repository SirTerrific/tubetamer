package com.sirterrific.tubetamer.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.sirterrific.tubetamer.R
import com.sirterrific.tubetamer.api.VideoCard
import com.sirterrific.tubetamer.api.VideoStatus

/** Search YouTube, ask for a video, play the approved ones. */
@Composable
fun SearchScreen(vm: SearchViewModel, baseUrl: String, onPlay: (VideoCard) -> Unit) {
    val state by vm.search.collectAsStateWithLifecycle()
    val requestError by vm.requestError.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(vm.query) }
    var asking by remember { mutableStateOf<VideoCard?>(null) }
    // Card to give focus back to (after the confirmation or the player); null = first result.
    var focusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val fieldFocus = remember { FocusRequester() }
    val cardFocus = remember { FocusRequester() }

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    val submit = { q: String ->
        focusKey = null
        vm.clearRequestError()
        vm.search(q)
    }

    val pending = asking
    if (pending != null) {
        ConfirmRequest(
            pending, baseUrl,
            onAsk = { vm.request(pending); asking = null },
            onCancel = { asking = null },
        )
        return
    }

    val results = (state as? SearchState.Results)?.videos.orEmpty()
    val ids = results.map { it.videoId }
    LaunchedEffect(ids) {
        if (ids.isNotEmpty()) runCatching { cardFocus.requestFocus() }
        else if (state !is SearchState.Loading) runCatching { fieldFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(text, onText = { text = it }, onSubmit = submit, focus = fieldFocus)
        ErrorLine(requestError)
        Spacer(Modifier.height(8.dp))
        when (val s = state) {
            SearchState.Idle -> Centered {
                StateMessage(TtIcons.Search, stringResource(R.string.search), stringResource(R.string.search_hint))
            }
            SearchState.Loading -> SkeletonGrid()
            is SearchState.Failed -> Centered {
                StateMessage(TtIcons.Wifi, stringResource(R.string.offline_title), tint = TtColors.Error)
                ErrorLine(s.error)
                Spacer(Modifier.height(20.dp))
                TtButton(stringResource(R.string.retry), { submit(text) }, icon = TtIcons.Retry, primary = true)
            }
            is SearchState.Results -> when {
                s.fetchFailed -> Centered { StateMessage(TtIcons.Blocked, stringResource(R.string.search_fetch_failed), tint = TtColors.Error) }
                s.videos.isEmpty() -> Centered { StateMessage(TtIcons.Search, stringResource(R.string.search_empty)) }
                else -> StatusGrid(s.videos, baseUrl, grid, cardFocus, focusKey) { v ->
                    focusKey = v.videoId
                    when (v.status) {
                        VideoStatus.APPROVED -> onPlay(v)
                        "" -> asking = v
                        else -> Unit // pending or denied: the badge says it all
                    }
                }
            }
        }
    }
}

/** The profile's requests with their status, refreshed while on screen. */
@Composable
fun RequestsScreen(vm: SearchViewModel, baseUrl: String, onPlay: (VideoCard) -> Unit) {
    val state by vm.requests.collectAsStateWithLifecycle()
    var focusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val cardFocus = remember { FocusRequester() }

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    val hasItems = (state as? RequestsState.Ready)?.requests?.isNotEmpty() == true
    LaunchedEffect(hasItems) { if (hasItems) runCatching { cardFocus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDisc(TtIcons.Requests)
            Spacer(Modifier.width(18.dp))
            Text(stringResource(R.string.my_requests), style = MaterialTheme.typography.headlineMedium, color = TtColors.Text)
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            RequestsState.Loading -> SkeletonGrid()
            is RequestsState.Failed -> Centered { ErrorLine(s.error) }
            is RequestsState.Ready ->
                if (s.requests.isEmpty()) {
                    Centered { StateMessage(TtIcons.Requests, stringResource(R.string.requests_empty)) }
                } else {
                    StatusGrid(s.requests, baseUrl, grid, cardFocus, focusKey) { v ->
                        focusKey = v.videoId
                        if (v.status == VideoStatus.APPROVED) onPlay(v)
                    }
                }
        }
    }
}

@Composable
private fun SearchBar(text: String, onText: (String) -> Unit, onSubmit: (String) -> Unit, focus: FocusRequester) {
    val context = LocalContext.current
    val compact = LocalTvLayout.current.compact
    // Same language as the menus (the server's), not necessarily the TV's.
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val voiceIntent = remember(language) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
    }
    // Hidden when the device has no speech recognizer (the on-screen keyboard may still offer one).
    val canVoice = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            onText(spoken)
            onSubmit(spoken)
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TtTextField(
            value = text,
            onValueChange = onText,
            placeholder = stringResource(R.string.search_hint),
            icon = TtIcons.Search,
            imeAction = ImeAction.Search,
            onAction = { onSubmit(text) },
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Spacer(Modifier.width(16.dp))
        TtButton(
            stringResource(R.string.search), { onSubmit(text) },
            icon = TtIcons.Search, primary = true, enabled = text.isNotBlank(), iconOnly = compact,
        )
        if (canVoice) {
            Spacer(Modifier.width(12.dp))
            TtButton(stringResource(R.string.search_voice), { runCatching { voice.launch(voiceIntent) } }, icon = TtIcons.Mic, iconOnly = compact)
        }
    }
}

@Composable
private fun StatusGrid(
    videos: List<VideoCard>,
    baseUrl: String,
    grid: LazyGridState,
    focus: FocusRequester,
    focusKey: String?,
    onClick: (VideoCard) -> Unit,
) {
    val l = LocalTvLayout.current
    val target = videos.firstOrNull { it.videoId == focusKey }?.videoId ?: videos.first().videoId
    LazyVerticalGrid(
        columns = GridCells.Fixed(l.gridColumns),
        state = grid,
        horizontalArrangement = Arrangement.spacedBy(l.gap),
        verticalArrangement = Arrangement.spacedBy(l.gap),
        // Room for the focused card's scale-up and glow.
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
    ) {
        items(videos, key = { it.videoId }) { v ->
            VideoCardView(
                v, baseUrl,
                onClick = { onClick(v) },
                modifier = if (v.videoId == target) Modifier.focusRequester(focus) else Modifier,
                badge = statusLabel(v.status),
            )
        }
    }
}

@Composable
private fun SkeletonGrid() {
    val l = LocalTvLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(l.gap), modifier = Modifier.padding(8.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(l.gap)) {
                repeat(l.gridColumns) { SkeletonCard(l.cardWidth) }
            }
        }
    }
}

@Composable
private fun ConfirmRequest(v: VideoCard, baseUrl: String, onAsk: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val l = LocalTvLayout.current
    val thumbW = (l.width * 0.32f).coerceIn(240.dp, 480.dp)
    Centered {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Box(
                Modifier.width(thumbW).aspectRatio(16f / 9f).background(TtColors.Card, CardShape),
            ) {
                Icon(TtIcons.Play, null, tint = TtColors.Dim, modifier = Modifier.align(Alignment.Center).size(40.dp))
                AsyncImage(
                    model = resolve(baseUrl, v.thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.widthIn(max = 460.dp)) {
                Text(stringResource(R.string.request_title), style = MaterialTheme.typography.headlineSmall, color = TtColors.Accent)
                Spacer(Modifier.height(14.dp))
                Text(
                    v.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TtColors.Text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(v.channelName, style = MaterialTheme.typography.bodyLarge, color = TtColors.Muted)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.request_body), color = TtColors.Muted, textAlign = TextAlign.Start)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TtButton(stringResource(R.string.request_send), onAsk, Modifier.focusRequester(focus), icon = TtIcons.Requests, primary = true)
                    TtButton(stringResource(R.string.cancel), onCancel, icon = TtIcons.Back)
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: String): String? = when (status) {
    VideoStatus.APPROVED -> stringResource(R.string.status_approved)
    VideoStatus.PENDING -> stringResource(R.string.status_pending)
    VideoStatus.DENIED -> stringResource(R.string.status_denied)
    else -> null
}
