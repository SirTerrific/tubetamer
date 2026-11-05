package com.sirterrific.tubetamer.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sirterrific.tubetamer.api.Profile
import com.sirterrific.tubetamer.ui.UiError
import com.sirterrific.tubetamer.core.R as CoreR

/** The web app's palette (web/static/style.css :root), same as the TV app. */
object MobileColors {
    val Base = Color(0xFF0F1828)
    val Card = Color(0xFF182442)
    val CardHover = Color(0xFF1E2D50)
    val Accent = Color(0xFFC9A84C)
    val AccentDark = Color(0xFFA88928)
    val OnAccent = Color(0xFF1B1406)
    val Text = Color(0xFFE5DDD0)
    val Muted = Color(0xFF8A9EC0)
    val Dim = Color(0xFF4A5E7A)
    val Error = Color(0xFFE74C6F)
    val Edu = Color(0xFF43A047)
    val Fun = Color(0xFFFF8F00)
    val Pending = Color(0xFFF0A030)
}

private val Scheme = darkColorScheme(
    primary = MobileColors.Accent,
    onPrimary = MobileColors.OnAccent,
    primaryContainer = MobileColors.AccentDark,
    onPrimaryContainer = MobileColors.OnAccent,
    secondary = MobileColors.Muted,
    onSecondary = MobileColors.Base,
    secondaryContainer = MobileColors.CardHover,
    onSecondaryContainer = MobileColors.Text,
    background = MobileColors.Base,
    onBackground = MobileColors.Text,
    surface = MobileColors.Base,
    onSurface = MobileColors.Text,
    surfaceVariant = MobileColors.Card,
    onSurfaceVariant = MobileColors.Muted,
    surfaceContainer = MobileColors.Card,
    surfaceContainerHigh = MobileColors.CardHover,
    outline = MobileColors.Dim,
    error = MobileColors.Error,
    onError = Color.White,
)

@Composable
fun MobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable
fun errorText(e: UiError): String = stringResource(
    when (e) {
        UiError.BAD_ADDRESS -> CoreR.string.err_bad_address
        UiError.UNREACHABLE -> CoreR.string.err_unreachable
        UiError.NOT_TUBETAMER -> CoreR.string.err_not_tubetamer
        UiError.TOO_OLD -> CoreR.string.err_too_old
        UiError.WRONG_PIN -> CoreR.string.err_wrong_pin
        UiError.TOO_MANY_TRIES -> CoreR.string.err_too_many_tries
        UiError.SERVER -> CoreR.string.err_server
    },
)

@Composable
fun ErrorLine(e: UiError?) {
    if (e != null) {
        Spacer(Modifier.height(12.dp))
        Text(errorText(e), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

/** Hat logo and name, as in the web header. */
@Composable
fun AppLogo(size: Dp = 40.dp, showName: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(CoreR.drawable.tubetamer_logo), contentDescription = null, modifier = Modifier.size(size))
        if (showName) {
            Spacer(Modifier.width(size * 0.3f))
            Text("TubeTamer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Child avatar: their chosen color and emoji (set in the web app), else a gold initial. */
@Composable
fun Avatar(p: Profile, size: Dp) {
    val color = parseColor(p.avatarColor)
    Box(
        Modifier.size(size).background(color ?: MobileColors.Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            p.avatarIcon.ifEmpty { p.displayName.take(1).uppercase() },
            fontSize = (size.value * 0.46f).sp,
            color = if (color == null) MobileColors.OnAccent else Color(0xFF1B2233),
        )
    }
}

private fun parseColor(hex: String): Color? =
    if (hex.isBlank()) null else runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

/** Centered column used by the full-screen states (loading, offline, empty). */
@Composable
fun CenteredColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            content()
        }
    }
}
