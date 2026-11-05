package com.sirterrific.tubetamer.mobile.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sirterrific.tubetamer.api.RowId
import com.sirterrific.tubetamer.api.VideoCard
import com.sirterrific.tubetamer.api.VideoStatus
import com.sirterrific.tubetamer.ui.formatDuration
import com.sirterrific.tubetamer.ui.progressFraction
import com.sirterrific.tubetamer.ui.resolve
import com.sirterrific.tubetamer.core.R as CoreR

/** Start loading the next page when an item this close to the end of a list shows up. */
internal const val PREFETCH_DISTANCE = 4

/** A video tile: thumbnail with duration and progress bar, title, channel. [badge] is the request status label. */
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
        colors = CardDefaults.cardColors(containerColor = MobileColors.Card),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MobileColors.CardHover)) {
            Icon(
                Icons.Filled.PlayArrow, contentDescription = null, tint = MobileColors.Dim,
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
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(MobileColors.Accent))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(v.category)
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
}

/** Small green (edu) or orange (fun) dot, the web app's category colors. */
@Composable
private fun CategoryDot(category: String) {
    val c = when (category) {
        RowId.EDU -> MobileColors.Edu
        RowId.FUN -> MobileColors.Fun
        else -> return
    }
    Box(Modifier.size(8.dp).background(c, RoundedCornerShape(4.dp)))
    Spacer(Modifier.width(6.dp))
}

/** Status pill on a video: approved / waiting / denied. */
@Composable
internal fun StatusBadge(status: String, label: String, modifier: Modifier = Modifier) {
    val (color, icon) = when (status) {
        VideoStatus.APPROVED -> MobileColors.Edu to Icons.Filled.Check
        VideoStatus.PENDING -> MobileColors.Pending to Icons.Filled.HourglassTop
        else -> MobileColors.Error to Icons.Filled.Block
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
    }
}

@Composable
internal fun statusLabel(status: String): String? = when (status) {
    VideoStatus.APPROVED -> stringResource(CoreR.string.status_approved)
    VideoStatus.PENDING -> stringResource(CoreR.string.status_pending)
    VideoStatus.DENIED -> stringResource(CoreR.string.status_denied)
    else -> null
}

/** Icon, title and optional hint, centered (empty and error states). */
@Composable
internal fun StateMessage(icon: ImageVector, title: String, hint: String? = null, tint: Color = MobileColors.Accent) {
    CenteredColumn {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Adaptive grid of video tiles; [onNearEnd] fires when one of the last items is shown. */
@Composable
internal fun VideoGrid(
    videos: List<VideoCard>,
    baseUrl: String,
    onClick: (VideoCard) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    showBadge: Boolean = false,
    onNearEnd: (() -> Unit)? = null,
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(170.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = modifier,
    ) {
        itemsIndexed(videos, key = { _, v -> v.videoId }) { i, v ->
            if (onNearEnd != null && i >= videos.size - PREFETCH_DISTANCE * 2) {
                LaunchedEffect(videos.size) { onNearEnd() }
            }
            VideoCardView(v, baseUrl, onClick = { onClick(v) }, badge = if (showBadge) statusLabel(v.status) else null)
        }
    }
}
