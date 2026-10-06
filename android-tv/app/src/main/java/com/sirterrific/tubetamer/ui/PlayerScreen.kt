package com.sirterrific.tubetamer.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.tv.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sirterrific.tubetamer.R
import com.sirterrific.tubetamer.TubeTamerApp
import com.sirterrific.tubetamer.api.VideoCard
import kotlinx.coroutines.delay

private const val SEEK_MS = 10_000L
private const val LOW_TIME_SEC = 5 * 60

/**
 * Full-screen playback of [video], streamed from the server.
 * [onExit] gets the last known position (seconds), so the home rows can update
 * their progress bars without a reload.
 */
@Composable
fun PlayerScreen(video: VideoCard, onExit: (positionSec: Int?) -> Unit, onExpired: () -> Unit) {
    val container = (LocalContext.current.applicationContext as TubeTamerApp).container
    val vm: PlayerViewModel = viewModel(key = "player", factory = PlayerViewModel.factory(container))
    LaunchedEffect(video.videoId) { vm.start(video.videoId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    val expired by vm.expired.collectAsStateWithLifecycle()
    LaunchedEffect(expired) { if (expired) onExpired() }

    var lastPos by remember(video.videoId) { mutableStateOf<Int?>(null) }
    val exit = { onExit(lastPos) }
    BackHandler(onBack = exit)
    DisposableEffect(Unit) { onDispose { vm.stop() } }

    // A state left by another video (first frame before start() runs) counts as starting.
    val current = state.takeUnless { it is PlayerState.Ready && it.videoId != video.videoId }
        ?: PlayerState.Starting
    // Black behind the video, the TubeTamer background for every other state.
    val bg: @Composable (@Composable () -> Unit) -> Unit = { body ->
        if (current is PlayerState.Ready) Box(Modifier.fillMaxSize().background(Color.Black)) { body() }
        else TtBackground { body() }
    }
    bg {
        when (val s = current) {
            PlayerState.Starting -> Message(stringResource(R.string.loading), video.title, icon = TtIcons.Play)
            is PlayerState.Preparing -> Message(
                if (s.percent == null) stringResource(R.string.player_preparing)
                else stringResource(R.string.player_preparing_pct, s.percent),
                video.title,
                icon = TtIcons.Hourglass,
            ) {
                Spacer(Modifier.height(24.dp))
                ProgressBar(s.percent)
            }
            is PlayerState.Ready -> VideoPlayer(
                s, vm, remaining,
                onPosition = { lastPos = it },
                onEnded = exit,
            )
            is PlayerState.Blocked -> BlockedView(s, onBack = exit)
            is PlayerState.Failed -> Message(stringResource(R.string.player_error), icon = TtIcons.Blocked, tint = TtColors.Error) {
                ErrorLine(s.error)
                Spacer(Modifier.height(24.dp))
                Actions(onRetry = vm::retry, onBack = exit)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    s: PlayerState.Ready,
    vm: PlayerViewModel,
    remaining: Int,
    onPosition: (Int) -> Unit,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val ended by rememberUpdatedState(onEnded)
    val position by rememberUpdatedState(onPosition)
    var failed by remember(s) { mutableStateOf(false) }

    val player = remember(s) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("TubeTamer-TV")
            .setDefaultRequestProperties(s.headers)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            // Small bounded buffer, nothing cached on disk: the Shield has little storage.
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 30_000, 2_500, 5_000).build())
            .setSeekBackIncrementMs(SEEK_MS)
            .setSeekForwardIncrementMs(SEEK_MS)
            .build()
            .apply {
                setMediaItem(mediaItem(s), s.startMs)
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended()
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            val pos = (player.currentPosition / 1000).toInt()
            position(pos)
            // The video this player showed, even if another one is already starting.
            vm.report(pos, final = true, id = s.videoId)
            player.removeListener(listener)
            player.release()
        }
    }

    // Pause when the app goes to the background (Home button), resume is manual.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(1_000)
            if (player.isPlaying) {
                val pos = (player.currentPosition / 1000).toInt()
                position(pos)
                vm.tick(pos)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    keepScreenOn = true
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    controllerShowTimeoutMs = 3_000
                    isFocusable = true
                    isFocusableInTouchMode = true
                    post { requestFocus() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (remaining in 0..LOW_TIME_SEC) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .background(TtColors.Base.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .border(1.dp, TtColors.Accent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(TtIcons.Clock, contentDescription = null, tint = TtColors.Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.time_left, (remaining + 59) / 60),
                    color = TtColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (failed) {
            Box(Modifier.fillMaxSize().background(TtColors.Base.copy(alpha = 0.92f))) {
                Message(stringResource(R.string.player_error), icon = TtIcons.Wifi, tint = TtColors.Error) {
                    ErrorLine(UiError.UNREACHABLE)
                    Spacer(Modifier.height(24.dp))
                    Actions(onRetry = vm::retry, onBack = onEnded)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun mediaItem(s: PlayerState.Ready): MediaItem = MediaItem.Builder()
    .setUri(s.streamUrl)
    .setMimeType(MimeTypes.VIDEO_MP4)
    .setSubtitleConfigurations(
        s.subtitles.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(it.lang)
                .setLabel(it.label)
                .build()
        },
    )
    .build()

@Composable
private fun BlockedView(s: PlayerState.Blocked, onBack: () -> Unit) {
    val title = stringResource(
        when (s.block) {
            Block.TIME_UP -> R.string.block_time_up
            Block.OUTSIDE_SCHEDULE -> R.string.block_outside
            Block.NOT_APPROVED -> R.string.block_not_approved
            Block.NO_LOCAL_PLAYBACK -> R.string.block_no_local
            Block.NOT_FOUND -> R.string.block_not_found
            Block.DOWNLOAD_FAILED -> R.string.block_download_failed
        },
    )
    val detail = when {
        s.detail.isEmpty() -> null
        s.block == Block.TIME_UP -> stringResource(R.string.block_time_up_next, s.detail)
        s.block == Block.OUTSIDE_SCHEDULE -> stringResource(R.string.block_outside_unlock, s.detail)
        else -> null
    }
    val other = if (s.block == Block.TIME_UP && s.otherCategories) stringResource(R.string.block_time_other) else null
    val (icon, tint) = when (s.block) {
        Block.TIME_UP -> TtIcons.Clock to TtColors.Accent
        Block.OUTSIDE_SCHEDULE -> TtIcons.Moon to TtColors.Muted
        Block.NOT_APPROVED -> TtIcons.Hourglass to TtColors.Pending
        else -> TtIcons.Blocked to TtColors.Error
    }
    Message(title, listOfNotNull(detail, other).joinToString("\n").ifEmpty { null }, icon = icon, tint = tint) {
        Spacer(Modifier.height(28.dp))
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        TtButton(stringResource(R.string.back), onBack, Modifier.focusRequester(focus), icon = TtIcons.Back, primary = true)
    }
}

@Composable
private fun Actions(onRetry: () -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TtButton(stringResource(R.string.retry), onRetry, Modifier.focusRequester(focus), icon = TtIcons.Retry, primary = true)
        TtButton(stringResource(R.string.back), onBack, icon = TtIcons.Back)
    }
}

@Composable
private fun Message(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = TtColors.Accent,
    extra: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 720.dp)) {
            if (icon != null) {
                IconDisc(icon, tint)
                Spacer(Modifier.height(20.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = TtColors.Text, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Spacer(Modifier.height(10.dp))
                Text(subtitle, color = TtColors.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
            extra()
        }
    }
}

/** Download progress, gold on dark blue; pulsing while the server has no percentage yet. */
@Composable
private fun ProgressBar(percent: Int?) {
    Box(Modifier.width(360.dp).height(8.dp).background(TtColors.Card, RoundedCornerShape(4.dp))) {
        if (percent != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(percent / 100f).background(TtColors.Accent, RoundedCornerShape(4.dp)))
        } else {
            Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(4.dp)))
        }
    }
}
