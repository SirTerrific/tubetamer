package com.sirterrific.tubetamer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sirterrific.tubetamer.R
import com.sirterrific.tubetamer.api.VideoStatus

internal val CardShape = RoundedCornerShape(14.dp)   // --radius-card
internal val ButtonShape = RoundedCornerShape(10.dp) // --radius-btn

/** Night-blue page with the web app's faint doodle pattern and a soft header glow. */
@Composable
fun TtBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val doodle = ImageBitmap.imageResource(R.drawable.bg_doodle)
    val pattern = remember(doodle) { ShaderBrush(ImageShader(doodle, TileMode.Repeated, TileMode.Repeated)) }
    Box(
        modifier
            .fillMaxSize()
            .background(TtColors.Base)
            .drawBehind {
                drawRect(pattern)
                drawRect(
                    Brush.verticalGradient(
                        0f to TtColors.Header.copy(alpha = 0.75f),
                        0.35f to Color.Transparent,
                    ),
                )
            },
        content = content,
    )
}

/** Hat logo and name, as in the web header. */
@Composable
fun AppLogo(size: Dp = 40.dp, showName: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.tubetamer_logo), contentDescription = null, modifier = Modifier.size(size))
        if (showName) {
            Spacer(Modifier.width(size * 0.3f))
            Text("TubeTamer", style = MaterialTheme.typography.titleLarge, color = TtColors.Text)
        }
    }
}

/**
 * The one button of the app. [primary] = gold, the main action of a screen.
 * Every button turns gold when focused, so the child always sees where the
 * remote is. [iconOnly] keeps just the icon (narrow screens).
 */
@Composable
fun TtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    iconOnly: Boolean = false,
    /** Fixed-size buttons (PIN keys): center the label in the whole button. */
    centered: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ButtonDefaults.shape(ButtonShape),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) TtColors.Accent else TtColors.Card,
            contentColor = if (primary) TtColors.OnAccent else TtColors.Text,
            focusedContainerColor = if (primary) TtColors.Text else TtColors.Accent,
            focusedContentColor = TtColors.OnAccent,
            pressedContainerColor = TtColors.AccentDark,
            pressedContentColor = TtColors.OnAccent,
            disabledContainerColor = TtColors.Card.copy(alpha = 0.5f),
            disabledContentColor = TtColors.Dim,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, if (primary) Color.Transparent else TtColors.Border), shape = ButtonShape),
            focusedBorder = Border(BorderStroke(2.dp, TtColors.AccentLight), shape = ButtonShape),
        ),
        contentPadding = if (iconOnly) PaddingValues(horizontal = 14.dp, vertical = 10.dp) else ButtonDefaults.ContentPadding,
    ) {
        val label: @Composable () -> Unit = {
            if (icon != null) {
                Icon(icon, contentDescription = if (iconOnly) text else null, modifier = Modifier.size(20.dp))
                if (!iconOnly) Spacer(Modifier.width(8.dp))
            }
            if (!iconOnly) Text(text, maxLines = 1)
        }
        if (centered) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { label() }
        } else {
            label()
        }
    }
}

/** Shared look for focusable cards: dark blue, gold border and glow when focused. */
object TtCard {
    @Composable fun colors() = CardDefaults.colors(
        containerColor = TtColors.Card,
        contentColor = TtColors.Text,
        focusedContainerColor = TtColors.CardHover,
        focusedContentColor = TtColors.Text,
    )

    @Composable fun border() = CardDefaults.border(
        border = Border(BorderStroke(1.dp, TtColors.Border), shape = CardShape),
        focusedBorder = Border(BorderStroke(3.dp, TtColors.Accent), shape = CardShape),
    )

    fun glow() = CardDefaults.glow(focusedGlow = Glow(TtColors.Accent.copy(alpha = 0.45f), 14.dp))

    val shape = CardDefaults.shape(CardShape)
}

@Composable
fun SectionTitle(text: String, icon: ImageVector? = null, tint: Color = TtColors.Muted, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(start = 8.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(), style = SectionTitleStyle.copy(color = tint))
    }
}

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

/** Big round icon over a title and hint, for empty and blocked states. */
@Composable
fun StateMessage(icon: ImageVector, title: String, hint: String? = null, tint: Color = TtColors.Accent) {
    Box(
        Modifier.size(84.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(42.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(42.dp)) }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, color = TtColors.Text, textAlign = TextAlign.Center)
    if (hint != null) {
        Spacer(Modifier.height(8.dp))
        Text(hint, style = MaterialTheme.typography.bodyLarge, color = TtColors.Muted, textAlign = TextAlign.Center)
    }
}

/** Status pill on a video: approved / waiting / denied. */
@Composable
fun StatusBadge(status: String, label: String, modifier: Modifier = Modifier) {
    val (color, icon) = when (status) {
        VideoStatus.APPROVED -> TtColors.Edu to TtIcons.Check
        VideoStatus.PENDING -> TtColors.Pending to TtIcons.Hourglass
        else -> TtColors.Error to TtIcons.Blocked
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

/** Grey placeholder that pulses while content loads, so the screen never looks frozen. */
@Composable
fun Modifier.shimmer(shape: RoundedCornerShape = CardShape): Modifier {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    return this.alpha(a).background(TtColors.Card, shape)
}

@Composable
fun SkeletonCard(width: Dp) {
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).shimmer())
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.85f).height(14.dp).shimmer(RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(12.dp).shimmer(RoundedCornerShape(4.dp)))
    }
}

/** Placeholder rows shaped like the home screen. */
@Composable
fun SkeletonRows(rows: Int = 2) {
    val l = LocalTvLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(top = 24.dp)) {
        repeat(rows) {
            Box(Modifier.padding(start = 8.dp).width(180.dp).height(16.dp).shimmer(RoundedCornerShape(4.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(l.gap), modifier = Modifier.padding(horizontal = 8.dp)) {
                repeat(l.gridColumns + 1) { SkeletonCard(l.cardWidth) }
            }
        }
    }
}

/** Text field with the gold focus ring used on the setup and search screens. */
@Composable
fun TtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = TtColors.Text),
        cursorBrush = SolidColor(TtColors.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onAction() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(TtColors.Card, ButtonShape)
            .border(if (focused) 2.dp else 1.dp, if (focused) TtColors.Accent else TtColors.Border, ButtonShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = if (focused) TtColors.Accent else TtColors.Muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = TtColors.Dim, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    inner()
                }
            }
        },
    )
}
