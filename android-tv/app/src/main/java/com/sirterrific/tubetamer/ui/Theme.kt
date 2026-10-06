package com.sirterrific.tubetamer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/** The web app's palette (web/static/style.css :root), so TV and web look like one product. */
object TtColors {
    val Base = Color(0xFF0F1828)          // --bg-base
    val Card = Color(0xFF182442)          // --bg-card
    val CardHover = Color(0xFF1E2D50)     // --bg-card-hover, used for focus
    val Header = Color(0xFF122442)        // header gradient start
    val Accent = Color(0xFFC9A84C)        // --accent (gold)
    val AccentDark = Color(0xFFA88928)    // --accent-dark
    val AccentLight = Color(0xFFEDD472)   // logo highlight
    val OnAccent = Color(0xFF1B1406)      // text on gold
    val Text = Color(0xFFE5DDD0)          // --text-primary (cream)
    val Muted = Color(0xFF8A9EC0)         // --text-muted
    val Dim = Color(0xFF4A5E7A)           // --text-dim
    val Border = Color(0x1FC9A84C)        // --border-subtle
    val Error = Color(0xFFE74C6F)
    val Edu = Color(0xFF43A047)
    val EduText = Color(0xFF66BB6A)
    val Fun = Color(0xFFFF8F00)
    val FunText = Color(0xFFFFA726)
    val Pending = Color(0xFFF0A030)
}

private val ColorScheme = darkColorScheme(
    primary = TtColors.Accent,
    onPrimary = TtColors.OnAccent,
    primaryContainer = TtColors.AccentDark,
    onPrimaryContainer = TtColors.OnAccent,
    secondary = TtColors.Muted,
    onSecondary = TtColors.Base,
    background = TtColors.Base,
    onBackground = TtColors.Text,
    surface = TtColors.Card,
    onSurface = TtColors.Text,
    surfaceVariant = TtColors.CardHover,
    onSurfaceVariant = TtColors.Muted,
    inverseSurface = TtColors.Accent,
    inverseOnSurface = TtColors.OnAccent,
    border = TtColors.Border,
    borderVariant = TtColors.Dim,
    error = TtColors.Error,
    onError = Color.White,
)

private val base = Typography()
private val TtTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, color = TtColors.Text),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Section titles: uppercase, letter-spaced, muted blue, like `.section-title` on the web. */
val SectionTitleStyle = TextStyle(
    fontSize = 17.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.2.sp,
    color = TtColors.Muted,
)

/**
 * Sizes that follow the screen. TVs usually report about 960x540 dp whatever their
 * resolution, but density settings, 720p boxes and phone-sized windows vary, so the
 * rows, grids and header adapt to the real width instead of fixed sizes.
 */
data class TvLayout(
    val width: Dp,
    val height: Dp,
    /** Horizontal and vertical screen margins (TV overscan safe area included). */
    val padH: Dp,
    val padV: Dp,
    val cardWidth: Dp,
    val gridColumns: Int,
    val gap: Dp,
    /** Narrow screens: header buttons show icons only. */
    val compact: Boolean,
) {
    val screenPadding get() = PaddingValues(horizontal = padH, vertical = padV)
}

internal fun tvLayout(width: Dp, height: Dp): TvLayout {
    val padH = (width * 0.05f).coerceIn(20.dp, 96.dp)
    val padV = (height * 0.055f).coerceIn(16.dp, 64.dp)
    val gap = if (width < 700.dp) 14.dp else 20.dp
    // Fractional count: the next card peeks in, telling the child the row scrolls.
    val perRow = when {
        width < 600.dp -> 2.3f
        width < 840.dp -> 3.3f
        width < 1100.dp -> 4.3f
        width < 1500.dp -> 5.3f
        else -> 6.3f
    }
    val content = width - padH * 2
    val card = ((content - gap * perRow.toInt()) / perRow).coerceIn(150.dp, 320.dp)
    val columns = ((content + gap) / (card + gap)).toInt().coerceAtLeast(2)
    return TvLayout(width, height, padH, padV, card, columns, gap, compact = width < 840.dp)
}

val LocalTvLayout = staticCompositionLocalOf { tvLayout(960.dp, 540.dp) }

@Composable
fun TubeTamerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = TtTypography) {
        BoxWithConstraints {
            CompositionLocalProvider(LocalTvLayout provides tvLayout(maxWidth, maxHeight)) {
                content()
            }
        }
    }
}
