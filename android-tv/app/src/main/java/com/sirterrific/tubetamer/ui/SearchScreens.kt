package com.sirterrific.tubetamer.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.sirterrific.tubetamer.R
import com.sirterrific.tubetamer.api.VideoCard
import com.sirterrific.tubetamer.api.VideoStatus

private val GRID_CARD_WIDTH = 200.dp

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
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            SearchState.Idle -> Unit
            SearchState.Loading -> Centered { Text(stringResource(R.string.loading)) }
            is SearchState.Failed -> Centered {
                ErrorLine(s.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { submit(text) }) { Text(stringResource(R.string.retry)) }
            }
            is SearchState.Results -> when {
                s.fetchFailed -> Centered { Hint(stringResource(R.string.search_fetch_failed)) }
                s.videos.isEmpty() -> Centered { Hint(stringResource(R.string.search_empty)) }
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
        SectionTitle(stringResource(R.string.my_requests))
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            RequestsState.Loading -> Centered { Text(stringResource(R.string.loading)) }
            is RequestsState.Failed -> Centered { ErrorLine(s.error) }
            is RequestsState.Ready ->
                if (s.requests.isEmpty()) {
                    Centered { Hint(stringResource(R.string.requests_empty)) }
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
    var focused by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit(text) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(
                    2.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            },
        )
        Spacer(Modifier.width(16.dp))
        Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.search)) }
        if (canVoice) {
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { runCatching { voice.launch(voiceIntent) } }) {
                Text(stringResource(R.string.search_voice))
            }
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
    val target = videos.firstOrNull { it.videoId == focusKey }?.videoId ?: videos.first().videoId
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_CARD_WIDTH),
        state = grid,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        // Room for the focused card's scale-up.
        contentPadding = PaddingValues(8.dp),
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
private fun ConfirmRequest(v: VideoCard, baseUrl: String, onAsk: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Centered {
        Text(
            stringResource(R.string.request_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.width(360.dp).aspectRatio(16f / 9f)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = resolve(baseUrl, v.thumbnail),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            v.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(640.dp),
        )
        Text(v.channelName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Hint(stringResource(R.string.request_body))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onAsk, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.request_send)) }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun statusLabel(status: String): String? = when (status) {
    VideoStatus.APPROVED -> stringResource(R.string.status_approved)
    VideoStatus.PENDING -> stringResource(R.string.status_pending)
    VideoStatus.DENIED -> stringResource(R.string.status_denied)
    else -> null
}
