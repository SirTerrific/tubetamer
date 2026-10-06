package com.sirterrific.tubetamer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.sirterrific.tubetamer.R
import com.sirterrific.tubetamer.TubeTamerApp
import com.sirterrific.tubetamer.api.ChannelInfo
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.api.RowId
import com.sirterrific.tubetamer.api.VideoCard
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Start loading the next page when focus is this close to the end of a row. */
private const val PREFETCH_DISTANCE = 4
private val CARD_WIDTH = 200.dp

@Composable
fun HomeScreen(profile: Profile, onSwitchProfile: () -> Unit, onExpired: () -> Unit) {
    val container = (LocalContext.current.applicationContext as TubeTamerApp).container
    val vm: HomeViewModel = viewModel(key = "home-${profile.id}", factory = HomeViewModel.factory(container))
    val home by vm.home.collectAsStateWithLifecycle()
    val channel by vm.channel.collectAsStateWithLifecycle()
    val expired by vm.expired.collectAsStateWithLifecycle()
    LaunchedEffect(expired) { if (expired) onExpired() }
    val baseUrl = container.credentials.get()?.baseUrl.orEmpty()
    // Playback arrives with the player (B7); cards are focusable and clickable already.
    val onPlay: (VideoCard) -> Unit = { }
    // Survives the channel screen (and later the player) so Back lands where the child was.
    val nav = remember(profile.id) { HomeNav() }

    val open = channel
    if (open != null) {
        BackHandler { vm.closeChannel() }
        ChannelScreen(open, baseUrl, onPlay, onNearEnd = vm::loadMoreChannel)
        return
    }

    Column(Modifier.fillMaxSize()) {
        val header: @Composable () -> Unit = { HomeHeader(profile, onSwitchProfile) }
        when (val s = home) {
            HomeState.Loading -> {
                header()
                Centered { Text(stringResource(R.string.loading)) }
            }
            is HomeState.Failed -> {
                header()
                Centered {
                    ErrorLine(s.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
                }
            }
            is HomeState.Ready ->
                if (s.rows.isEmpty() && s.channels.isEmpty()) {
                    header()
                    Centered { Text(stringResource(R.string.home_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    HomeRows(s, nav, baseUrl, header, onPlay, vm::loadMore, vm::openChannel)
                }
        }
    }
}

@Composable
private fun HomeHeader(profile: Profile, onSwitchProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(profile, 44)
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.home_hello, profile.displayName),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onSwitchProfile) { Text(stringResource(R.string.switch_profile)) }
    }
}

@Composable
private fun HomeRows(
    s: HomeState.Ready,
    nav: HomeNav,
    baseUrl: String,
    header: @Composable () -> Unit,
    onPlay: (VideoCard) -> Unit,
    onNearEnd: (String) -> Unit,
    onChannel: (ChannelInfo) -> Unit,
) {
    val target = remember { FocusRequester() }
    val firstKey = s.rows.firstOrNull()?.videos?.firstOrNull()?.let { videoKey(s.rows.first().id, it) }
        ?: s.channels.firstOrNull()?.let { channelKey(it) }
    val focusKey = nav.lastFocused ?: firstKey
    LaunchedEffect(Unit) { runCatching { target.requestFocus() } }
    fun Modifier.tracked(key: String): Modifier =
        (if (key == focusKey) focusRequester(target) else this)
            .onFocusChanged { if (it.isFocused) nav.lastFocused = key }
    LazyColumn(
        state = nav.list,
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Header scrolls away with the rows so a focused row never sits under it.
        item(key = "header") { header() }
        items(s.rows, key = { it.id }) { row ->
            Column {
                SectionTitle(rowTitle(row.id))
                LazyRow(
                    state = nav.row(row.id),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    // Room for the focused card's scale-up, which the row would clip.
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    itemsIndexed(row.videos, key = { _, v -> v.videoId }) { i, v ->
                        VideoCardView(
                            v, baseUrl,
                            onClick = { onPlay(v) },
                            modifier = Modifier.tracked(videoKey(row.id, v)).width(CARD_WIDTH).onFocusChanged {
                                if (it.isFocused && i >= row.videos.size - PREFETCH_DISTANCE) onNearEnd(row.id)
                            },
                        )
                    }
                }
            }
        }
        if (s.channels.isNotEmpty()) {
            item(key = "channels") {
                Column {
                    SectionTitle(stringResource(R.string.row_channels))
                    LazyRow(
                        state = nav.row("channels"),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        items(s.channels, key = { it.id }) { ch ->
                            ChannelChip(ch, onClick = { onChannel(ch) }, modifier = Modifier.tracked(channelKey(ch)))
                        }
                    }
                }
            }
        }
    }
}

/** Scroll positions and last focused item of the home screen, kept while it is off screen. */
private class HomeNav {
    val list = LazyListState()
    private val rows = mutableMapOf<String, LazyListState>()
    fun row(id: String): LazyListState = rows.getOrPut(id) { LazyListState() }
    var lastFocused: String? = null
}

private fun videoKey(rowId: String, v: VideoCard) = "v:$rowId/${v.videoId}"
private fun channelKey(ch: ChannelInfo) = "ch:${ch.id}"

@Composable
private fun ChannelScreen(
    state: ChannelState,
    baseUrl: String,
    onPlay: (VideoCard) -> Unit,
    onNearEnd: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            state.channel.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val row = state.row
        when {
            state.error != null -> Centered { ErrorLine(state.error) }
            row == null -> Centered { Text(stringResource(R.string.loading)) }
            row.videos.isEmpty() -> Centered {
                Text(stringResource(R.string.channel_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                Text(
                    stringResource(R.string.channel_videos, row.total),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(CARD_WIDTH),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    itemsIndexed(row.videos, key = { _, v -> v.videoId }) { i, v ->
                        val mod = if (i == 0) Modifier.focusRequester(first) else Modifier
                        VideoCardView(
                            v, baseUrl,
                            onClick = { onPlay(v) },
                            modifier = mod.onFocusChanged {
                                if (it.isFocused && i >= row.videos.size - PREFETCH_DISTANCE * 2) onNearEnd()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCardView(v: VideoCard, baseUrl: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier, scale = CardDefaults.scale(focusedScale = 1.06f)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF2A2A2A))) {
            AsyncImage(
                model = resolve(baseUrl, v.thumbnail),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (v.duration > 0) {
                Text(
                    formatDuration(v.duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            val progress = progressFraction(v.progressSeconds, v.duration)
            if (progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                v.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                v.channelName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChannelChip(ch: ChannelInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.width(220.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(ch.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun rowTitle(id: String): String = stringResource(
    when (id) {
        RowId.ACTIVE -> R.string.row_active
        RowId.EDU -> R.string.row_edu
        RowId.FUN -> R.string.row_fun
        RowId.SHORTS -> R.string.row_shorts
        else -> R.string.row_other
    },
)

/** Server paths like /thumb/abc resolve against the base URL (which may carry a sub-path). */
internal fun resolve(baseUrl: String, path: String): String? {
    if (path.isEmpty()) return null
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    return base.resolve(path.removePrefix("/"))?.toString()
}

internal fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun progressFraction(progress: Int, duration: Int): Float =
    if (duration <= 0 || progress <= 0) 0f else (progress.toFloat() / duration).coerceIn(0f, 1f)
