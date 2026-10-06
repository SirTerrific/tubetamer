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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
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

@Composable
fun HomeScreen(profile: Profile, onSwitchProfile: () -> Unit, onExpired: () -> Unit) {
    val container = (LocalContext.current.applicationContext as TubeTamerApp).container
    val vm: HomeViewModel = viewModel(key = "home-${profile.id}", factory = HomeViewModel.factory(container))
    val home by vm.home.collectAsStateWithLifecycle()
    val channel by vm.channel.collectAsStateWithLifecycle()
    val expired by vm.expired.collectAsStateWithLifecycle()
    LaunchedEffect(expired) { if (expired) onExpired() }
    val baseUrl = container.credentials.get()?.baseUrl.orEmpty()
    val l = LocalTvLayout.current
    // Survives the channel screen and the player so Back lands where the child was.
    val nav = remember(profile.id) { HomeNav() }
    var playing by remember(profile.id) { mutableStateOf<VideoCard?>(null) }
    val onPlay: (VideoCard) -> Unit = { playing = it }
    var page by remember(profile.id) { mutableStateOf(HomePage.HOME) }
    val search: SearchViewModel = viewModel(key = "search-${profile.id}", factory = SearchViewModel.factory(container))
    val searchExpired by search.expired.collectAsStateWithLifecycle()
    LaunchedEffect(searchExpired) { if (searchExpired) onExpired() }

    val now = playing
    if (now != null) {
        PlayerScreen(
            now,
            onExit = { pos ->
                if (pos != null) vm.updateProgress(now.videoId, pos)
                playing = null
            },
            onExpired = onExpired,
        )
        return
    }

    if (page != HomePage.HOME) {
        // Back to the home rows, refreshed: a request may have been approved meanwhile.
        BackHandler { page = HomePage.HOME; vm.refresh() }
        Box(Modifier.fillMaxSize().padding(l.screenPadding)) {
            if (page == HomePage.SEARCH) SearchScreen(search, baseUrl, onPlay)
            else RequestsScreen(search, baseUrl, onPlay)
        }
        return
    }

    val open = channel
    if (open != null) {
        BackHandler { vm.closeChannel() }
        Box(Modifier.fillMaxSize().padding(l.screenPadding)) {
            ChannelScreen(open, nav, baseUrl, onPlay, onNearEnd = vm::loadMoreChannel)
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = l.padH).padding(top = l.padV)) {
        val header: @Composable () -> Unit = {
            HomeHeader(
                profile, onSwitchProfile,
                onSearch = { page = HomePage.SEARCH },
                onRequests = { page = HomePage.REQUESTS },
            )
        }
        when (val s = home) {
            HomeState.Loading -> {
                header()
                SkeletonRows()
            }
            is HomeState.Failed -> {
                header()
                Centered {
                    StateMessage(TtIcons.Wifi, stringResource(R.string.offline_title), tint = TtColors.Error)
                    ErrorLine(s.error)
                    Spacer(Modifier.height(20.dp))
                    TtButton(stringResource(R.string.retry), vm::refresh, icon = TtIcons.Retry, primary = true)
                }
            }
            is HomeState.Ready ->
                if (s.rows.isEmpty() && s.channels.isEmpty()) {
                    header()
                    Centered {
                        StateMessage(TtIcons.Channel, stringResource(R.string.home_empty))
                        Spacer(Modifier.height(20.dp))
                        TtButton(stringResource(R.string.search), { page = HomePage.SEARCH }, icon = TtIcons.Search, primary = true)
                    }
                } else {
                    HomeRows(s, nav, baseUrl, header, onPlay, vm::loadMore) { ch ->
                        nav.resetChannel()
                        vm.openChannel(ch)
                    }
                }
        }
    }
}

