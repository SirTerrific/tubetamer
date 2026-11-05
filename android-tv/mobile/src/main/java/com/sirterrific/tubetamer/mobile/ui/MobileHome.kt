package com.sirterrific.tubetamer.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sirterrific.tubetamer.api.ChannelInfo
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.api.RowId
import com.sirterrific.tubetamer.api.VideoCard
import com.sirterrific.tubetamer.mobile.MobileApp
import com.sirterrific.tubetamer.ui.ChannelState
import com.sirterrific.tubetamer.ui.HomeState
import com.sirterrific.tubetamer.ui.HomeViewModel
import com.sirterrific.tubetamer.ui.SearchViewModel
import com.sirterrific.tubetamer.ui.VideoRow
import com.sirterrific.tubetamer.mobile.R as MobileR
import com.sirterrific.tubetamer.core.R as CoreR
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HomePage { HOME, SEARCH, REQUESTS }

/** Width from which the navigation bar becomes a rail (tablets, phones in landscape). */
private const val WIDE_DP = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(profile: Profile, onSwitchProfile: () -> Unit, onExpired: () -> Unit) {
    val container = (LocalContext.current.applicationContext as MobileApp).container
    val vm: HomeViewModel = viewModel(key = "home-${profile.id}", factory = HomeViewModel.factory(container))
    val search: SearchViewModel = viewModel(key = "search-${profile.id}", factory = SearchViewModel.factory(container))
    // Before anything reads the flags: a ViewModel kept from an earlier sign-in must not say "expired".
    remember(profile.id) { vm.begin(); search.begin() }
    val home by vm.home.collectAsStateWithLifecycle()
    val channel by vm.channel.collectAsStateWithLifecycle()
    val expired by vm.expired.collectAsStateWithLifecycle()
    val timeLeft by vm.timeLeft.collectAsStateWithLifecycle()
    LaunchedEffect(expired) { if (expired) onExpired() }
    val searchExpired by search.expired.collectAsStateWithLifecycle()
    LaunchedEffect(searchExpired) { if (searchExpired) onExpired() }
    val baseUrl = container.credentials.get()?.baseUrl.orEmpty()
    var playing by remember(profile.id) { mutableStateOf<VideoCard?>(null) }
    val onPlay: (VideoCard) -> Unit = { playing = it }
    var page by rememberSaveable(profile.id) { mutableStateOf(HomePage.HOME) }

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

    val open = channel
    if (open != null) {
        BackHandler { vm.closeChannel() }
        ChannelScreen(open, baseUrl, onBack = vm::closeChannel, onPlay = onPlay, onNearEnd = vm::loadMoreChannel)
        return
    }

    // Back to the home rows, refreshed: a request may have been approved meanwhile.
    if (page != HomePage.HOME) BackHandler { page = HomePage.HOME; vm.refresh() }
    val select = { p: HomePage ->
        if (p == HomePage.HOME && page != HomePage.HOME) vm.refresh()
        page = p
    }
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(profile, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(CoreR.string.home_hello, profile.displayName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    timeLeft?.let {
                        Text(
                            stringResource(CoreR.string.time_left, it),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (it <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = onSwitchProfile) {
                        Icon(Icons.Filled.SwitchAccount, contentDescription = stringResource(CoreR.string.switch_profile))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { if (!wide) HomeNavigationBar(page, select) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (wide) HomeNavigationRail(page, select)
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (page) {
                    HomePage.HOME -> HomeContent(home, baseUrl, vm, onPlay, onSearch = { select(HomePage.SEARCH) })
                    HomePage.SEARCH -> SearchScreen(search, baseUrl, onPlay)
                    HomePage.REQUESTS -> RequestsScreen(search, baseUrl, onPlay)
                }
            }
        }
    }
}

private class Dest(val page: HomePage, val icon: ImageVector, val label: @Composable () -> String)

private val destinations = listOf(
    Dest(HomePage.HOME, Icons.Filled.Home) { stringResource(MobileR.string.nav_home) },
    Dest(HomePage.SEARCH, Icons.Filled.Search) { stringResource(CoreR.string.search) },
    Dest(HomePage.REQUESTS, Icons.Filled.Inbox) { stringResource(CoreR.string.my_requests) },
)

@Composable
private fun HomeNavigationBar(page: HomePage, onSelect: (HomePage) -> Unit) {
    NavigationBar {
        destinations.forEach { d ->
            val label = d.label()
            NavigationBarItem(
                selected = page == d.page,
                onClick = { onSelect(d.page) },
                icon = { Icon(d.icon, contentDescription = null) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun HomeNavigationRail(page: HomePage, onSelect: (HomePage) -> Unit) {
    NavigationRail {
        Spacer(Modifier.weight(1f))
        destinations.forEach { d ->
            val label = d.label()
            NavigationRailItem(
                selected = page == d.page,
                onClick = { onSelect(d.page) },
                icon = { Icon(d.icon, contentDescription = null) },
                label = { Text(label, maxLines = 1) },
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    home: HomeState,
    baseUrl: String,
    vm: HomeViewModel,
    onPlay: (VideoCard) -> Unit,
    onSearch: () -> Unit,
) {
    when (home) {
        HomeState.Loading -> CenteredColumn { CircularProgressIndicator() }
        is HomeState.Failed -> CenteredColumn {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = MobileColors.Error, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(CoreR.string.offline_title), style = MaterialTheme.typography.titleLarge)
            ErrorLine(home.error)
            Spacer(Modifier.height(20.dp))
            Button(onClick = vm::refresh) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(CoreR.string.retry))
            }
        }
        is HomeState.Ready ->
            if (home.rows.isEmpty() && home.channels.isEmpty()) {
                CenteredColumn {
                    Text(stringResource(CoreR.string.home_empty), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onSearch) { Text(stringResource(CoreR.string.search)) }
                }
            } else {
                // The ViewModel has no "refreshing" flag: show the spinner for a short fixed time.
                var refreshing by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        vm.refresh()
                        scope.launch { delay(700); refreshing = false }
                    },
                ) {
                    HomeRows(home, baseUrl, onPlay, vm::loadMore, onChannel = vm::openChannel)
                }
            }
    }
}

@Composable
private fun HomeRows(
    s: HomeState.Ready,
    baseUrl: String,
    onPlay: (VideoCard) -> Unit,
    onNearEnd: (String) -> Unit,
    onChannel: (ChannelInfo) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(s.rows, key = { it.id }) { row ->
            Column {
                SectionTitle(rowTitle(row.id))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    itemsIndexed(row.videos, key = { _, v -> v.videoId }) { i, v ->
                        if (i >= row.videos.size - PREFETCH_DISTANCE) {
                            LaunchedEffect(row.videos.size) { onNearEnd(row.id) }
                        }
                        VideoCardView(v, baseUrl, onClick = { onPlay(v) }, modifier = Modifier.width(240.dp))
                    }
                    if (row.loadingMore) {
                        item { Box(Modifier.width(80.dp).height(135.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    }
                }
            }
        }
        if (s.channels.isNotEmpty()) {
            item(key = "channels") {
                Column {
                    SectionTitle(stringResource(CoreR.string.row_channels))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        items(s.channels, key = { it.id }) { ch -> ChannelChip(ch, onClick = { onChannel(ch) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun ChannelChip(ch: ChannelInfo, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(220.dp)
            .background(MobileColors.Card, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(MobileColors.Accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(ch.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MobileColors.Accent)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(ch.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ch.videoCount > 0) {
                Text(
                    stringResource(CoreR.string.channel_videos, ch.videoCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelScreen(
    state: ChannelState,
    baseUrl: String,
    onBack: () -> Unit,
    onPlay: (VideoCard) -> Unit,
    onNearEnd: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.row?.let {
                            Text(
                                stringResource(CoreR.string.channel_videos, it.total),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreR.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val row: VideoRow? = state.row
            when {
                state.error != null -> CenteredColumn { ErrorLine(state.error) }
                row == null -> CenteredColumn { CircularProgressIndicator() }
                row.videos.isEmpty() -> CenteredColumn { Text(stringResource(CoreR.string.channel_empty)) }
                else -> VideoGrid(row.videos, baseUrl, onClick = onPlay, onNearEnd = onNearEnd)
            }
        }
    }
}

@Composable
private fun rowTitle(id: String): String = stringResource(
    when (id) {
        RowId.ACTIVE -> CoreR.string.row_active
        RowId.EDU -> CoreR.string.row_edu
        RowId.FUN -> CoreR.string.row_fun
        RowId.SHORTS -> CoreR.string.row_shorts
        else -> CoreR.string.row_other
    },
)
