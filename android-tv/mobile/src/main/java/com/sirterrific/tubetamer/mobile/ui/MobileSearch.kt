package com.sirterrific.tubetamer.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sirterrific.tubetamer.api.VideoCard
import com.sirterrific.tubetamer.api.VideoStatus
import com.sirterrific.tubetamer.ui.RequestsState
import com.sirterrific.tubetamer.ui.SearchState
import com.sirterrific.tubetamer.ui.SearchViewModel
import com.sirterrific.tubetamer.ui.resolve
import com.sirterrific.tubetamer.core.R as CoreR

/** Search YouTube, ask for a video, play the approved ones. */
@Composable
fun SearchScreen(vm: SearchViewModel, baseUrl: String, onPlay: (VideoCard) -> Unit) {
    val state by vm.search.collectAsStateWithLifecycle()
    val requestError by vm.requestError.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(vm.query) }
    var asking by remember { mutableStateOf<VideoCard?>(null) }
    val grid = rememberLazyGridState()

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    val submit = { q: String ->
        vm.clearRequestError()
        vm.search(q)
    }

    asking?.let { pending ->
        ConfirmRequest(
            pending, baseUrl,
            onAsk = { vm.request(pending); asking = null },
            onCancel = { asking = null },
        )
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(CoreR.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Close, contentDescription = null) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) submit(text) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        ErrorLine(requestError)
        when (val s = state) {
            SearchState.Idle -> StateMessage(Icons.Filled.Search, stringResource(CoreR.string.search), stringResource(CoreR.string.search_hint))
            SearchState.Loading -> CenteredColumn { CircularProgressIndicator() }
            is SearchState.Failed -> CenteredColumn {
                Icon(Icons.Filled.Wifi, contentDescription = null, tint = MobileColors.Error)
                Text(stringResource(CoreR.string.offline_title), style = MaterialTheme.typography.titleLarge)
                ErrorLine(s.error)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { submit(text) }) { Text(stringResource(CoreR.string.retry)) }
            }
            is SearchState.Results -> when {
                s.fetchFailed -> StateMessage(Icons.Filled.Block, stringResource(CoreR.string.search_fetch_failed), tint = MobileColors.Error)
                s.videos.isEmpty() -> StateMessage(Icons.Filled.Search, stringResource(CoreR.string.search_empty))
                else -> VideoGrid(s.videos, baseUrl, state = grid, showBadge = true, onClick = { v ->
                    when (v.status) {
                        VideoStatus.APPROVED -> onPlay(v)
                        "" -> asking = v
                        else -> Unit // pending or denied: the badge says it all
                    }
                })
            }
        }
    }
}

/** The profile's requests with their status, refreshed while on screen. */
@Composable
fun RequestsScreen(vm: SearchViewModel, baseUrl: String, onPlay: (VideoCard) -> Unit) {
    val state by vm.requests.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    when (val s = state) {
        RequestsState.Loading -> CenteredColumn { CircularProgressIndicator() }
        is RequestsState.Failed -> CenteredColumn { ErrorLine(s.error) }
        is RequestsState.Ready ->
            if (s.requests.isEmpty()) {
                StateMessage(Icons.Filled.Inbox, stringResource(CoreR.string.requests_empty))
            } else {
                VideoGrid(s.requests, baseUrl, state = grid, showBadge = true, onClick = { v ->
                    if (v.status == VideoStatus.APPROVED) onPlay(v)
                })
            }
    }
}

@Composable
private fun ConfirmRequest(v: VideoCard, baseUrl: String, onAsk: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(CoreR.string.request_title)) },
        text = {
            Column {
                AsyncImage(
                    model = resolve(baseUrl, v.thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MobileColors.CardHover),
                )
                Spacer(Modifier.height(12.dp))
                Text(v.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                Text(v.channelName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(CoreR.string.request_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onAsk) { Text(stringResource(CoreR.string.request_send)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(CoreR.string.cancel)) } },
    )
}
