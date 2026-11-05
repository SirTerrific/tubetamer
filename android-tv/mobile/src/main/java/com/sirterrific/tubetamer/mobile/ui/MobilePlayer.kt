package com.sirterrific.tubetamer.mobile.ui

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.sirterrific.tubetamer.api.VideoCard
import com.sirterrific.tubetamer.mobile.MobileApp
import com.sirterrific.tubetamer.ui.Block
import com.sirterrific.tubetamer.ui.PlayerState
import com.sirterrific.tubetamer.ui.PlayerViewModel
import com.sirterrific.tubetamer.ui.UiError
import com.sirterrific.tubetamer.core.R as CoreR
import kotlinx.coroutines.delay

private const val LOW_TIME_SEC = 5 * 60

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen playback of [video], streamed from the server. Same ViewModel calls as the TV player.
 * [onExit] gets the last known position (seconds) so the home rows can update their progress bars.
 */
@Composable
fun PlayerScreen(video: VideoCard, onExit: (positionSec: Int?) -> Unit, onExpired: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as MobileApp).container
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

    // Landscape and no system bars while a video is open; back to normal on exit.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // A state left by another video (first frame before start() runs) counts as starting.
    val current = state.takeUnless { it is PlayerState.Ready && it.videoId != video.videoId }
        ?: PlayerState.Starting
    Box(Modifier.fillMaxSize().background(if (current is PlayerState.Ready) Color.Black else MobileColors.Base)) {
        when (val s = current) {
            PlayerState.Starting -> Message(stringResource(CoreR.string.loading), video.title, icon = Icons.Filled.PlayArrow)
            is PlayerState.Preparing -> Message(
                s.percent.let { pct -> if (pct == null) stringResource(CoreR.string.player_preparing)
                else stringResource(CoreR.string.player_preparing_pct, pct) },
                video.title,
                icon = Icons.Filled.HourglassTop,
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
            is PlayerState.Failed -> Message(stringResource(CoreR.string.player_error), icon = Icons.Filled.Block, tint = MobileColors.Error) {
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
            .setUserAgent("TubeTamer-Mobile")
            .setDefaultRequestProperties(s.headers)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
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

    // Pause when the app goes to the background (budget keeps counting only while playing).
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
                    useController = true
                    keepScreenOn = true
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = 3_000
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (remaining in 0..LOW_TIME_SEC) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(MobileColors.Base.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .border(1.dp, MobileColors.Accent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = MobileColors.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(CoreR.string.time_left, (remaining + 59) / 60), style = MaterialTheme.typography.titleSmall)
            }
        }
        if (failed) {
            Box(Modifier.fillMaxSize().background(MobileColors.Base.copy(alpha = 0.92f))) {
                Message(stringResource(CoreR.string.player_error), icon = Icons.Filled.Wifi, tint = MobileColors.Error) {
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
            Block.TIME_UP -> CoreR.string.block_time_up
            Block.OUTSIDE_SCHEDULE -> CoreR.string.block_outside
            Block.NOT_APPROVED -> CoreR.string.block_not_approved
            Block.NO_LOCAL_PLAYBACK -> CoreR.string.block_no_local
            Block.NOT_FOUND -> CoreR.string.block_not_found
            Block.DOWNLOAD_FAILED -> CoreR.string.block_download_failed
        },
    )
    val detail = when {
        s.detail.isEmpty() -> null
        s.block == Block.TIME_UP -> stringResource(CoreR.string.block_time_up_next, s.detail)
        s.block == Block.OUTSIDE_SCHEDULE -> stringResource(CoreR.string.block_outside_unlock, s.detail)
        else -> null
    }
    val other = if (s.block == Block.TIME_UP && s.otherCategories) stringResource(CoreR.string.block_time_other) else null
    val (icon, tint) = when (s.block) {
        Block.TIME_UP -> Icons.Filled.Schedule to MobileColors.Accent
        Block.OUTSIDE_SCHEDULE -> Icons.Filled.Nightlight to MobileColors.Muted
        Block.NOT_APPROVED -> Icons.Filled.HourglassTop to MobileColors.Pending
        else -> Icons.Filled.Block to MobileColors.Error
    }
    Message(title, listOfNotNull(detail, other).joinToString("\n").ifEmpty { null }, icon = icon, tint = tint) {
        Spacer(Modifier.height(28.dp))
        Button(onClick = onBack) { Text(stringResource(CoreR.string.back)) }
    }
}

@Composable
private fun Actions(onRetry: () -> Unit, onBack: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onRetry) { Text(stringResource(CoreR.string.retry)) }
        OutlinedButton(onClick = onBack) { Text(stringResource(CoreR.string.back)) }
    }
}

@Composable
private fun Message(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = MobileColors.Accent,
    extra: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 560.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            extra()
        }
    }
}

/** Download progress, gold on dark blue; empty track while the server has no percentage yet. */
@Composable
private fun ProgressBar(percent: Int?) {
    Box(Modifier.width(280.dp).height(8.dp).background(MobileColors.Card, RoundedCornerShape(4.dp))) {
        if (percent != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(percent / 100f).background(MobileColors.Accent, RoundedCornerShape(4.dp)))
        }
    }
}