@Composable
private fun HomeHeader(profile: Profile, onSwitchProfile: () -> Unit, onSearch: () -> Unit, onRequests: () -> Unit) {
    val compact = LocalTvLayout.current.compact
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(size = 40.dp, showName = !compact)
        Spacer(Modifier.width(24.dp))
        Avatar(profile, 40)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.home_hello, profile.displayName),
            style = MaterialTheme.typography.titleLarge,
            color = TtColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TtButton(stringResource(R.string.search), onSearch, icon = TtIcons.Search, primary = true, iconOnly = compact)
            TtButton(stringResource(R.string.my_requests), onRequests, icon = TtIcons.Requests, iconOnly = compact)
            TtButton(stringResource(R.string.switch_profile), onSwitchProfile, icon = TtIcons.SwitchProfile, iconOnly = compact)
        }
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
    val l = LocalTvLayout.current
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
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = l.padV),
    ) {
        // Header scrolls away with the rows so a focused row never sits under it.
        item(key = "header") { header() }
        items(s.rows, key = { it.id }) { row ->
            val (icon, tint) = rowIcon(row.id)
            Column {
                SectionTitle(rowTitle(row.id), icon = icon, tint = tint)
                LazyRow(
                    state = nav.row(row.id),
                    horizontalArrangement = Arrangement.spacedBy(l.gap),
                    // Room for the focused card's scale-up and glow, which the row would clip.
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                ) {
                    itemsIndexed(row.videos, key = { _, v -> v.videoId }) { i, v ->
                        VideoCardView(
                            v, baseUrl,
                            onClick = { onPlay(v) },
                            modifier = Modifier.tracked(videoKey(row.id, v)).width(l.cardWidth).onFocusChanged {
                                if (it.isFocused && i >= row.videos.size - PREFETCH_DISTANCE) onNearEnd(row.id)
                            },
                        )
                    }
                    if (row.loadingMore) item { SkeletonCard(l.cardWidth) }
                }
            }
        }
        if (s.channels.isNotEmpty()) {
            item(key = "channels") {
                Column {
                    SectionTitle(stringResource(R.string.row_channels), icon = TtIcons.Channel)
                    LazyRow(
                        state = nav.row("channels"),
                        horizontalArrangement = Arrangement.spacedBy(l.gap),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
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

    var channelGrid = LazyGridState()
        private set
    var channelFocus: String? = null

    fun resetChannel() {
        channelGrid = LazyGridState()
        channelFocus = null
    }
}

private fun videoKey(rowId: String, v: VideoCard) = "v:$rowId/${v.videoId}"
private fun channelKey(ch: ChannelInfo) = "ch:${ch.id}"

@Composable
private fun ChannelScreen(
    state: ChannelState,
    nav: HomeNav,
    baseUrl: String,
    onPlay: (VideoCard) -> Unit,
    onNearEnd: () -> Unit,
) {
    val l = LocalTvLayout.current
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDisc(TtIcons.Channel)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(state.channel.name, style = MaterialTheme.typography.headlineMedium, color = TtColors.Text, maxLines = 1)
                state.row?.let {
                    Text(stringResource(R.string.channel_videos, it.total), color = TtColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val row = state.row
        when {
            state.error != null -> Centered { ErrorLine(state.error) }
            row == null -> SkeletonRows(rows = 2)
            row.videos.isEmpty() -> Centered { StateMessage(TtIcons.Channel, stringResource(R.string.channel_empty)) }
            else -> {
                val target = remember { FocusRequester() }
                val focusId = nav.channelFocus ?: row.videos.first().videoId
                LaunchedEffect(Unit) { runCatching { target.requestFocus() } }
                LazyVerticalGrid(
                    state = nav.channelGrid,
                    columns = GridCells.Fixed(l.gridColumns),
                    horizontalArrangement = Arrangement.spacedBy(l.gap),
                    verticalArrangement = Arrangement.spacedBy(l.gap),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    itemsIndexed(row.videos, key = { _, v -> v.videoId }) { i, v ->
                        val mod = (if (v.videoId == focusId) Modifier.focusRequester(target) else Modifier)
                            .onFocusChanged { if (it.isFocused) nav.channelFocus = v.videoId }
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

/**
 * A video tile: thumbnail with duration and progress bar, title, channel.
 * [badge] (search and requests) is the label of the video's request status.
 */
@Composable
internal fun VideoCardView(
    v: VideoCard,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = TtCard.shape,
        colors = TtCard.colors(),
        border = TtCard.border(),
        glow = TtCard.glow(),
        scale = CardDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(TtColors.CardHover)) {
            // Shows until the thumbnail arrives (or when the server has none).
            Icon(
                TtIcons.Play, contentDescription = null, tint = TtColors.Dim,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
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
                        .padding(8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (badge != null) {
                StatusBadge(v.status, badge, Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            val progress = progressFraction(v.progressSeconds, v.duration)
            if (progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(TtColors.Accent))
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                v.title,
                style = MaterialTheme.typography.titleSmall,
                color = TtColors.Text,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(v.category)
                Text(
                    v.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TtColors.Muted,
                )
            }
        }
    }
}

/** Small green (edu) or orange (fun) dot, the web app's category colors. */
@Composable
private fun CategoryDot(category: String) {
    val c = when (category) {
        RowId.EDU -> TtColors.Edu
        RowId.FUN -> TtColors.Fun
        else -> return
    }
    Box(Modifier.size(8.dp).background(c, RoundedCornerShape(4.dp)))
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun ChannelChip(ch: ChannelInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val l = LocalTvLayout.current
    Card(
        onClick = onClick,
        modifier = modifier.width(l.cardWidth),
        shape = TtCard.shape,
        colors = TtCard.colors(),
        border = TtCard.border(),
        glow = TtCard.glow(),
        scale = CardDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(TtColors.Accent.copy(alpha = 0.16f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(ch.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = TtColors.Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(ch.name, style = MaterialTheme.typography.titleSmall, color = TtColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ch.videoCount > 0) {
                    Text(stringResource(R.string.channel_videos, ch.videoCount), style = MaterialTheme.typography.bodySmall, color = TtColors.Muted)
                }
            }
        }
    }
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

private fun rowIcon(id: String): Pair<ImageVector, Color> = when (id) {
    RowId.ACTIVE -> TtIcons.Play to TtColors.Accent
    RowId.EDU -> TtIcons.Book to TtColors.EduText
    RowId.FUN -> TtIcons.Star to TtColors.FunText
    RowId.SHORTS -> TtIcons.Bolt to TtColors.Muted
    else -> TtIcons.Channel to TtColors.Muted
}

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

/** What the home screen shows besides its rows (the player and channel screens sit on top of any). */
private enum class HomePage { HOME, SEARCH, REQUESTS }
